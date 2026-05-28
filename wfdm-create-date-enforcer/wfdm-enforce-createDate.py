
import requests
from requests.auth import HTTPBasicAuth
import sys
import os
import json
import time

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

token = None
token_expiry = 0

def createDate_formatter(unformatted_date):
  return unformatted_date.replace("T", " ").split(".")[0]


def get_original_timestamp(doc_json):
    for v in doc_json.get('versions', []):
        if v.get('versionNumber') == 1 and v.get('uploadedOnTimestamp'):
            return v['uploadedOnTimestamp']
    return None


def get_token():
    global token, token_expiry

    if token and time.time() < token_expiry - 60:
        return token

    print("Refreshing token...")
    response = requests.get(token_service, auth=HTTPBasicAuth(client_name, client_secret))

    if response.status_code != 200:
        sys.exit("Failed to fetch token")

    data = response.json()
    token = data['access_token']

    expires_in = data.get('expires_in', 3600)
    token_expiry = time.time() + expires_in

    return token

def api_request(method, url, **kwargs):
    global token

    headers = kwargs.get('headers', {})
    headers['Authorization'] = 'Bearer ' + get_token()
    kwargs['headers'] = headers

    try:
        response = requests.request(method, url, timeout=30, **kwargs)

        if response.status_code == 401:
            print("Token expired, refreshing and retrying...")
            token = None  

            headers['Authorization'] = 'Bearer ' + get_token()
            response = requests.request(method, url, timeout=30, **kwargs)

        return response

    except requests.exceptions.RequestException as e:
        raise e  
    
def save_checkpoint(document_id, page):
    with open("checkpoint.json", "w") as f:
        json.dump({
            "document_id": document_id,
            "page": page
        }, f)




# Step #1, lets go get a token for the API
print('Fetching a token from OAUTH...')
get_token()

print('')
print('-------------------------------------------------------')
print('Starting createDate Update')
print('WFDM Paging: ' + str(row_count) + ' rows')
print('Connect to WFDM API: ' + wfdm_api)
print('-------------------------------------------------------')
print('')


# Define our Recursive function
def enforce_createDate(document_id, page, row_count):
  # REMEMBER: There are fetch size limits, so we'll need to be paging data
  # For whatever reason, the page is not a zero-based index
  # This will be recursive, so there's always a stack overflow risk here
  #print('Updating documents in the folder ' + document_id)
  url = docs_endpoint + doc_root + document_id + '&pageNumber=' + str(page) + '&pageRowCount=' + str(row_count) + '&orderBy=default%20ASC'
  try:
    wfdm_docs_response = api_request("GET", url)
    # verify 200
    if wfdm_docs_response.status_code != 200:
      print(f'Failed to fetch folder {document_id} page {page}: {wfdm_docs_response.status_code}, skipping...')
      return 0
  
  except requests.exceptions.RequestException as e:
      print(f"GET failed for folder {document_id} page {page}: {e}")
      return 0

  wfdm_docs = wfdm_docs_response.json()
  del wfdm_docs_response

  #print('Found ' + str(len(wfdm_docs['collection'])) + ' documents, page ' + str(page) + ' of ' + str(wfdm_docs['totalPageCount']))
  # Time to start looping through the documents and checking for a createDate
  for document in wfdm_docs['collection']:
    # Reload the document so we know we have a valid etag and meta records
    # print('Fetching Document ' + document['fileId'] + '...')
    wfdm_doc_response = api_request("GET", docs_endpoint + '/' + document['fileId'])
    # verify 200
    if wfdm_doc_response.status_code != 200:
      print(wfdm_doc_response)
    else :
      # Pull out the fileId, this is our parent for WFDM
      doc_json = wfdm_doc_response.json()
      del wfdm_doc_response
      # First, update the metadata records
      if doc_json['fileType'] != 'DIRECTORY' and doc_json['uploadedOnTimestamp'] != None and not any(x['metadataName'] == "DateCreated" for x in doc_json['metadata']):
        print("Adding DateCreated to file " + doc_json['fileId'])
        original_ts = get_original_timestamp(doc_json)

        date_created_values = {
          "@type": "http://resources.wfdm.nrs.gov.bc.ca/fileMetadataResource",
          "metadataName": "DateCreated",
          "metadataValue": createDate_formatter(original_ts),
          "metadataType": "STRING"
        }
        doc_json['metadata'].append(date_created_values)
        # Now that they type is updated, we can push in an update
        try:
          wfdm_put_response = api_request("PUT", docs_endpoint + '/' + document['fileId'], data=json.dumps(doc_json), params={'metadataUpdate': 'true'}, headers={ 'content-type':'application/json'})
          # verify 200
          if wfdm_put_response.status_code != 200:
            print(wfdm_put_response)
            # Don't fail out here, just cary on
          del wfdm_put_response
        except requests.exceptions.RequestException as e:
                print(f"PUT failed for file {document['fileId']}: {e}")
                continue 
      elif doc_json['fileType'] != 'DIRECTORY' and any(x['metadataName'] == "DateCreated" for x in doc_json['metadata']):
        for idx, metadata_item in enumerate(doc_json['metadata']):
          if metadata_item['metadataName'] == 'DateCreated':
            # use to compare the version 1 timestamp, to the date created metadata field
            # if both exist and match we can skip updating
            original_ts = get_original_timestamp(doc_json)
            new_createdate_value = createDate_formatter(original_ts)
            existing_createdate_value = metadata_item['metadataValue']

            if existing_createdate_value == new_createdate_value:
                        print(f"Skipping {doc_json['fileId']} (already correct)")
                        break

            doc_json['metadata'][idx]['metadataValue'] = new_createdate_value
            print(doc_json['metadata'][idx]['metadataValue'])
            try:
              wfdm_put_response = api_request("PUT", docs_endpoint + '/' + document['fileId'], data=json.dumps(doc_json), params={'metadataUpdate': 'true'}, headers={ 'content-type':'application/json'})
              # verify 200
              if wfdm_put_response.status_code != 200:
                print(wfdm_put_response)
              print('Formatted ' + doc_json['fileId'] + ' dateCreated value')
            
            except requests.exceptions.RequestException as e:
                print(f"PUT failed for file {document['fileId']}: {e}")
                continue 
            break


      # then, if this is a directory, jump into it and update the documents it contains
      if document['fileType'] == 'DIRECTORY':
          enforce_createDate(document['fileId'], 1, row_count)

  # check if we need to page
  if wfdm_docs['totalPageCount'] > page:
    # use the values from this instead of root_id and 1 at the first instance of enforce_createDate to allow recovery
    save_checkpoint(document_id, page)
    # Indeed we do
    enforce_createDate(document_id, page + 1, row_count)

  # Completed updates and exiting
  return 0

# Step #2, now that we have a nice shiny new token, lets go fetch from WFDM why not
# First though, we need to know our Root ID
print('Fetching The WFDM Root Document...')
try:
  wfdm_root_response = api_request("GET", doc_endpoint + wfdm_root)
  if wfdm_root_response.status_code != 200:
    print(wfdm_root_response)
    sys.exit("Failed to fetch from WFDM. Response code was: " + str(wfdm_root_response.status_code))
    
except requests.exceptions.RequestException as e:
    sys.exit(f"Root fetch failed: {e}")

# Pull out the fileId, this is our parent for WFDM
root_id = wfdm_root_response.json()['fileId']
del wfdm_root_response
# start the metadata update
print('... Done! Starting createDate append from root document ' + str(root_id))
#enforce_createDate(root_id, 1, row_count)
# if script crashes update root_id and 1 with the document id and page number to resume from
enforce_createDate(root_id, 1, row_count)

