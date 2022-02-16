import { Bucket } from 'aws-cdk-lib/aws-s3'
import { App, Stack, StackProps, RemovalPolicy, CfnOutput } from 'aws-cdk-lib'
import { ServerlessClamscan } from '../cdk-serverless-clamscan/cdk-serverless-clamscan'
import { Queue } from 'aws-cdk-lib/aws-sqs'
import {
  SqsDestination
} from 'aws-cdk-lib/aws-lambda-destinations'

export class WfdmClamavStack extends Stack {
  constructor(scope: App, id: string, env: string, props?: StackProps) {
    super(scope, id, props)

    const queue = new Queue(this, 'wfdmClamscanQueue' + env)
    const sc = new ServerlessClamscan(this, 'wfdmClamscan' + env, {
      onResult: new SqsDestination(queue),
      onError: new SqsDestination(queue),
    })

    const bucket = new Bucket(this, 'wfdm-clamav-bucket'+ ((env) ? "-" + env : ""), {
      autoDeleteObjects: true, 
      removalPolicy: RemovalPolicy.DESTROY
    })

    sc.addSourceBucket(bucket)

    new CfnOutput(this, 'oBucketName',{
      description: 'The name of the input S3 Bucket',
      value: bucket.bucketName
    })
  }
}