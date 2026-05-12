import requests
from requests.auth import HTTPBasicAuth
import sys
import os
import json

# Token service, for fetching a token
token_service = os.getenv('TOKEN_SERVICE')
# Client, use Basic Auth
client_name = os.getenv('CLIENT')
client_secret = os.getenv('CLIENT_SECRET')
# WFDM API endpoints
wfdm_api = os.getenv('WFDM_API_URL')
doc_endpoint = wfdm_api + 'document'
docs_endpoint = wfdm_api + 'documents'
wfdm_root = '?filePath=%2F'
doc_root = '?parentId='
# Some default process settings
row_count = os.getenv('QUERY_ROW_COUNT')

print('')
print('-------------------------------------------------------')
print('Starting Re-Index processing')
print('WFDM Paging: ' + str(row_count) + ' rows')
print('Connect to WFDM API: ' + wfdm_api)
print('-------------------------------------------------------')
print('')

def reindex_wfdm(document_id, page, row_count):
  print('Re-indexing documents in the folder ' + document_id)
  url = docs_endpoint + doc_root + document_id + '&pageNumber=' + str(page) + '&pageRowCount=' + str(row_count) + '&orderBy=default%20ASC'
  wfdm_docs_response = requests.get(url, headers={'Authorization': 'Bearer ' + token})
  if wfdm_docs_response.status_code != 200:
    print(wfdm_docs_response)
    sys.exit("Failed to fetch from WFDM. Response code was: " + str(wfdm_docs_response.status_code))

  wfdm_docs = wfdm_docs_response.json()
  del wfdm_docs_response

  print('Found ' + str(len(wfdm_docs['collection'])) + ' documents, page ' + str(page) + ' of ' + str(wfdm_docs['totalPageCount']))
  for document in wfdm_docs['collection']:
    if document['fileType'] == 'DIRECTORY':
      reindex_wfdm(document['fileId'], 1, row_count)
    else:
      print('Fetching bytes for document ' + document['fileId'] + '...')
      wfdm_bytes_response = requests.get(
        docs_endpoint + '/' + document['fileId'] + '/bytes',
        headers={'Authorization': 'Bearer ' + token}
      )
      if wfdm_bytes_response.status_code != 200:
        print('Failed to fetch bytes for document ' + document['fileId'] + ': ' + str(wfdm_bytes_response.status_code))
        continue

      print('Updating document ' + document['fileId'] + '...')
      wfdm_put_response = requests.put(
        docs_endpoint + '/' + document['fileId'],
        files={
          'resource': (None, json.dumps(document), 'application/json'),
          'file': (None, wfdm_bytes_response.content, wfdm_bytes_response.headers.get('Content-Type', 'application/octet-stream'))
        },
        headers={'Authorization': 'Bearer ' + token}
      )
      del wfdm_bytes_response

      if wfdm_put_response.status_code != 200:
        print('Failed to update document ' + document['fileId'] + ': ' + str(wfdm_put_response.status_code))
      else:
        print('Successfully updated document ' + document['fileId'])
      del wfdm_put_response

  if wfdm_docs['totalPageCount'] > page:
    reindex_wfdm(document_id, page + 1, row_count)

  return 0

print('Fetching a token from OAUTH...')
token_response = requests.get(token_service, auth=HTTPBasicAuth(client_name, client_secret))
if token_response.status_code != 200:
  sys.exit("Failed to fetch a token for WFDM. Response code was: " + str(token_response.status_code))

token = token_response.json()['access_token']
del token_response

print('Fetching The WFDM Root Document...')
wfdm_root_response = requests.get(doc_endpoint + wfdm_root, headers={'Authorization': 'Bearer ' + token})
if wfdm_root_response.status_code != 200:
  print(wfdm_root_response)
  sys.exit("Failed to fetch from WFDM. Response code was: " + str(wfdm_root_response.status_code))

root_id = wfdm_root_response.json()['fileId']
del wfdm_root_response
print('... Done! Starting re-indexing process from root document ' + str(root_id))
reindex_wfdm(root_id, 1, row_count)