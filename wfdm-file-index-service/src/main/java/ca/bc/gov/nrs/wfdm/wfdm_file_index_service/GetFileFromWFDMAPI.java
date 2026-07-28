package ca.bc.gov.nrs.wfdm.wfdm_file_index_service;

import org.json.JSONArray;
import org.json.JSONObject;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Static handler for WFDM API Access.
 */
public class GetFileFromWFDMAPI {
  private static final String METADATA_NAME = "metadataName";
  private static final String METADATA_VALUE = "metadataValue";
  private static final String AUTHORIZATION = "Authorization";
  private static final String BEARER = "Bearer ";
  private static final String CREATOR = "Creator";
  private static final String UPLOADED_BY_METADATA = "UploadedBy";
  private static final String UPLOADED_BY_PROPERTY = "uploadedBy";
  private static final String VERSIONS = "versions";
  private static final String UPLOADED_ON_TIMESTAMP = "uploadedOnTimestamp";
  private static final String WFDM_RESOURCE_TYPE_URL = "http://resources.wfdm.nrs.gov.bc.ca/fileMetadataResource";
  private static final String TYPE = "@type";


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
    HttpResponse<JsonNode> httpResponse = Unirest.get(getTokenUrl().trim())
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
  public static HttpResponse<String> getFileInformation(String accessToken, String fileId) throws UnirestException {
    HttpResponse<String> detailsResponse = Unirest.get(getApiUrl().trim() + fileId)
        .header(AUTHORIZATION, BEARER + accessToken)
        .header("Content-Type", "application/json").asString();

    if (detailsResponse.getStatus() == 200) {
      return detailsResponse;
    } else {
      return null;
    }
  }

