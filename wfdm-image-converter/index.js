const { promisify } = require('util');
const fs = require('fs');
const convert = require('heic-convert');
const Axios = require('axios');
const FormData = require('form-data');
const path = require('path');

//Lambda takes a fileId from the queue from the api that has sent a heic or heif file to be converted
// it gets that image file from the api and saves it to the lambdas tmp folder
// then the file is converted to jpg and also written to the tmp folder
// then the file is sent to the wfdm api as a new image
// once that is succesful the original image is deleted


exports.handler = async (event) => {
  try {

    let fileId;
    let wfdmApi = process.env.wfdmApi
    let apiURL = wfdmApi + "documents/";


    let tokenService = process.env.tokenService
    let clientName = process.env.clientName
    let clientSecret = process.env.clientSecret

   
    for (let { messageId, body } of event.Records) {
        console.log('SQS message %s: %j', messageId, body);
        console.log(body);
        let jsonBody = JSON.stringify(body)
        jsonBody = jsonBody.split(':')
        jsonBody = jsonBody[1].split(',')
        jsonBody = jsonBody[0].replaceAll("'", "")
        console.log("json body after stringify" + jsonBody)
        fileId = jsonBody;
    }
  

    const encoded = Buffer.from(clientName + ':' + clientSecret).toString('base64');

    let tokenConfig = {
      method: 'get',
      maxBodyLength: Infinity,
      url: tokenService,
      headers: {
        'Authorization': 'Basic ' + encoded
      }
    };
    let bearerToken
    let bearerTokenResponse = await Axios.request(tokenConfig)
      .then((response) => {
        return response;
      })
      .catch((error) => {
        console.log(error);
        return error;
      });

    if (bearerTokenResponse.status !== 200) {
      console.log("no bearer token found");
    } else {
      bearerToken = bearerTokenResponse.data.access_token

      let fileInfo = await Axios.get(apiURL + fileId, {
        headers: {
          'content-type': 'multipart/form-data',
          'Authorization': 'Bearer ' + bearerToken
        }
      })
      if (fileInfo.status !== 200) {
        console.log("file was not retrieved with fileId: " + fileId)
      } else {
        //find the new file to be used
        let fileName = path.basename(fileInfo.data.filePath).split('.')[0] + ".jpg";

        let parentFileId = fileInfo.data.parent.fileId;

        //save the image to be converted to the temp folder
        await downloadImage(apiURL + fileId + "/bytes?", "/tmp/" + fileId, bearerToken);

        const inputBuffer = await promisify(fs.readFile)("/tmp/" + fileId);

        // convert the image to jpg
        const outputBuffer = await convert({
          buffer: inputBuffer,
          format: 'JPEG',
          quality: 1
        });

        // write the converted image to the tmp folder
        await promisify(fs.writeFile)("/tmp/" + fileName, outputBuffer);

        let stats = fs.statSync("/tmp/" + fileName);
        let fileSizeInBytes = stats.size;

        // create the json data that a file is created with
        let jsonData = {
          "@type": "http://resources.wfdm.nrs.gov.bc.ca/fileDetails",
          "type": "http://resources.wfdm.nrs.gov.bc.ca/fileDetails",
          "parent": {
            "@type": "http://resources.wfdm.nrs.gov.bc.ca/file",
            "type": "http://resources.wfdm.nrs.gov.bc.ca/file",
            "fileId": parentFileId
          },
          "fileSize": fileSizeInBytes,
          "fileType": "DOCUMENT",
          "filePath": fileName,
          "security": [],
          "metadata": [],
          "fileCheckout": null,
          "lockedInd": null,
          "uploadedOnTimestamp": null
        }

        let jsonDataString = JSON.stringify(jsonData);

        let jsonDataFilePath = "/tmp/fileInfo" + fileName + ".json";

        //write the json file to temp so it can be sent along with the image
        await promisify(fs.writeFile)(jsonDataFilePath, jsonDataString);

        // send the new image with it's json data back to the wfdm api
        let postImageResponse = await postImage(apiURL, "/tmp/" + fileName, jsonDataFilePath, bearerToken);

        if (postImageResponse.status !== 201) {
          console.log("failed to write image")
        } else {
          //remove files from temp folder once they've been uploaded back to API
          fs.unlinkSync("/tmp/" + fileId);
          fs.unlinkSync("/tmp/" + fileName);
          fs.unlinkSync(jsonDataFilePath);

          // with the converted succesfully converted, the original can be deleted
          let deleteResponse = await deleteOriginalImage(apiURL, bearerToken, fileId);

          if (deleteResponse !== '') {
            console.log("failed to delete original image with fileId: " + fileId)
          } else {
            console.log("deleted original image with fileId: " + fileId)
          }
        }
      }
    }
  } catch (error) {
    console.log(error)
    fs.unlinkSync("/tmp/" + fileId);
    fs.unlinkSync("/tmp/" + fileName);
    fs.unlinkSync(jsonDataFilePath);
  }


   }


async function deleteOriginalImage(url, bearerToken, fileId) {

  const axios = require('axios');

  let config = {
    method: 'delete',
    maxBodyLength: Infinity,
    url: url + fileId,
    headers: {
      'Authorization': 'Bearer ' + bearerToken
    }
  };

  return Axios.request(config)
    .then((response) => {
      console.log(JSON.stringify(response.data));
      return response.data
    })
    .catch((error) => {
      console.log(error);
      return error;
    });

}

async function postImage(url, filePath, jsonDataFilePath, bearerToken) {
  try {
    let data = new FormData();
    data.append('file', fs.createReadStream(filePath));
    data.append('resource', fs.createReadStream(jsonDataFilePath));

    let config = {
      method: 'post',
      maxBodyLength: Infinity,
      url: url,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + bearerToken,
        ...data.getHeaders()
      },
      data: data
    };

   return await Axios.request(config)
      .then((response) => {
        console.log(JSON.stringify(response.data));
        return response
      })
      .catch((error) => {
        console.log(error);
        return error
      });
  } catch (error) {
    console.error(error);
    return error
  }
};

async function downloadImage(url, filepath, bearerToken) {
  const response = await Axios({
    url,
    method: 'GET',
    responseType: 'stream',
    headers: {
      'Content-Type': 'application/octet-stream',
      'Authorization': `Bearer ` + bearerToken
    },
  });
  return new Promise((resolve, reject) => {
    response.data.pipe(fs.createWriteStream(filepath))
      .on('error', reject)
      .once('close', () => resolve(filepath));
  });
}
