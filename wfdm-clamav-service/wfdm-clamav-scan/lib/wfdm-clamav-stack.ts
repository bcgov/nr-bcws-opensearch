import { Bucket } from '@aws-cdk/aws-s3';
import { Construct, Stack, StackProps, RemovalPolicy, CfnOutput } from '@aws-cdk/core';
import { ServerlessClamscan } from 'cdk-serverless-clamscan';
import { Queue } from '@aws-cdk/aws-sqs';
import {
  SqsDestination
} from '@aws-cdk/aws-lambda-destinations';

export class WfdmClamavStack extends Stack {
  constructor(scope: Construct, id: string, props?: StackProps) {
    super(scope, id, props);

    const queue = new Queue(this, 'wfdmClamscanQueue');
    const sc = new ServerlessClamscan(this, 'wfdmClamscan', {
      onResult: new SqsDestination(queue),
      onError: new SqsDestination(queue),
    });

    const bucket = new Bucket(this, 'wfdm-clamav-bucket', {
      autoDeleteObjects: true, 
      removalPolicy: RemovalPolicy.DESTROY
    });

    sc.addSourceBucket(bucket);

    new CfnOutput(this, 'oBucketName',{
      description: 'The name of the input S3 Bucket',
      value: bucket.bucketName
    })
  }
}