  public static boolean setIndexedMetadata(String accessToken, String fileId, String versionNumber,
      JSONObject fileDetails, String Etag) throws UnirestException {

    // default fields we will need to add if they don't already exist

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
    Boolean uploadedByExists = false;

    Boolean creatorIsNull = false;
    Boolean uploadedByIsNull = false;

    // Add metadata to the File details to flag it as "Unscanned"
    JSONArray metaArray = fileDetails.getJSONArray("metadata");
    // Locate any existing scan meta and remove
    for (int i = 0; i < metaArray.length(); i++) {
      String metadataName = metaArray.getJSONObject(i).getString(METADATA_NAME);
      if ( i >= 0 && metadataName.equalsIgnoreCase("WFDMIndexVersion-" + versionNumber)
          || (metadataName.equalsIgnoreCase("wfdm-indexed-v" + versionNumber))) {
        metaArray.remove(i);
        i--;
      }
      if (i >= 0 && metadataName.equalsIgnoreCase("WFDMIndexDate-" + versionNumber)) {
        metaArray.remove(i);
        i--;
      }

      // By default the API inherits the parent folders meta value, 
      //Creator needs to have a default value of uploadedBy,
      // So if the parent folder creator is Null, we still want to set the default value
      if (i >= 0 && metadataName.equals(CREATOR)) {
        creatorIsNull = metaArray.getJSONObject(i).getString(METADATA_VALUE).equals("null");
      }
      if (i >= 0 && metadataName.equals(UPLOADED_BY_METADATA)) {
        uploadedByIsNull = metaArray.getJSONObject(i).getString(METADATA_VALUE).equals("null");
      }

      if (!creatorExists) creatorExists = metadataName.equals(CREATOR);
      if (!uploadedByExists) uploadedByExists = metadataName.equals(UPLOADED_BY_METADATA);
      if (!titleExists) titleExists = metadataName.equals("Title");
      if (!dateCreatedExists) dateCreatedExists = metadataName.equals("DateCreated");
      if (!dateModifiedExists) dateModifiedExists = metadataName.equals("DateModified");
      if (!descriptionExists) descriptionExists = metadataName.equals("Description");
      if (!formatExists) formatExists = metadataName.equals("Format");
      if (!uniqueIdentifierExists) uniqueIdentifierExists = metadataName.equals("UniqueIdentifier");
      if (!informationScheduleExists)  informationScheduleExists = metadataName.equals("InformationSchedule");
      if (!securityClassificationExists) securityClassificationExists = metadataName.equals("SecurityClassification");
      if (!oPRExists)  oPRExists = metadataName.equals("OPR");      
      if (!incidentNumberExists) incidentNumberExists = metadataName.equals("IncidentNumber");
      if (!appAcronymExists) appAcronymExists = metadataName.equals("AppAcronym");

    }

    // check for default metadata, if it exists do nothing
    if (!creatorExists || creatorIsNull)  {
      String uploadedBy = fileDetails.isNull(UPLOADED_BY_PROPERTY) ? "null" : fileDetails.getString(UPLOADED_BY_PROPERTY);
      metaArray.put(addMeta(CREATOR, uploadedBy));
    }
    if (!uploadedByExists || uploadedByIsNull) {
      String uploadedBy = fileDetails.isNull(UPLOADED_BY_PROPERTY) ? "null" : fileDetails.getString(UPLOADED_BY_PROPERTY);
      metaArray.put(addMeta(UPLOADED_BY_METADATA, uploadedBy));
    }

    if (!dateCreatedExists) {
      // store date in metadata standard format yyyy-MM-dd HH:mm:ss
      String dateCreatedValue = "null";
      DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"); 
      DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

      // Always try to derive DateCreated from version 1 in the versions array
      if (fileDetails.has(VERSIONS) && !fileDetails.isNull(VERSIONS)) {
          JSONArray versions = fileDetails.getJSONArray(VERSIONS);

          for (int i = 0; i < versions.length(); i++) {
            JSONObject version = versions.getJSONObject(i);
            int vNum = version.getInt("versionNumber");

            if (vNum == 1 && version.has(UPLOADED_ON_TIMESTAMP) && !version.isNull(UPLOADED_ON_TIMESTAMP)) {
              try {
                    String raw = version.getString(UPLOADED_ON_TIMESTAMP);
                    LocalDateTime parsed = LocalDateTime.parse(raw, inputFormatter);
                    dateCreatedValue = parsed.format(outputFormatter);
                } catch (Exception e) {
                    // fallback - store raw value instead of failing
                    dateCreatedValue = version.getString(UPLOADED_ON_TIMESTAMP);
                }
                break;
            }
          }
        }
        metaArray.put(addMeta("DateCreated", dateCreatedValue));
    }

    if (!titleExists) metaArray.put(addMeta("Title", "null"));
    if (!dateModifiedExists) metaArray.put(addMeta("DateModified", "null"));
    if (!descriptionExists) metaArray.put(addMeta("Description", "null"));
    if (!formatExists) metaArray.put(addMeta("Format", "null"));
    if (!uniqueIdentifierExists) metaArray.put(addMeta("UniqueIdentifier", "null"));
    if (!informationScheduleExists) metaArray.put(addMeta("InformationSchedule", "null"));
    if (!securityClassificationExists) metaArray.put(addMeta("SecurityClassification", "null"));
    if (!oPRExists) metaArray.put(addMeta("OPR", "null"));
    if (!incidentNumberExists) metaArray.put(addMeta("IncidentNumber", "null"));
    if (!appAcronymExists) metaArray.put(addMeta("AppAcronym", "null"));

    // inject scan meta
    JSONObject meta = new JSONObject();
    meta.put(TYPE, WFDM_RESOURCE_TYPE_URL);
    meta.put(METADATA_NAME, "WFDMIndexVersion-" + versionNumber);
    meta.put(METADATA_VALUE, "true");
    metaArray.put(meta);

    JSONObject meta2 = new JSONObject();
    meta2.put(TYPE, WFDM_RESOURCE_TYPE_URL);
    meta2.put(METADATA_NAME, "WFDMIndexDate-" + versionNumber);
    Format formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    meta2.put(METADATA_VALUE, formatter.format(new Date().getTime()));
    metaArray.put(meta2);

    // PUT the changes
    String wfdmAPIUrl = PropertyLoader.getProperty("wfdm.document.api.url").trim();
    HttpResponse<String> metaUpdateResponse = Unirest.put(getApiUrl().trim() + fileId)
        .header("Content-Type", "application/json")
        .header(AUTHORIZATION, BEARER + accessToken)
        .header("If-Match", Etag) 
        .body(fileDetails.toString())
        .asString();

    return metaUpdateResponse.getStatus() == 200;
  }

  public static JSONObject addMeta(String metaName, String metaValue) {
    JSONObject meta = new JSONObject();
    meta.put(TYPE, WFDM_RESOURCE_TYPE_URL);
    meta.put(METADATA_NAME, metaName);
    meta.put(METADATA_VALUE, metaValue);
    return meta;
  }

  static String getTokenUrl() {
    return System.getenv("WFDM_DOCUMENT_TOKEN_URL");
  }

  static String getApiUrl() {
      return System.getenv("WFDM_DOCUMENT_API_URL");
  }

}
