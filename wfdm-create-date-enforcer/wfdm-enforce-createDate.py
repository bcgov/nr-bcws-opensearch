from sqlite3 import Date
import requests
from requests.auth import HTTPBasicAuth
import sys
import os
import json
import threading

# Token service, for fetching a token
token_service = os.getenv('TOKEN_SERVICE')
# Client, use Basic Auth
client_name = os.getenv('CLIENT')
client_secret = os.getenv('CLIENT_SECRET')
# WFDM API endpoints
wfdm_api = os.getenv('WFDM_API_URL')
doc_endpoint = wfdm_api + 'document' # can we take a moment to recognize that this is a poor API naming scheme
docs_endpoint = wfdm_api + 'documents'
wfdm_root = '?filePath=%2F'
doc_root = '?parentId='
# Some default process settings
row_count = row_count = os.getenv('QUERY_ROW_COUNT')

# Step #1, lets go get a token for the API
print('Fetching a token from OAUTH...')
token_response = requests.get(token_service, auth=HTTPBasicAuth(client_name, client_secret))
# verify 200
if token_response.status_code != 200:
  sys.exit("Failed to fetch a token for WFDM. Response code was: " + str(token_response.status_code)) 

token = token_response.json()['access_token']
del token_response

print('')
print('-------------------------------------------------------')
print('Starting createDate Update')
print('WFDM Paging: ' + str(row_count) + ' rows')
print('Connect to WFDM API: ' + wfdm_api)
print('-------------------------------------------------------')
print('')

# Define our Recursive function
def enforce_createDate(document_id, page, row_count, threadDepth=0):
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
  # Time to start looping through the documents and checking for a createDate
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
      if any(x['metadataName'] == "DateCreated" for x in doc_json['metadata']):
        print('createDate already exists on fileId ' + doc_json['parent']['fileId'])
      elif doc_json['uploadedOnTimestamp'] == None:
        print("No uploadedOnTimestamp available")
      else:
        date_created_values = {
          "@type": "http://resources.wfdm.nrs.gov.bc.ca/fileMetadataResource",
          "metadataName": "DateCreated",
          "metadataValue": doc_json['uploadedOnTimestamp'],
          "metadataType": "STRING"
        }
        doc_json['metadata'].append(date_created_values)
        # Now that they type is updated, we can push in an update
        wfdm_put_response = requests.put(docs_endpoint + '/' + document['fileId'], data=json.dumps(doc_json),  headers={'Authorization': 'Bearer ' + token, 'content-type':'application/json'})
        # verify 200
        if wfdm_put_response.status_code != 200:
          print(wfdm_put_response)
          # Don't fail out here, just cary on
        del wfdm_put_response

      # then, if this is a directory, jump into it and update the documents it contains
      if document['fileType'] == 'DIRECTORY':
        if threadDepth == 0:
          threads = []
          t = threading.Thread(target = enforce_createDate, args=(document['fileId'], 1, row_count, threadDepth + 1))
          threads.append(t)
          for t in threads:
            t.start()
          for t in threads:
            t.join()
        else:
          enforce_createDate(document['fileId'], 1, row_count, 1)
        print('Need to enter directory')

  # check if we need to page
  if wfdm_docs['totalPageCount'] > page:
    # Indeed we do
    print('Multiple pages')
    enforce_createDate(document_id, page + 1, row_count)
  
  # Completed updates and exiting
  return 0

# Step #2, now that we have a nice shiny new token, lets go fetch from WFDM why not
# First though, we need to know our Root ID
print('Fetching The WFDM Root Document...')
wfdm_root_response = requests.get(doc_endpoint + wfdm_root, headers={'Authorization': 'Bearer ' + token})
# verify 200
print(wfdm_root_response.json())
if wfdm_root_response.status_code != 200:
  print(wfdm_root_response)
  sys.exit("Failed to fetch from WFDM. Response code was: " + str(wfdm_root_response.status_code))
# Pull out the fileId, this is our parent for WFDM
root_id = wfdm_root_response.json()['fileId']
del wfdm_root_response
# start the metadata update
print('... Done! Starting createDate append from root document ' + str(root_id))
enforce_createDate(root_id, 1, row_count)

