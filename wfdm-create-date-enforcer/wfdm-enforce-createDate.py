
import requests
from requests.auth import HTTPBasicAuth
import sys
import os
import json
import threading
import time
from concurrent.futures import ThreadPoolExecutor

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
max_retries = 3
MAX_WORKERS = 5

token = None
token_fetched_at = None
TOKEN_REFRESH_SECONDS = 7200 # refresh for token every 2 hours
token_lock = threading.Lock()

print('')
print('-------------------------------------------------------')
print('Starting createDate Update')
print('WFDM Paging: ' + str(row_count) + ' rows')
print('Connect to WFDM API: ' + wfdm_api)
print('-------------------------------------------------------')
print('')

# Step #1, lets go get a token for the API
def get_token():
    global token, token_fetched_at
    print('Fetching a token from OAUTH...')
    token_response = requests.get(token_service, auth=HTTPBasicAuth(client_name, client_secret))
    # verify 200
    if token_response.status_code != 200:
        sys.exit("Failed to fetch a token for WFDM. Response code was: " + str(token_response.status_code))
    token = token_response.json()['access_token']
    token_fetched_at = time.time()
    del token_response

def ensure_token():
    with token_lock:
        if token is None or (time.time() - token_fetched_at) > TOKEN_REFRESH_SECONDS:
            get_token()

def auth_headers():
    ensure_token()
    return {'Authorization': 'Bearer ' + token}

def request_with_retry(method, url, **kwargs):
    kwargs.setdefault('timeout', 120)
    for attempt in range(max_retries):
        try:
            response = requests.request(method, url, **kwargs)
            return response
        except (requests.exceptions.Timeout, requests.exceptions.ConnectionError) as e:
            print(f'Error on attempt {attempt + 1} of {max_retries} for {url}: {e}')
            if attempt < max_retries - 1:
                time.sleep(5)
            else:
                print(f'All retries exhausted for {url}, skipping...')
                return None

def createDate_formatter(unformatted_date):
  return unformatted_date.replace("T", " ").split(".")[0]

# Define our Recursive function
def enforce_createDate(document_id, page, row_count , executor):
  # REMEMBER: There are fetch size limits, so we'll need to be paging data
  # For whatever reason, the page is not a zero-based index
  # This will be recursive, so there's always a stack overflow risk here
  #print('Updating documents in the folder ' + document_id)
  url = docs_endpoint + doc_root + document_id + '&pageNumber=' + str(page) + '&pageRowCount=' + str(row_count) + '&orderBy=default%20ASC'
  wfdm_docs_response = request_with_retry('GET', url, headers=auth_headers())
  # verify 200
  # log out failure but do not stop job
  if wfdm_docs_response is None or wfdm_docs_response.status_code != 200:
      status = wfdm_docs_response.status_code if wfdm_docs_response else 'timeout'
      print(f'Failed to fetch folder {document_id} page {page}: {status}, skipping...')
      return 0

  wfdm_docs = wfdm_docs_response.json()
  del wfdm_docs_response

  #print('Found ' + str(len(wfdm_docs['collection'])) + ' documents, page ' + str(page) + ' of ' + str(wfdm_docs['totalPageCount']))
  # Time to start looping through the documents and checking for a createDate
  for document in wfdm_docs['collection']:
    # Reload the document so we know we have a valid etag and meta records
    # print('Fetching Document ' + document['fileId'] + '...')
    wfdm_doc_response = request_with_retry('GET', docs_endpoint + '/' + document['fileId'], headers=auth_headers())
    # verify 200
    if wfdm_doc_response is None or wfdm_doc_response.status_code != 200:
        status = wfdm_doc_response.status_code if wfdm_doc_response else 'timeout'
        print(f'Failed to fetch document {document["fileId"]}: {status}, skipping...')
    else :  
      # Pull out the fileId, this is our parent for WFDM
      doc_json = wfdm_doc_response.json()
      del wfdm_doc_response
      # First, update the metadata records    
      if doc_json['uploadedOnTimestamp'] != None and not any(x['metadataName'] == "DateCreated" for x in doc_json['metadata']):
        print("Adding DateCreated to file " + doc_json['fileId'])
        date_created_values = {
          "@type": "http://resources.wfdm.nrs.gov.bc.ca/fileMetadataResource",
          "metadataName": "DateCreated",
          "metadataValue": createDate_formatter(doc_json['uploadedOnTimestamp']),
          "metadataType": "STRING"
        }
        doc_json['metadata'].append(date_created_values)
        # Now that they type is updated, we can push in an update
        wfdm_put_response = request_with_retry('PUT', docs_endpoint + '/' + document['fileId'], data=json.dumps(doc_json),  headers={**auth_headers(), 'content-type':'application/json'})
        # verify 200
        if wfdm_put_response is None or wfdm_put_response.status_code != 200:
            status = wfdm_put_response.status_code if wfdm_put_response else 'timeout'
            print(f'Failed to PUT document {document["fileId"]}: {status}, skipping...')
        # Don't fail out here, just cary on
        del wfdm_put_response

      elif any(x['metadataName'] == "DateCreated" for x in doc_json['metadata']):
        for idx, metadata_item in enumerate(doc_json['metadata']):
          if metadata_item['metadataName'] == 'DateCreated' and doc_json['uploadedOnTimestamp']:
            print(doc_json['metadata'][idx]['metadataValue'])
            doc_json['metadata'][idx]['metadataValue'] = createDate_formatter(doc_json['uploadedOnTimestamp'])
            print(doc_json['metadata'][idx]['metadataValue'])
            wfdm_put_response = request_with_retry('PUT', docs_endpoint + '/' + document['fileId'], data=json.dumps(doc_json),  headers={**auth_headers(), 'content-type':'application/json'})
        # verify 200
            if wfdm_put_response is None or wfdm_put_response.status_code != 200:
                status = wfdm_put_response.status_code if wfdm_put_response else 'timeout'
                print(f'Failed to PUT document {document["fileId"]}: {status}, skipping...')
            print('Formatted ' + doc_json['fileId'] + ' dateCreated value')


      # then, if this is a directory, jump into it and update the documents it contains
      if document['fileType'] == 'DIRECTORY':
         executor.submit(enforce_createDate, document['fileId'], 1, row_count, executor)

  # check if we need to page
  if wfdm_docs['totalPageCount'] > page:
    # Indeed we do
    print('Thread count is ' + str(threading.active_count()))
    enforce_createDate(document_id, page + 1, row_count , executor)
  
  # Completed updates and exiting
  return 0

get_token()

# Step #2, now that we have a nice shiny new token, lets go fetch from WFDM why not
# First though, we need to know our Root ID
print('Fetching The WFDM Root Document...')
wfdm_root_response = request_with_retry('GET', doc_endpoint + wfdm_root, headers=auth_headers())
if wfdm_root_response is None or wfdm_root_response.status_code != 200:
  status = wfdm_root_response.status_code if wfdm_root_response else 'timeout'
  sys.exit("Failed to fetch from WFDM. Response code was: " + str(wfdm_root_response.status_code))
# Pull out the fileId, this is our parent for WFDM
root_id = wfdm_root_response.json()['fileId']
del wfdm_root_response
# start the metadata update
print('... Done! Starting createDate append from root document ' + str(root_id))
with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
    enforce_createDate(root_id, 1, row_count, executor)


