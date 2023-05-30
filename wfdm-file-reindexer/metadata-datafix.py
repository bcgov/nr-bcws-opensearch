import requests
from requests.auth import HTTPBasicAuth
import boto3
import sys
import os

token_service = 'https://intapps.nrs.gov.bc.ca/pub/oauth2/v1/oauth/token?disableDeveloperFilter=true&response_type=token&grant_type=client_credentials'
# Client, use Basic Auth
client_name = 'WFDM_DOCUMENTS_INDEX'
client_secret = 'Password'
# WFDM API endpoints
wfdm_api = 'https://i1bcwsapi.nrs.gov.bc.ca/wfdm-document-management-api/'
doc_endpoint = wfdm_api + 'document' # can we take a moment to recognize that this is a poor API naming scheme
docs_endpoint = wfdm_api + 'documents'
wfdm_root = '?filePath=%2F'
doc_root = '?parentId='
# AWS Client
# Create SQS client
session = boto3.Session(
    aws_access_key_id=os.getenv('AKIAWWHKPWO3KIFOMHN2'),
    aws_secret_access_key=os.getenv('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')
)
sqs = boto3.client('sqs') # use this for local testing
queue_url = 'https://sqs.ca-central-1.amazonaws.com/460053263286/WF1-WFDM-sqs-queue-INT'
sqs_delay = os.getenv('SQS_MESSAGE_DELAY')
# Some default process settings
row_count = 20
clam_scan = os.getenv('AV_SCAN')

print('')
print('-------------------------------------------------------')
print('Starting Re-Index processing')
print('WFDM Paging: ' + str(row_count) + ' rows')
print('Run ClamAV Scan? ' + str(clam_scan))
print('Connect to AWS Queue: ' + queue_url)
print('Connect to WFDM API: ' + wfdm_api)
print('-------------------------------------------------------')
print('')

# Define our Recursive function
def reindex_wfdm_metadata(document_id, page, row_count):
  # REMEMBER: There are fetch size limits, so we'll need to be paging data
  # For whatever reason, the page is not a zero-based index
  # This will be recursive, so there's always a stack overflow risk here
  print('Re-indexing documents in the folder ' + document_id)
  url = docs_endpoint + doc_root + document_id + '&pageNumber=' + str(page) + '&pageRowCount=' + str(row_count) + '&orderBy=default%20ASC'
  wfdm_docs_response = requests.get(url, headers={'Authorization': 'Bearer ' + token})
  # verify 200
  if wfdm_docs_response.status_code != 200:
    print(wfdm_docs_response)
    sys.exit("Failed to fetch from WFDM. Response code was: " + str(wfdm_docs_response.status_code))

  wfdm_docs = wfdm_docs_response.json()
  del wfdm_docs_response

  print('Found ' + str(len(wfdm_docs['collection'])) + ' documents, page ' + str(page) + ' of ' + str(wfdm_docs['totalPageCount']))
  # Time to start looping through the documents here and re-indexing.
  # If the document is a DIRECTORY, then we don't actually index or scan, we just kick off
  # another call to reindex_wfdm. Remember to set the page back to 1
  for document in wfdm_docs['collection']:
    if document['fileType'] == 'DIRECTORY':
      reindex_wfdm(document['fileId'], 1, row_count)
    else:
      # We found a file, so we can force an update and kick off a re-index process
      # if scan == true, we should grab the bytes and set eventType to bytes
      event = 'meta' if clam_scan == False else 'bytes'
      sqs.send_message(
        QueueUrl=queue_url,
        DelaySeconds=sqs_delay,
        MessageAttributes={},
        MessageBody=(
            '{"fileId":"' + document['fileId'] + '","fileVersionNumber":"' + str(document['versionNumber']) + '","eventType":"' + event + '","fileType":"' + document['fileType'] + '"}'
        )
      )

  # check if we need to page
  if wfdm_docs['totalPageCount'] > page:
    # Indeed we do
    reindex_wfdm(document_id, page + 1, row_count)
  
  # At this point, the reindex_wfdm call is complete and will exit
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
# start the re-indexing with our recursive reindex function
print('... Done! Starting re-indexing process from root document ' + str(root_id))
reindex_wfdm(root_id, 1, row_count)
