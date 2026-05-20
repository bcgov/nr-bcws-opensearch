import requests
from requests.auth import HTTPBasicAuth
import sys
import os
import time
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed

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
MAX_WORKERS = 5

token = None
token_fetched_at = None
TOKEN_REFRESH_SECONDS = 7200
token_lock = threading.Lock()

print('')
print('-------------------------------------------------------')
print('Starting Re-Index processing')
print('WFDM Paging: ' + str(row_count) + ' rows')
print('Connect to WFDM API: ' + wfdm_api)
print('-------------------------------------------------------')
print('')

def get_token():
    global token, token_fetched_at
    print('Fetching a token from OAUTH...')
    r = requests.get(token_service, auth=HTTPBasicAuth(client_name, client_secret))
    if r.status_code != 200:
        sys.exit("Failed to fetch token. Response code was: " + str(r.status_code))
    token = r.json()['access_token']
    token_fetched_at = time.time()

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

def reindex_wfdm(document_id, page, row_count, executor, futures_list, futures_lock):
    print('Re-indexing documents in the folder ' + document_id)
    url = docs_endpoint + doc_root + document_id + '&pageNumber=' + str(page) + '&pageRowCount=' + str(row_count) + '&orderBy=default%20ASC'
    wfdm_docs_response = request_with_retry('GET', url, headers=auth_headers())
    if wfdm_docs_response is None or wfdm_docs_response.status_code != 200:
        status = wfdm_docs_response.status_code if wfdm_docs_response else 'timeout'
        print(f'Failed to fetch folder {document_id} page {page}: {status}, skipping...')
        return 0

    wfdm_docs = wfdm_docs_response.json()
    del wfdm_docs_response

    print('Found ' + str(len(wfdm_docs['collection'])) + ' documents, page ' + str(page) + ' of ' + str(wfdm_docs['totalPageCount']))
    for document in wfdm_docs['collection']:
        if document['fileType'] == 'DIRECTORY':
            f = executor.submit(reindex_wfdm, document['fileId'], 1, row_count, executor, futures_list, futures_lock)
            with futures_lock:
                futures_list.append(f)
        else:
            print('Updating document ' + document['fileId'] + '...')
            wfdm_put_response = request_with_retry('PUT',
                docs_endpoint + '/' + document['fileId'],
                json=document,
                headers={**auth_headers(), 'Content-Type': 'application/json'}
            )
            if wfdm_put_response is None or wfdm_put_response.status_code != 200:
                status = wfdm_put_response.status_code if wfdm_put_response else 'timeout'
                print('Failed to update document ' + document['fileId'] + ': ' + str(status))
            else:
                print('Successfully updated document ' + document['fileId'])
            del wfdm_put_response

    if wfdm_docs['totalPageCount'] > page:
        reindex_wfdm(document_id, page + 1, row_count, executor, futures_list, futures_lock)

    return 0

get_token()

print('Fetching The WFDM Root Document...')
wfdm_root_response = request_with_retry('GET', doc_endpoint + wfdm_root, headers=auth_headers())
if wfdm_root_response is None or wfdm_root_response.status_code != 200:
    status = wfdm_root_response.status_code if wfdm_root_response else 'timeout'
    sys.exit("Failed to fetch from WFDM. Response code was: " + str(status))

root_id = wfdm_root_response.json()['fileId']
del wfdm_root_response
print('... Done! Starting re-indexing process from root document ' + str(root_id))

futures_list = []
futures_lock = threading.Lock()

with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
    root_future = executor.submit(reindex_wfdm, root_id, 1, row_count, executor, futures_list, futures_lock)
    with futures_lock:
        futures_list.append(root_future)

    seen = set()
    while True:
        with futures_lock:
            current = list(futures_list)
        new_futures = [f for f in current if id(f) not in seen]
        if not new_futures:
            break
        for f in as_completed(new_futures):
            seen.add(id(f))
            try:
                f.result()
            except Exception as e:
                print(f'Error in thread: {e}, continuing...')
        time.sleep(0.1)

print('')
print('-------------------------------------------------------')
print('Re-indexing complete')
print('-------------------------------------------------------')