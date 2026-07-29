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

    // Add metadata to the File details to flag it as "Unscanned"
    JSONArray metaArray = fileDetails.getJSONArray("metadata");
    MetadataFlags flags = inspectMetadata(metaArray, versionNumber);

    // check for default metadata, if it exists do nothing
    addDefaultCreatorMetadata(metaArray, fileDetails, flags);

    if (!flags.dateCreatedExists) {
      metaArray.put(addMeta("DateCreated", deriveDateCreated(fileDetails)));
    }

    addMissingDefaultMetadata(metaArray, flags);

    // inject scan meta
    addIndexMetadata(metaArray, versionNumber);

    // PUT the changes
    return updateMetadata(accessToken, fileId, Etag, fileDetails);
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
  
  private static class MetadataFlags {
    boolean creatorExists;
    boolean titleExists;
    boolean dateCreatedExists;
    boolean dateModifiedExists;
    boolean descriptionExists;
    boolean formatExists;
    boolean uniqueIdentifierExists;
    boolean informationScheduleExists;
    boolean securityClassificationExists;
    boolean oprExists;
    boolean incidentNumberExists;
    boolean appAcronymExists;
    boolean uploadedByExists;

    boolean creatorIsNull;
    boolean uploadedByIsNull;
  }

  private static MetadataFlags inspectMetadata(JSONArray metaArray, String versionNumber) {

    MetadataFlags flags = new MetadataFlags();

    for (int i = 0; i < metaArray.length(); i++) {

      String metadataName = metaArray.getJSONObject(i).getString(METADATA_NAME);

      if (( metadataName.equalsIgnoreCase("WFDMIndexVersion-" + versionNumber))
          || metadataName.equalsIgnoreCase("wfdm-indexed-v" + versionNumber)) {
        metaArray.remove(i);
        i--;
        continue;
      }

      if ( metadataName.equalsIgnoreCase("WFDMIndexDate-" + versionNumber)) {
        metaArray.remove(i);
        i--;
        continue;
      }

      if ( metadataName.equals(CREATOR)) {
        flags.creatorIsNull = metaArray.getJSONObject(i).getString(METADATA_VALUE).equals("null");
      }

      if ( metadataName.equals(UPLOADED_BY_METADATA)) {
        flags.uploadedByIsNull =  metaArray.getJSONObject(i).getString(METADATA_VALUE).equals("null");
      }

      if (!flags.creatorExists) flags.creatorExists = metadataName.equals(CREATOR);
      if (!flags.uploadedByExists) flags.uploadedByExists = metadataName.equals(UPLOADED_BY_METADATA);
      if (!flags.titleExists) flags.titleExists = metadataName.equals("Title");
      if (!flags.dateCreatedExists) flags.dateCreatedExists = metadataName.equals("DateCreated");
      if (!flags.dateModifiedExists) flags.dateModifiedExists = metadataName.equals("DateModified");
      if (!flags.descriptionExists) flags.descriptionExists = metadataName.equals("Description");
      if (!flags.formatExists) flags.formatExists = metadataName.equals("Format");
      if (!flags.uniqueIdentifierExists) flags.uniqueIdentifierExists = metadataName.equals("UniqueIdentifier");
      if (!flags.informationScheduleExists) flags.informationScheduleExists = metadataName.equals("InformationSchedule");
      if (!flags.securityClassificationExists) flags.securityClassificationExists = metadataName.equals("SecurityClassification");
      if (!flags.oprExists) flags.oprExists = metadataName.equals("OPR");
      if (!flags.incidentNumberExists) flags.incidentNumberExists = metadataName.equals("IncidentNumber");
      if (!flags.appAcronymExists) flags.appAcronymExists = metadataName.equals("AppAcronym");
    }

    return flags;
  }


  private static String deriveDateCreated(JSONObject fileDetails) {

    String dateCreatedValue = "null";

    DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");

    DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    if (!fileDetails.has(VERSIONS) || fileDetails.isNull(VERSIONS)) {
      return dateCreatedValue;
    }

    JSONArray versions = fileDetails.getJSONArray(VERSIONS);

    for (int i = 0; i < versions.length(); i++) {

      JSONObject version = versions.getJSONObject(i);

      if (version.getInt("versionNumber") != 1) {
        continue;
      }

      if (!version.has(UPLOADED_ON_TIMESTAMP) || version.isNull(UPLOADED_ON_TIMESTAMP)) {
        continue;
      }

      String raw = version.getString(UPLOADED_ON_TIMESTAMP);

      try {
        LocalDateTime parsed = LocalDateTime.parse(raw, inputFormatter);
        return parsed.format(outputFormatter);
      } catch (RuntimeException e) {
        return raw;
      }
    }

    return dateCreatedValue;
  }

  private static void addMissingDefaultMetadata(JSONArray metaArray, MetadataFlags flags) {
    if (!flags.titleExists) {
      metaArray.put(addMeta("Title", "null"));
    }

    if (!flags.dateModifiedExists) {
      metaArray.put(addMeta("DateModified", "null"));
    }

    if (!flags.descriptionExists) {
      metaArray.put(addMeta("Description", "null"));
    }

    if (!flags.formatExists) {
      metaArray.put(addMeta("Format", "null"));
    }

    if (!flags.uniqueIdentifierExists) {
      metaArray.put(addMeta("UniqueIdentifier", "null"));
    }

    if (!flags.informationScheduleExists) {
      metaArray.put(addMeta("InformationSchedule", "null"));
    }

    if (!flags.securityClassificationExists) {
      metaArray.put(addMeta("SecurityClassification", "null"));
    }

    if (!flags.oprExists) {
      metaArray.put(addMeta("OPR", "null"));
    }

    if (!flags.incidentNumberExists) {
      metaArray.put(addMeta("IncidentNumber", "null"));
    }

    if (!flags.appAcronymExists) {
      metaArray.put(addMeta("AppAcronym", "null"));
    }
	}

  private static void addIndexMetadata(JSONArray metaArray, String versionNumber) {
    JSONObject versionMeta = new JSONObject();
    versionMeta.put(TYPE, WFDM_RESOURCE_TYPE_URL);
    versionMeta.put(METADATA_NAME, "WFDMIndexVersion-" + versionNumber);
    versionMeta.put(METADATA_VALUE, "true");
    metaArray.put(versionMeta);

    JSONObject dateMeta = new JSONObject();
    dateMeta.put(TYPE, WFDM_RESOURCE_TYPE_URL);
    dateMeta.put(METADATA_NAME, "WFDMIndexDate-" + versionNumber);

    Format formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    dateMeta.put(METADATA_VALUE, formatter.format(new Date().getTime()));

    metaArray.put(dateMeta);
  }

  private static void addDefaultCreatorMetadata(JSONArray metaArray, JSONObject fileDetails, MetadataFlags flags) {

    String uploadedBy = fileDetails.isNull(UPLOADED_BY_PROPERTY) ? "null" : fileDetails.getString(UPLOADED_BY_PROPERTY);

    if (!flags.creatorExists || flags.creatorIsNull) {
      metaArray.put(addMeta(CREATOR, uploadedBy));
    }

    if (!flags.uploadedByExists || flags.uploadedByIsNull) {
      metaArray.put(addMeta(UPLOADED_BY_METADATA, uploadedBy));
    }
  }

  private static boolean updateMetadata(String accessToken, String fileId,
     String etag, JSONObject fileDetails) 
     throws UnirestException {

    HttpResponse<String> metaUpdateResponse =
        Unirest.put(getApiUrl().trim() + fileId)
            .header("Content-Type", "application/json")
            .header(AUTHORIZATION, BEARER + accessToken)
            .header("If-Match", etag)
            .body(fileDetails.toString())
            .asString();

    return metaUpdateResponse.getStatus() == 200;
  }


}
