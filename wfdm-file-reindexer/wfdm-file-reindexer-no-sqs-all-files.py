import requests
from requests.auth import HTTPBasicAuth
import sys
import os
import json
import time
from datetime import datetime, UTC

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
max_retries = 3
BEARER = 'Bearer '

print('')
print('-------------------------------------------------------')
print('Starting Re-Index processing')
print('WFDM Paging: ' + str(row_count) + ' rows')
print('Connect to WFDM API: ' + wfdm_api)
print('-------------------------------------------------------')
print('')

def request_with_retry(method, url, **kwargs):
  kwargs.setdefault('timeout', 120)
  for attempt in range(max_retries):
    try:
      response = requests.request(method, url, **kwargs)
      return response
    except requests.exceptions.Timeout:
      print(f'Timeout on attempt {attempt + 1} of {max_retries} for {url}')
      if attempt < max_retries - 1:
        time.sleep(5)
      else:
        print(f'All retries exhausted for {url}, skipping...')
        return None

def reindex_wfdm(document_id, page, row_count):
  print('Re-indexing documents in the folder ' + document_id)
  url = docs_endpoint + doc_root + document_id + '&pageNumber=' + str(page) + '&pageRowCount=' + str(row_count) + '&orderBy=default%20ASC'
  wfdm_docs_response = requests.get(url, headers={'Authorization': BEARER + token})
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


      doc_response = request_with_retry(
          'GET',
          docs_endpoint + '/' + document['fileId'],
          headers={'Authorization': BEARER + token}
      )

      if doc_response is None or doc_response.status_code != 200:
          print('Failed to fetch metadata for document ' + document['fileId'])
          continue

      etag = doc_response.headers.get('ETag')

      document = doc_response.json()

      reindex_date = datetime.now(UTC).strftime('%Y-%m-%d %H:%M:%S')

      metadata = document.get('metadata', [])

      existing = next(
          (m for m in metadata if m.get('metadataName') == 'WFDMReIndexDate'),
          None
      )

      if existing:
          existing['metadataValue'] = reindex_date
      else:
          metadata.append({
              '@type': 'http://resources.wfdm.nrs.gov.bc.ca/fileMetadataResource',
              'metadataName': 'WFDMReIndexDate',
              'metadataValue': reindex_date,
              'metadataType': 'DATE'
          })

      document['metadata'] = metadata



      print('Updating document ' + document['fileId'] + '...')

      wfdm_put_response = request_with_retry(
          'PUT',
          docs_endpoint + '/' + document['fileId'],
          files={
              'resource': (
                  'blob',
                  json.dumps(document),
                  'application/json'
              )
          },
          headers={
              'Authorization': BEARER + token,
              'If-Match': etag
          }
      )

      if wfdm_put_response is None or wfdm_put_response.status_code != 200:
        status = wfdm_put_response.status_code if wfdm_put_response else 'timeout'
        print('Failed to update document ' + document['fileId'] + ': ' + str(status))
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
wfdm_root_response = requests.get(doc_endpoint + wfdm_root, headers={'Authorization': BEARER + token})
if wfdm_root_response.status_code != 200:
  print(wfdm_root_response)
  sys.exit("Failed to fetch from WFDM. Response code was: " + str(wfdm_root_response.status_code))

root_id = wfdm_root_response.json()['fileId']
del wfdm_root_response
print('... Done! Starting re-indexing process from root document ' + str(root_id))
reindex_wfdm(root_id, 1, row_count)