ebs_volume_size             = 10
domain                      = "bcwildfireservices.com"
lambda_function_handler     = "ca.bc.gov.nrs.wfdm.wfdm_file_index_service.ProcessSQSMessage"
indexing_function_handler   = "ca.bc.gov.nrs.wfdm.wfdm_file_index_initializer.ProcessSQSMessage::handleRequest"
clamav_function_handler     = "ca.bc.gov.nrs.wfdm.wfdm_clamav_scan_handler.ProcessSQSMessage::handleRequest"
lambda_payload_filename     = "wfdm-opensearch-indexing-1.0.jar"
lambda_initializer_filename = "wfdm-file-index-initializer-1.0.jar"
lambda_clamav_filename      = "wfdm-clamav-scan-handler-1.0.jar"
java_layer_name             = "aws-java-base-layer-terraform"
layer_file_name             = "java.zip"
aws_ip_allocation_id        = "eipalloc-01f46fab6aa887028"
main_route53_zone           = "bcwildfireservices.com"
vpc_id                      = "vpc-07f415cee19113c24"
public_subnet_id            = "subnet-07699eb500d5b4017"
private_subnet_id           = "subnet-04cc3b783b1e1b7f9"
internet_gateway_id         = "igw-015fad32b3bef200d"
security_group_id           = "sg-01eb3f147e2e0dfc0"


