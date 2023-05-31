from sqlite3 import Date
import requests
from requests.auth import HTTPBasicAuth
import sys
import dateutil.parser
import os
import json
import pathlib

# Token service, for fetching a token
token_service = 'https://intapps.nrs.gov.bc.ca/pub/oauth2/v1/oauth/token?disableDeveloperFilter=true&response_type=token&grant_type=client_credentials'
# Client, use Basic Auth
client_name = 'WFDM_DOCUMENTS_INDEX'
client_secret = ''
# WFDM API endpoints
wfdm_api = 'https://i1bcwsapi.nrs.gov.bc.ca/wfdm-document-management-api/'
doc_endpoint = wfdm_api + 'document' # can we take a moment to recognize that this is a poor API naming scheme
docs_endpoint = wfdm_api + 'documents'
wfdm_root = '?filePath=%2F'
doc_root = '?parentId='
# Some default process settings
row_count = 20

pathName = os.path.dirname(os.path.abspath(__file__))
with open(pathName + '/jsonUpdates/wfrmFieldUpdates.json') as jsonFile:
    importedMetadataFixJson = jsonFile.read()

jsonParsed = json.loads(importedMetadataFixJson)
jsonValues = jsonParsed.values()
jsonListValues = list(jsonValues)

print('')
print('-------------------------------------------------------')
print('Starting Meta Update')
print('WFDM Paging: ' + str(row_count) + ' rows')
print('Connect to WFDM API: ' + wfdm_api)
print('-------------------------------------------------------')
print('')

# Define our Recursive function
def update_metadata(document_id, page, row_count):
  # REMEMBER: There are fetch size limits, so we'll need to be paging data
  # For whatever reason, the page is not a zero-based index
  # This will be recursive, so there's always a stack overflow risk here
  print('Updating documents in the folder ' + document_id)
  url = docs_endpoint + doc_root + document_id + '&pageNumber=' + str(page) + '&pageRowCount=' + str(row_count) + '&orderBy=default%20ASC'
  wfdm_docs_response = requests.get(url, headers={'Authorization': 'Bearer ' + token})
  # verify 200
  if wfdm_docs_response.status_code != 200:
    print(wfdm_docs_response)
    sys.exit("Failed to fetch from WFDM. Response code was: " + str(wfdm_docs_response.status_code))

  wfdm_docs = wfdm_docs_response.json()
  del wfdm_docs_response

  print('Found ' + str(len(wfdm_docs['collection'])) + ' documents, page ' + str(page) + ' of ' + str(wfdm_docs['totalPageCount']))
  # Time to start looping through the documents and updating their meta types
  for document in wfdm_docs['collection']:
    # Reload the document so we know we have a valid etag and meta records
    print('Fetching Document ' + document['fileId'] + '...')
    wfdm_doc_response = requests.get(docs_endpoint + '/' + document['fileId'], headers={'Authorization': 'Bearer ' + token})
    # verify 200
    if wfdm_doc_response.status_code != 200:
      print(wfdm_doc_response)
    else :  
      # Pull out the fileId, this is our parent for WFDM
      doc_json = wfdm_doc_response.json()
      del wfdm_doc_response
      # First, update the metadata records
      for positionInMetaArr, meta in enumerate( doc_json['metadata']):
        # Detect the data type, and update
        # the dataType attribute on the metadata column
        value = meta['metadataValue']
        name = meta['metadataName']
        ## avoid checking the default field, they're fine
        if name != ("Title" or "DateCreated" or "DateModified" or "Description" or "Format" or "UniqueIdentifier" or "InformationSchedule" or "SecurityClassification" or "OPR" or "IncidentNumber" or "AppAcronym"):
            for i in jsonListValues[0]:
                if i["fieldNameToUpdate"] == name:
                    ## when we're transfering data to a default field, we need to find that default field, set it with the original field value, then remove the removed array
                    if i["fixType"] == "transferValueThenDelete":
                        print(i["fixType"])
                        for meta2 in doc_json['metadata']:
                           if meta2['metadataName'] == i['destinationFieldName']:
                              meta2['metadataValue'] = value
                              doc_json['metadata'].pop(positionInMetaArr)
                              break
                    elif i["fixType"] == "renameField":
                        print(i["fixType"])
                        meta['metadataName'] = i["destinationFieldName"]
                else:
                    print(name + "does not exist")


      # Now that they type is updated, we can push in an update
      wfdm_put_response = requests.put(docs_endpoint + '/' + document['fileId'], data=json.dumps(doc_json),  headers={'Authorization': 'Bearer ' + token, 'content-type':'application/json'})
      # verify 200
      if wfdm_put_response.status_code != 200:
        print(wfdm_put_response)
        # Don't fail out here, just cary on
      del wfdm_put_response

      # then, if this is a directory, jump into it and update the documents it contains
      if document['fileType'] == 'DIRECTORY':
        update_metadata(document['fileId'], 1, row_count)

  # check if we need to page
  if wfdm_docs['totalPageCount'] > page:
    # Indeed we do
    update_metadata(document_id, page + 1, row_count)
  
  # Completed updates and exiting
  return 0

# Step #1, lets go get a token for the API
print('Fetching a token from OAUTH...')
token_response = requests.get(token_service, auth=HTTPBasicAuth(client_name, client_secret))
# verify 200
if token_response.status_code != 200:
  sys.exit("Failed to fetch a token for WFDM. Response code was: " + str(token_response.status_code)) 

token = token_response.json()['access_token']
del token_response

# Step #2, now that we have a nice shiny new token, lets go fetch from WFDM why not
# First though, we need to know our Root ID
print('Fetching The WFDM Root Document...')
wfdm_root_response = requests.get(doc_endpoint + wfdm_root, headers={'Authorization': 'Bearer ' + token})
# verify 200
if wfdm_root_response.status_code != 200:
  print(wfdm_root_response)
  sys.exit("Failed to fetch from WFDM. Response code was: " + str(wfdm_root_response.status_code))
# Pull out the fileId, this is our parent for WFDM
root_id = wfdm_root_response.json()['fileId']
del wfdm_root_response
# start the metadata update
print('... Done! Starting Metadata update process from root document ' + str(root_id))
update_metadata(root_id, 1, row_count)
