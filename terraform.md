
MODIFYING THE TERRAFORM SCRIPTS			
============================================

The deployment process is reliant on the files in the terraform and terragrunt
directories.

All .tf files in the terraform folder are used to generate or obtain resources or variables.
Each .tf file is scanned on run, and any resources it describes are created or updated.

Key files are as follows:

	main.tf					- Specifies the resources used by the application

	policies.tf				- Contains policy documents referenced by resources in main.tf

	variables.tf			- Variable declarations - note that all terraform variables must be 
							  declared before usage

	variables.auto.tfvars	- A collection of parameters that are common among all environments
	
The .hcl files in the terragrunt folder are used to specify paramenters which differ between
environments. They also specify the terraform repository to be used when deploying

Documentation on how resources are specified can be found at: 
	https://registry.terraform.io/providers/hashicorp/aws/3.72.0/docs




CONFIGURATION OF GIT AND TERRAFORM
============================================

In order to run the terragrunt scripts, you must first configure your git repo and 
terraform repository.

The terraform repository must be configured once per environment. Git only needs to be
configured once per fork.

To configure the terraform repository for a new environment:
	1. Go to https://app.terraform.io/session and log in or create an account
	2. Create a new workspace with a unique name, selecting "API-driven workflow" when 
	   asked to choose workflow type.
	3. In your workspace, go to the "Variables" tab, and create two new sensitive 
	   environment variables: AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
	4. Copy in the relevant access key id and secret access key for an AWS user
	   with appropriate permissions to create, modify, and delete resources

To configure the git repo:
	1. Go to https://app.terraform.io/app/settings/tokens and create an api token
	2. Store the value of the API token in a secure location
	3. Copy that value into a new GitHub Secret (Settings -> Secrets -> Create New Secret)
	   with the name TF_API_TOKEN




CREATING A NEW ENVIRONMENT
============================================

CDK
------------------------------
Before creating a new environment, the CDK script for that environment must be run in
order to create and get the name for the relevant ClamAV stack and queue.

The name of the stack to be used should be like WfdmClamavStack{ENV}

The correct queue to be used will be named starting with:
	wfdmclamavstack{env}-...


S3
-------------------------------
Create a copy of one of the 'wfdm-s3-bucket-{env}' buckets, which contain the source code to be used.



Once that is done, the steps are as follows:

	1. Copy one of the deploy_{env}.yml files in .github\workflows, and rename it appropriately.
	
	2. Replace references to the previous environment with references to the new one. Values that
	   should be updated are:
		   name:
		   on: workflow_dispatch: tags:
		   jobs: environment: name:
		   jobs: steps: working-directory:
		   
		NOTE: failure to replace values may cause conflicts and naming collisions on run
		
	3. Copy one of the terragrunt/{env} folders, renaming it accordingly
	
	4. In its contained terragrunt.hcl folder, update parameters as needed. Note that the
	   following parameters MUST be updated for the new environment to permit clamAV to work
	   and to prevent naming collisions:
		   target_env
		   env_lowercase
		   env_full
		   clamAVStackName 
		   clamstackQueue

	   You must also update the workspace name specified in the 	generate "backend" { ... }
	   statement. Failure to do so will result in the new environment overwriting the existing 
	   environment instead of deploying seperately from it.




EXECUTING A DEPLOYMENT WITH GITHUB ACTIONS
================================================================

In GitHUB, go to the Actions tab.

Select the workflow you would like to use, then click "Run Workflow." This will
open the terraform terminal and run the deployment script in it.

Note that configurations specified in the workflow will overwrite manual changes 
that have been made, if any are present. The terraform terminal will note any
changes or updates that it needs to make, and provide continuous information on the
state of the deployment.




CONFIGURATION OF OPENSEARCH AFTER DEPLOYMENT
============================================


When newly deployed or updated, the opensearch domain will default to using AWS IAM
authentication only, and will only work through the API

To configure it to allow use of the both the UI and connection from the API, you will need to
make the following changes:

	1. Go to the Amazon OpenSearch Service dashboard.
	
	2. Select the wf1-wfdm-opensearch-{env} domain and begin editing the security configuration
	
	3. Make the following changes:
	    1)  Under fine-grained access control, check "Enable fine-grained access control"
			and create master user with appropriate name and passwords
			
		2)	Under Access policy, select "Only use fine-grained access control." This is
			necesary to allow access to the UI
			
	4. Go to the dashboard url for your opensearch domain and login with the master user
	
	5. In the kibana dashboard, go to Security and make the following changes:
		1)	Create a role named Read-Write-Only, with read write and index 
			permissions on *, data, and admin

		2)	Go to security > roles > Read-Write-Only and map the arns for the 
			WF1-WFDM-{env}-Document-API user and the indexing lambda function role as 
			kibana users AND backend roles

	6. Either through Postman or the Kibana Dev Tools, create an index using the following POST request:
	PUT /wf1-wfdm-opensearch-{env}
{
   "mappings":{
      "properties":{
         "key":{
            "type":"text"
         },
         "absoluteFilePath":{
            "type":"text"
         },
         "fileContent":{
            "type":"text"
         },
         "lastModified":{
           "type": "date"
         },
         "lastUpdatedBy":{
            "type":"text"
         },
         "mimeType":{
            "type":"text"
         },
         "fileName":{
            "type":"text"
         },
          "fileRetention":{
            "type":"text"
         },
         "fileLink":{
            "type":"text"
         },
         "fileSize":{
            "type":"text"
         },
        "scanStatus":{
            "type":"text"
         },
         "metadata":{
            "type":"nested",
            "properties":{
               "metadataValue":{
                  "type":"keyword"
               },
               "metadataName":{
                  "type":"keyword"
               }
            }
         },
         "security":{
            "type":"nested",
            "properties":{
               "displayLabel":{
                  "type":"keyword"
               },
               "securityKey":{
                  "type":"keyword"
               }
            }
         },
         "securityScope":{
            "type":"nested",
            "properties":{
               "displayLabel":{
                  "type":"keyword"
               },
               "canReadorWrite":{
                  "type":"keyword"
               }
            }
         },
		
         "filePath": {
        "type": "text",
        "fields": {
          "tree": {
            "type": "text",
            "analyzer": "custom_path_tree"
          }
        }
      }
		
      }
   },
   "settings": {
    "analysis": {
      "analyzer": {
        "custom_path_tree": {
          "tokenizer": "custom_hierarchy"
        },
        "custom_path_tree_reversed": {
          "tokenizer": "custom_hierarchy_reversed"
        }
      },
      "tokenizer": {
        "custom_hierarchy": {
          "type": "path_hierarchy",
          "delimiter": "/"
        },
        "custom_hierarchy_reversed": {
          "type": "path_hierarchy",
          "delimiter": "/",
          "reverse": "true"
        }
      }
    }
  }
} 