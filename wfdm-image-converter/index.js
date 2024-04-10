const { promisify } = require('util');
const fs = require('fs');
const convert = require('heic-convert');
const Axios = require('axios');
const FormData = require('form-data');
const path = require('path');

exports.handler = async (event) => {
//(async () => {
  try {

    let fileId;
    //fileId = "39051";
    let wfdmApi = process.env.wfdmApi
    let apiURL = wfdmApi + "documents/";


    let tokenService = process.env.tokenService
    let clientName = process.env.clientName
    let clientSecret = process.env.clientSecret
    //let tokenService =
    //let clientName = 
    //let clientSecret = 

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
    
    let bearerToken = await Axios.request(tokenConfig)
    .then((response) => {
      return response.data.access_token;
    })
    .catch((error) => {
      console.log(error);
    });
    
    let fileInfo = await Axios.get(apiURL + fileId, {
      headers: {
        'content-type': 'multipart/form-data',
        'Authorization': 'Bearer ' + bearerToken
      }
    })

    //find the new file to be used
    let fileName = path.basename(fileInfo.data.filePath).split('.')[0] + ".jpg";

    let parentFileId = fileInfo.data.parent.fileId;
  
    await downloadImage(apiURL + fileId + "/bytes?", "/tmp/" + fileId, bearerToken);

    const inputBuffer = await promisify(fs.readFile)("/tmp/" + fileId);

    const outputBuffer = await convert({
      buffer: inputBuffer,
      format: 'JPEG',
      quality: 1
    });

    await promisify(fs.writeFile)("/tmp/" + fileName, outputBuffer);

    let stats = fs.statSync("/tmp/" + fileName);
    let fileSizeInBytes = stats.size;

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

    await promisify(fs.writeFile)(jsonDataFilePath, jsonDataString);

    await putImage(apiURL, "/tmp/" + fileName, jsonDataFilePath, bearerToken);

    //remove files from temp folder once they've been uploaded back to API
    fs.unlinkSync("/tmp/" + fileId);
    fs.unlinkSync("/tmp/" + fileName);
    fs.unlinkSync(jsonDataFilePath);
    

  } catch(error) {
    console.log(error)

  }


  }
//})();

async function putImage(url, filePath, jsonDataFilePath, bearerToken){
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
      data : data
    };
    
  await Axios.request(config)
  .then((response) => {
    console.log(JSON.stringify(response.data));
  })
  .catch((error) => {
    console.log(error);
  });
  } catch (error) {
    console.error(error);
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
