iam_role_lambda_function = "iam-role-lambda-index-searching"
domain = "terraform-opensearch-dev" 
ebs_volume_size = 10
lambda_function_handler = "ca.bc.gov.nrs.wfdm.wfdm_opensearch_indexing.ProcessSQSMessage"
lambda_payload_filename = "../wfdm-file-index-service/target/wfdm-opensearch-indexing-1.0.jar"
java_layer_name = "aws-java-base-layer-terraform"
layer_file_name = "java.zip"