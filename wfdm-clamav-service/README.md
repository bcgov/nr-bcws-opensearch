# WFDM ClamAV Lambda 
 nvm use 16.14.0
 npm install -g aws-cdk-lib
 npm install -g aws-cdk@latest (NOTE: you may need to first uninstall your existing aws-cdk, if an old version is present)
 npm install
 cdk bootstrap
 cdk deploy

# Developer Notes
Run all the above commands in wfdm-clamav-service/wfdm-clamav-scan, you'll need environment variables to use cdk

To adjust policy make changes in cdk-serverless-clamscan.js, then run cdk bootstrap. You can preview the changes with cdk diff WfdmClamavStackINT (or whatever environemnt)
To adjust what image you're creating edit wfdm-clamav.js with the environment

You may need to delete the stack if the current policy changes do not allow you to update buckets

To update versions you can set the specfic clamav verison in the dockerfile eg clamav1.4 as the generic clamav verison will be outdated.

# Stack Updates
If you have to fully delete the stack, you'll need to update the clamscan sqs queue in terragrunt.hcl
This may be necessary if bucket policies cause them to not be updateable by CDK deploy.


# ClamAV Functionality
Clamav works with the download_defs lambda getting the current defintions and updating the definitions S3 bucket. The scan lambda takes those defintions and moves them from S3 to EFS once per day on scan.

We're not using freshclam. The private mirror did not work with a private S3 bucket which caused the main freshclam CDN to be contacted on every scan, leaving us persistently rate limited.