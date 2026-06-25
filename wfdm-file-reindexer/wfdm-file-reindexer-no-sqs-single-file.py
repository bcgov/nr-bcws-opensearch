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
docs_endpoint = wfdm_api + 'documents'
# File to reindex
file_id = os.getenv('FILE_ID')
max_retries = 3

print('')
print('-------------------------------------------------------')
print('Starting Re-Index processing for single file')
print('Connect to WFDM API: ' + wfdm_api)
print('File ID: ' + file_id)
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

print('Fetching a token from OAUTH...')
token_response = requests.get(token_service, auth=HTTPBasicAuth(client_name, client_secret))
if token_response.status_code != 200:
  sys.exit("Failed to fetch a token for WFDM. Response code was: " + str(token_response.status_code))

token = token_response.json()['access_token']
del token_response

print('Fetching document ' + file_id + '...')
wfdm_doc_response = requests.get(docs_endpoint + '/' + file_id, headers={'Authorization': 'Bearer ' + token})
if wfdm_doc_response.status_code != 200:
  sys.exit("Failed to fetch document from WFDM. Response code was: " + str(wfdm_doc_response.status_code))

document = wfdm_doc_response.json()
del wfdm_doc_response

print('Fetching bytes for document ' + file_id + '...')
wfdm_bytes_response = request_with_retry('GET',
  docs_endpoint + '/' + file_id + '/bytes',
  headers={'Authorization': 'Bearer ' + token}
)
if wfdm_bytes_response is None or wfdm_bytes_response.status_code != 200:
  sys.exit('Failed to fetch bytes for document ' + file_id)

print('Updating document ' + file_id + '...')
wfdm_put_response = request_with_retry('PUT',
  docs_endpoint + '/' + file_id,
  files={
    'resource': (None, json.dumps(document), 'application/json'),
    'file': (None, wfdm_bytes_response.content, wfdm_bytes_response.headers.get('Content-Type', 'application/octet-stream'))
  },
  headers={'Authorization': 'Bearer ' + token}
)
del wfdm_bytes_response

if wfdm_put_response is None or wfdm_put_response.status_code != 200:
  status = wfdm_put_response.status_code if wfdm_put_response else 'timeout'
  print('Failed to update document ' + file_id + ': ' + str(status))
else:
  print('Successfully updated document ' + file_id)