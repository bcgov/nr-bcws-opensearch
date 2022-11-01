package ca.bc.gov.nrs.wfdm.wfdm_file_index_service;

import org.json.JSONArray;
import org.json.JSONObject;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Static handler for WFDM API Access.
 */
public class GetFileFromWFDMAPI {

  // Private constructor hides the implicit public constructor
  private GetFileFromWFDMAPI() {
    /* empty */ }

  /**
   * Fetch an Access Token for authentication with the WFDM API
   * 
   * @param client   The Client ID
   * @param password The Client Secret
   * @return
   * @throws UnirestException
   */
  public static String getAccessToken(String client, String password) throws UnirestException {
    HttpResponse<JsonNode> httpResponse = Unirest.get(System.getenv("WFDM_DOCUMENT_TOKEN_URL").trim())
        .basicAuth(client, password)
        .asJson();

    if (httpResponse.getStatus() == 200) {
      JSONObject responseBody = httpResponse.getBody().getObject();
      return responseBody.get("access_token").toString();
    } else {
      return null;
    }
  }

  /**
   * Fetch the details of a WFDM File resource, including Metadata and security
   * This method will not return the files bytes
   * 
   * @param accessToken
   * @param fileId
   * @return
   * @throws UnirestException
   */
  public static String getFileInformation(String accessToken, String fileId) throws UnirestException {
    HttpResponse<String> detailsResponse = Unirest.get(System.getenv("WFDM_DOCUMENT_API_URL").trim() + fileId)
        .header("Authorization", "Bearer " + accessToken)
        .header("Content-Type", "application/json").asString();

    if (detailsResponse.getStatus() == 200) {
      return detailsResponse.getBody();
    } else {
      return null;
    }
  }

  public static boolean setIndexedMetadata(String accessToken, String fileId, String versionNumber,
      JSONObject fileDetails) throws UnirestException {

    //default fields we will need to add if they don't already exist

    Boolean nameExists = false;
    Boolean creatorExists = false;
    Boolean titleExists = false;
    Boolean dateCreatedExists = false;
    Boolean dateModifiedExists = false;
    Boolean descriptionExists = false;
    Boolean formatExists = false;
    Boolean uniqueIdentifierExists = false;
    Boolean informationScheduleExists = false;
    Boolean securityClassificationExists = false;
    Boolean retentionScheduleExists = false;
    Boolean oPRExists = false;
    Boolean incidentNumberExists = false;
    Boolean appAcronymExists = false;





    // Add metadata to the File details to flag it as "Unscanned"
    JSONArray metaArray = fileDetails.getJSONArray("metadata");
    // Locate any existing scan meta and remove
    for (int i = 0; i < metaArray.length(); i++) {
      String metadataName = metaArray.getJSONObject(i).getString("metadataName");
      if (metadataName.equalsIgnoreCase("WFDMIndexVersion-" + versionNumber)  ||  (metadataName.equalsIgnoreCase("wfdm-indexed-v" + versionNumber) )             ) {
        metaArray.remove(i);
        break;
      }
      if (metadataName.equalsIgnoreCase("WFDMIndexDate-" + versionNumber)) {
        metaArray.remove(i);
        break;
      }


      switch(metadataName) {
        case "Name":
          nameExists = true;
          break;
      }
        




    }

    //check for default metadata, if it exists do nothing, if not update:
    if (!nameExists) {
      JSONObject metaName = new JSONObject();
      metaName.put("metadataName", "Name");
      metaName.put("metadataValue", "null");
      metaArray.put(metaName);
    }





    // inject scan meta
    JSONObject meta = new JSONObject();
    meta.put("@type", "http://resources.wfdm.nrs.gov.bc.ca/fileMetadataResource");
    meta.put("metadataName", "WFDMIndexVersion-" + versionNumber);
    meta.put("metadataValue", "true");
    metaArray.put(meta);

    JSONObject meta2 = new JSONObject();
    meta2.put("@type", "http://resources.wfdm.nrs.gov.bc.ca/fileMetadataResource");
    meta2.put("metadataName", "WFDMIndexDate-" + versionNumber);
    Format formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    meta2.put("metadataValue", formatter.format(new Date().getTime()));
    metaArray.put(meta2);

    // PUT the changes
    String wfdmAPIUrl = PropertyLoader.getProperty("wfdm.document.api.url").trim();
    HttpResponse<String> metaUpdateResponse = Unirest.put(System.getenv("WFDM_DOCUMENT_API_URL").trim() + fileId)
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + accessToken)
        .body(fileDetails.toString())
        .asString();

    return metaUpdateResponse.getStatus() == 200;
  }
}
