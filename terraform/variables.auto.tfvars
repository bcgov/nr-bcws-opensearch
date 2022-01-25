ebs_volume_size = 10
lambda_function_handler = "ca.bc.gov.nrs.wfdm.wfdm_opensearch_indexing.ProcessSQSMessage"
lambda_payload_filename = "../wfdm-file-index-service/target/wfdm-opensearch-indexing-1.0.jar"
java_layer_name = "aws-java-base-layer-terraform"
layer_file_name = "java.zip"
aws_ip_association_id = "eipassoc-0755b417a7fee8939"