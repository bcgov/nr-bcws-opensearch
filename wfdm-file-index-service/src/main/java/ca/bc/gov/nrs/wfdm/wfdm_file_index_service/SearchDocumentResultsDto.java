package ca.bc.gov.nrs.wfdm.wfdm_file_index_service;

import java.util.List;
import java.util.Map;

public class SearchDocumentResultsDto {
    private String fileId;
    private String fileName;
    private List<Map<String, Object>> metadata;
    private String key;
    private String securityKey;
    private List<Map<String, Object>> security;
    private List<Map<String, Object>> securityScope;
    private String filePath;
    private String absoluteFilePath;
    private String mimeType;
    private String lastUpdatedBy;
    private String fileLink;
    private String fileSize;
    private String lastModified;
    private String fileRetention;
    private String uploadedBy;
    private String fileExtension;
    private Long fileSizeBytes;
    private String fileType;
    private String fileContent;
    private String lockedInd;
    private String retentionTerm;
    private String uploadedOnTimestampString;
    private String validEndTimestamp;
    private String validStartTimestamp;
    private String versionNumber;
    private String scanStatus;

    public SearchDocumentResultsDto() {

    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public List<Map<String, Object>> getMetadata() {
        return metadata;
    }

    public void setMetadata(List<Map<String, Object>> metadata) {
        this.metadata = metadata;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getSecurityKey() {
        return securityKey;
    }

    public void setSecurityKey(String securityKey) {
        this.securityKey = securityKey;
    }

    public List<Map<String, Object>> getSecurity() {
        return security;
    }

    public void setSecurity(List<Map<String, Object>> security) {
        this.security = security;
    }

    public List<Map<String, Object>> getSecurityScope() {
        return securityScope;
    }

    public void setSecurityScope(List<Map<String, Object>> securityScope) {
        this.securityScope = securityScope;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getAbsoluteFilePath() {
        return absoluteFilePath;
    }

    public void setAbsoluteFilePath(String absoluteFilePath) {
        this.absoluteFilePath = absoluteFilePath;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    public void setLastUpdatedBy(String lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
    }

    public String getFileLink() {
        return fileLink;
    }

    public void setFileLink(String fileLink) {
        this.fileLink = fileLink;
    }

    public String getFileSize() {
        return fileSize;
    }

    public void setFileSize(String fileSize) {
        this.fileSize = fileSize;
    }

    public String getLastModified() {
        return lastModified;
    }

    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }

    public String getFileRetention() {
        return fileRetention;
    }

    public void setFileRetention(String fileRetention) {
        this.fileRetention = fileRetention;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getFileType() {
        return fileType;
    }
    public void setFileType(String fileType){
        this.fileType = fileType;
    }

    public String getFileContent() {
        return fileContent;
    }

    public void setFileContent(String fileContent) {
        this.fileContent = fileContent;
    }

    public String getLockedInd() {
        return lockedInd;
    }
    public void setLockedInd(String lockedInd){
        this.lockedInd = lockedInd;
    }

    public String getRetentionTerm() {
        return retentionTerm;
    }

    public void setRetentionTerm(String retentionTerm){
        this.retentionTerm = retentionTerm;
    }

    public String getUploadedOnTimestampString() {
        return uploadedOnTimestampString;
    }

    public void setUploadedOnTimestampString(String uploadedOnTimestampString){
        this.uploadedOnTimestampString = uploadedOnTimestampString;
    }

    public String getValidEndTimestamp() {
        return validEndTimestamp;
    }

    public void setValidEndTimestamp(String validEndTimestamp){
        this.validEndTimestamp = validEndTimestamp;
    }

    public String getValidStartTimestamp() {
        return validStartTimestamp;
    }

    public void setValidStartTimestamp(String validStartTimestamp){
        this.validStartTimestamp = validStartTimestamp;
    }

    public String getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(String versionNumber){
        this.versionNumber = versionNumber;
    }

    public String getScanStatus() {
        return scanStatus;
    }

    public void setScanStatus(String scanStatus) {
        this.scanStatus = scanStatus;
    }
}
