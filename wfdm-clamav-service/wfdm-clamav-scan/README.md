# WFDM Serverless Clamscan CDK

This is the CDK project for the generation and deployment of Cloudformation scripts dockerfiles, and lambdas needed to publish a ClamAV service, s3 buckets for scanning and storing virus definitions, automatic update of virus definitions, and the queues needed to report and tag WFDM documents during and after virus scanning is complete. This piece is a part of the broader WFDM AWS services used in virus scanning and parsing documents for use with advanced searching.

## CDK details

For more information on AWS publishing with CDK, see: https://aws.amazon.com/cdk/

### Important parts

The CDK has been built in Typescript, and contains a number of important files needed for building and running the CDK synth and publish.

#### cdk.json:
The `cdk.json` file tells the CDK Toolkit how to execute your app.

#### package.json:
The `package.json` contains instructions on what deveopment, peer and active dependencies are needed to transpile and run your CDK

## Building the CDK

Before you can run the publish, you will need to set up your environment and build the CDK scripts. The following instructions will help you get set up.

### Install NodeJS (And maybe nvm)

You can find installation packages for Nodejs here: https://nodejs.org/en/

This CDK was build using Node v16.14.0

If you already have a version of Nodejs installed but would like to install a different version, you can use various Node Version Managers. On windows, we used NVM: https://github.com/coreybutler/nvm-windows

To install NVM, download the windows installer and following the instructions provided by the wizard. First, go here: https://github.com/coreybutler/nvm-windows/releases

And download the latest installer zip.

Once NVM is installed, it will detect any current version of Nodejs you have available. To install additional versions use the following commands:

```bash
nvm install {version}
nvm use {version}
```

You can view all of your installed versions of Nodejs at any time with the `nvm list` command.

After installing a version of Nodejs with nvm, you will have to reinstall global utilities (e.g. yarn), as they'll be tied to your version. This includes CDK tools.

### Install the AWS tools

See: https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html

To update your current installation of AWS CLI on Windows, download a new installer each time you update to overwrite previous versions. AWS CLI is updated regularly. To see when the latest version was released, see the AWS CLI changelog on GitHub.

Download and run the AWS CLI MSI installer for Windows (64-bit): https://awscli.amazonaws.com/AWSCLIV2.msi

Alternatively, you can run the msiexec command to run the MSI installer.

```bash
msiexec.exe /i https://awscli.amazonaws.com/AWSCLIV2.msi
```

For various parameters that can be used with msiexec, see msiexec on the Microsoft Docs website.

To confirm the installation, open the Start menu, search for cmd to open a command prompt window, and at the command prompt use the aws --version command.

```bash
aws --version aws-cli/2.4.5 Python/3.8.8 Windows/10 exe/AMD64 prompt/off
```

If Windows is unable to find the program, you might need to close and reopen the command prompt window to refresh the path, or Adding the AWS CLI to your path.

### Install the CDK tools

See: https://docs.aws.amazon.com/cdk/v2/guide/cli.html

The AWS CDK Toolkit, the CLI command cdk, is the primary tool for interacting with your AWS CDK app. It executes your app, interrogates the application model you defined, and produces and deploys the AWS CloudFormation templates generated by the AWS CDK. It also provides other features useful for creating and working with AWS CDK projects. This topic contains information about common use cases of the CDK Toolkit.

The AWS CDK Toolkit is installed with the Node Package Manager. In most cases, we recommend installing it globally.

```bash
npm install -g aws-cdk             # install latest version
npm install -g aws-cdk@X.YY.Z      # install specific version
```

### Install dependencies

The dependencies defined in the `package.json` file. To install these, use tne Node Package Manager.

Note that your CMD prompt must be in the same path as your package.json.

```bash
cd \path\to\wfdm-clamav-scan
npm install
```

### Build

Once dependencies have been installed, you can transpile the Typescript code into javascript and prepare it for execution. To do this, run the following command:

```bash
npm run build
```

If this process fails, verify your installation of typescript, and ensure you've installed all dependencies successfully.

If you make any changes to the CDK code, you will need to run a build before you can rerun the deployment or synth process.

### Deploy

To deploy your CDK, there are a few steps you need to follow, depending on how you want to deploy.

First, we need to set up our credentials for AWS. First, set your default regions and profile:

```bash
# as a default
aws configure set default.region ca-central-1
aws configure set default.aws_access_key_id my_access_key
aws configure set default.aws_secret_access_key my_secret_key
# or, if you prefer to use a profile
aws configure set profile.Vivid-infra.region ca-central-1
aws configure set profile.Vivid-infra.aws_access_key_id my_access_key
aws configure set profile.Vivid-infra.aws_secret_access_key my_secret_key
```

This will create `credentials` and `config` files in your \users\<username>\.aws directory. Alternatively, you can manually create these files.

Once the configured defaults are in place, you'll be able to run a build.

```bash
npm run build
```

If you make any changes to the code, you'll have to run a build before you deploy.

If you've never run a `cdk deploy` before for this environment, you'll need to bootstrap first. Run the following command to initialize your cdk environment:

```bash
cdk bootstrap
```

Once a build has completed successfully and you've bootstrapped your environment, run a synth if you just want to generate the cloudformation template, or a deploy if you want to create the cdk outputs and deploy them.

```bash
cdk synth
# or, to deploy
cdk deploy
```

When you run `cdk deploy`, this will generate cdk scripts and populate them into a folder called `cdk.out`. At this point, if you have to complete any manual changes to the script, you can cancel deploy and make your changes. You can also make changes to these scripts before verifying the deployment, and the CDK process will immediately execute those changes as well.

### Useful commands

 * `npm run build`   compile typescript to js
 * `npm run watch`   watch for changes and compile
 * `npm run test`    perform the jest unit tests
 * `cdk deploy`      deploy this stack to your default AWS account/region
 * `cdk diff`        compare deployed stack with current state
 * `cdk synth`       emits the synthesized CloudFormation template

### Modifications made to the serverless clamscan component:

Replace VPC creation with finding the existing VPC we defined for this process

Ln 123: 

```typescript
const vpc = aws_ec2_1.Vpc.fromLookup(this, 'vivid-opensearch-vpc', { isDefault: false, vpcId: 'vpc-07f415cee19113c24' });
```

Environment param for adding S3Endpoint for gateway endpoints?

Ln 125:

```typescript
this._s3Gw = vpc.addGatewayEndpoint('S3Endpoint_dlv', {
            service: aws_ec2_1.GatewayVpcEndpointAwsService.S3,
        });
```

And, for any subsequent releases, use the existing endpoint:

```typescript
/*this._s3Gw = vpc.addGatewayEndpoint('S3Endpoint', {
    service: aws_ec2_1.GatewayVpcEndpointAwsService.S3,
});*/
this._s3Gw = new aws_ec2_1.GatewayVpcEndpoint(this, 'S3Endpoint', {
  service: aws_ec2_1.GatewayVpcEndpointAwsService.S3,
  vpc: vpc
});
```

Remove the isolated subnet creation and use existing non-isolated vpc subnets

ln 242:

```typescript
vpcSubnets: { subnets: vpc.subnets  },
```

Removed from package.json Dependencies:

```json
 "cdk-serverless-clamscan": "^2.0.0"
```

Moved into wfdm-clamav-scan package
