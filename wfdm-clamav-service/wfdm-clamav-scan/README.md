# Welcome to your CDK TypeScript project!

This is a blank project for TypeScript development with CDK.

The `cdk.json` file tells the CDK Toolkit how to execute your app.

## Useful commands

 * `npm run build`   compile typescript to js
 * `npm run watch`   watch for changes and compile
 * `npm run test`    perform the jest unit tests
 * `cdk deploy`      deploy this stack to your default AWS account/region
 * `cdk diff`        compare deployed stack with current state
 * `cdk synth`       emits the synthesized CloudFormation template

Modifications made to the serverless clamscan index:

Replace VPC creation with finding the exising VPC we defined for this process

Ln 123: 
const vpc = aws_ec2_1.Vpc.fromLookup(this, 'vivid-opensearch-vpc', { isDefault: false, vpcId: 'vpc-07f415cee19113c24' });

Environment param for adding S3Endpoint for gateway endpoints?

Ln 125:
this._s3Gw = vpc.addGatewayEndpoint('S3Endpoint_dlv', {
            service: aws_ec2_1.GatewayVpcEndpointAwsService.S3,
        });

And, for any subsequent releases, use the existing endpoint:
/*this._s3Gw = vpc.addGatewayEndpoint('S3Endpoint', {
    service: aws_ec2_1.GatewayVpcEndpointAwsService.S3,
});*/
this._s3Gw = new aws_ec2_1.GatewayVpcEndpoint(this, 'S3Endpoint', {
  service: aws_ec2_1.GatewayVpcEndpointAwsService.S3,
  vpc: vpc
});

Remove the isolated subnet creation and use exising non-isolated vpc subnets

ln 242:
vpcSubnets: { subnets: vpc.subnets  },

Removed from package.json Dependencies:

 "cdk-serverless-clamscan": "^2.0.0"
 Moved into wfdm-clamav-scan package