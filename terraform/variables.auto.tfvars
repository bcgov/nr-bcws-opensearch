ebs_volume_size = 10
domain = "bcwildfireservices.com"
lambda_function_handler = "ca.bc.gov.nrs.wfdm.wfdm_opensearch_indexing.ProcessSQSMessage"
indexing_function_handler = "ca.bc.gov.nrs.wfdm.wfdm_file_index_initializer.ProcessSQSMessage::handleRequest"
lambda_payload_filename = "wfdm-opensearch-indexing-1.0.jar"
lambda_initializer_filename = "wfdm-file-index-initializer-1.0.jar"
java_layer_name = "aws-java-base-layer-terraform"
layer_file_name = "java.zip"
aws_ip_allocation_id = "eipalloc-01f46fab6aa887028"
main_route53_zone = "bcwildfireservices.com"