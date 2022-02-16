#!/usr/bin/env node
import 'source-map-support/register';
import { App } from 'aws-cdk-lib';
import { WfdmClamavStack } from '../lib/wfdm-clamav-stack';

const app = new App();

new WfdmClamavStack(app, 'WfdmClamavStackTST', 'tst', 
{
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT, 
    region: process.env.CDK_DEFAULT_REGION
  }
  // Adding the account and region is needed for looking up
  // an existing VPC. Without this, it will attempt to create
  // a new VPC. They can be configured via env variables, or
  // set to lookup machine defaults
});