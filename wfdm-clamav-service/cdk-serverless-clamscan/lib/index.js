"use strict";
var _a;
Object.defineProperty(exports, "__esModule", { value: true });
exports.ServerlessClamscan = void 0;
const JSII_RTTI_SYMBOL_1 = Symbol.for("jsii.rtti");
// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
const path = require("path");
const aws_cdk_lib_1 = require("aws-cdk-lib");
const aws_ec2_1 = require("aws-cdk-lib/aws-ec2");
const aws_efs_1 = require("aws-cdk-lib/aws-efs");
const aws_events_1 = require("aws-cdk-lib/aws-events");
const aws_events_targets_1 = require("aws-cdk-lib/aws-events-targets");
const aws_iam_1 = require("aws-cdk-lib/aws-iam");
const aws_lambda_1 = require("aws-cdk-lib/aws-lambda");
const aws_lambda_destinations_1 = require("aws-cdk-lib/aws-lambda-destinations");
const aws_lambda_event_sources_1 = require("aws-cdk-lib/aws-lambda-event-sources");
const aws_s3_1 = require("aws-cdk-lib/aws-s3");
const aws_sqs_1 = require("aws-cdk-lib/aws-sqs");
const constructs_1 = require("constructs");
const gatewayVpcEndpointService = aws_ec2_1.IGatewayVpcEndpointService;
/**
 * An [aws-cdk](https://github.com/aws/aws-cdk) construct that uses [ClamAV®](https://www.clamav.net/). to scan objects in Amazon S3 for viruses. The construct provides a flexible interface for a system to act based on the results of a ClamAV virus scan.
 *
 * The construct creates a Lambda function with EFS integration to support larger files.
 * A VPC with isolated subnets, a S3 Gateway endpoint will also be created.
 *
 * Additionally creates an twice-daily job to download the latest ClamAV definition files to the
 * Virus Definitions S3 Bucket by utilizing an EventBridge rule and a Lambda function and
 * publishes CloudWatch Metrics to the 'serverless-clamscan' namespace.
 *
 * __Important O&M__:
 * When ClamAV publishes updates to the scanner you will see “Your ClamAV installation is OUTDATED” in your scan results.
 * While the construct creates a system to keep the database definitions up to date, you must update the scanner to
 * detect all the latest Viruses.
 *
 * Update the docker images of the Lambda functions with the latest version of ClamAV by re-running `cdk deploy`.
 *
 * Successful Scan Event format
 * ```json
 * {
 *     "source": "serverless-clamscan",
 *     "input_bucket": <input_bucket_name>,
 *     "input_key": <object_key>,
 *     "status": <"CLEAN"|"INFECTED"|"N/A">,
 *     "message": <scan_summary>,
 *   }
 * ```
 *
 * Note: The Virus Definitions bucket policy will likely cause a deletion error if you choose to delete
 * the stack associated in the construct. However since the bucket itself gets deleted, you can delete
 * the stack again to resolve the error.
 *
 * @stability stable
 */
class ServerlessClamscan extends constructs_1.Construct {
    /**
     * Creates a ServerlessClamscan construct.
     *
     * @param scope The parent creating construct (usually `this`).
     * @param id The construct's name.
     * @param props A `ServerlessClamscanProps` interface.
     * @stability stable
     */
    constructor(scope, id, props) {
        var _b, _c;
        super(scope, id);
        this._efsRootPath = '/lambda';
        this._efsMountPath = `/mnt${this._efsRootPath}`;
        this._efsDefsPath = 'virus_database/';
        if (!props.onResult) {
            this.resultBus = new aws_events_1.EventBus(this, 'ScanResultBus');
            this.resultDest = new aws_lambda_destinations_1.EventBridgeDestination(this.resultBus);
            this.infectedRule = new aws_events_1.Rule(this, 'InfectedRule', {
                eventBus: this.resultBus,
                description: 'Event for when a file is marked INFECTED',
                eventPattern: {
                    detail: {
                        responsePayload: {
                            source: ['serverless-clamscan'],
                            status: ['INFECTED'],
                        },
                    },
                },
            });
            this.cleanRule = new aws_events_1.Rule(this, 'CleanRule', {
                eventBus: this.resultBus,
                description: 'Event for when a file is marked CLEAN',
                eventPattern: {
                    detail: {
                        responsePayload: {
                            source: ['serverless-clamscan'],
                            status: ['CLEAN'],
                        },
                    },
                },
            });
        }
        else {
            this.resultDest = props.onResult;
        }
        if (!props.onError) {
            this.errorDeadLetterQueue = new aws_sqs_1.Queue(this, 'ScanErrorDeadLetterQueue', {
                encryption: aws_sqs_1.QueueEncryption.KMS_MANAGED,
            });
            this.errorQueue = new aws_sqs_1.Queue(this, 'ScanErrorQueue', {
                encryption: aws_sqs_1.QueueEncryption.KMS_MANAGED,
                deadLetterQueue: {
                    maxReceiveCount: 3,
                    queue: this.errorDeadLetterQueue,
                },
            });
            this.errorDest = new aws_lambda_destinations_1.SqsDestination(this.errorQueue);
            const cfnDlq = this.errorDeadLetterQueue.node.defaultChild;
            cfnDlq.addMetadata('cdk_nag', {
                rules_to_suppress: [
                    { id: 'AwsSolutions-SQS3', reason: 'This queue is a DLQ.' },
                ],
            });
        }
        else {
            this.errorDest = props.onError;
        }
        const vpc = aws_ec2_1.Vpc.fromLookup(this, 'vivid-opensearch-vpc', { isDefault: false, vpcId: 'vpc-07f415cee19113c24' });
        vpc.addFlowLog('FlowLogs');
        /*this._s3Gw = vpc.addGatewayEndpoint('S3Endpoint', {
            service: aws_ec2_1.GatewayVpcEndpointAwsService.S3,
        });*/
        this._s3Gw = new aws_ec2_1.GatewayVpcEndpoint(this, 'S3Endpoint', {
          service: aws_ec2_1.GatewayVpcEndpointAwsService.S3,
          vpc: vpc
        });
        const fileSystem = new aws_efs_1.FileSystem(this, 'ScanFileSystem', {
            vpc: vpc,
            encrypted: props.efsEncryption === false ? false : true,
            lifecyclePolicy: aws_efs_1.LifecyclePolicy.AFTER_7_DAYS,
            performanceMode: aws_efs_1.PerformanceMode.GENERAL_PURPOSE,
            removalPolicy: aws_cdk_lib_1.RemovalPolicy.DESTROY,
            securityGroup: new aws_ec2_1.SecurityGroup(this, 'ScanFileSystemSecurityGroup', {
                vpc: vpc,
                allowAllOutbound: false,
            }),
        });
        const lambda_ap = fileSystem.addAccessPoint('ScanLambdaAP', {
            createAcl: {
                ownerGid: '1000',
                ownerUid: '1000',
                permissions: '755',
            },
            posixUser: {
                gid: '1000',
                uid: '1000',
            },
            path: this._efsRootPath,
        });
        const logs_bucket = (_b = props.defsBucketAccessLogsConfig) === null || _b === void 0 ? void 0 : _b.logsBucket;
        const logs_bucket_prefix = (_c = props.defsBucketAccessLogsConfig) === null || _c === void 0 ? void 0 : _c.logsPrefix;
        if (logs_bucket === true || logs_bucket === undefined) {
            this.defsAccessLogsBucket = new aws_s3_1.Bucket(this, 'VirusDefsAccessLogsBucket', {
                encryption: aws_s3_1.BucketEncryption.S3_MANAGED,
                removalPolicy: aws_cdk_lib_1.RemovalPolicy.RETAIN,
                serverAccessLogsPrefix: 'access-logs-bucket-logs',
                blockPublicAccess: {
                    blockPublicAcls: true,
                    blockPublicPolicy: true,
                    ignorePublicAcls: true,
                    restrictPublicBuckets: true,
                },
            });
            this.defsAccessLogsBucket.addToResourcePolicy(new aws_iam_1.PolicyStatement({
                effect: aws_iam_1.Effect.DENY,
                actions: ['s3:*'],
                resources: [
                    this.defsAccessLogsBucket.arnForObjects('*'),
                    this.defsAccessLogsBucket.bucketArn,
                ],
                principals: [new aws_iam_1.AnyPrincipal()],
                conditions: {
                    Bool: {
                        'aws:SecureTransport': false,
                    },
                },
            }));
        }
        else if (logs_bucket != false) {
            this.defsAccessLogsBucket = logs_bucket;
        }
        const defs_bucket = new aws_s3_1.Bucket(this, 'VirusDefsBucket', {
            encryption: aws_s3_1.BucketEncryption.S3_MANAGED,
            removalPolicy: aws_cdk_lib_1.RemovalPolicy.DESTROY,
            autoDeleteObjects: true,
            serverAccessLogsBucket: this.defsAccessLogsBucket,
            serverAccessLogsPrefix: logs_bucket === false ? undefined : logs_bucket_prefix,
            blockPublicAccess: {
                blockPublicAcls: true,
                blockPublicPolicy: true,
                ignorePublicAcls: true,
                restrictPublicBuckets: true,
            },
        });
        defs_bucket.addToResourcePolicy(new aws_iam_1.PolicyStatement({
            effect: aws_iam_1.Effect.DENY,
            actions: ['s3:*'],
            resources: [defs_bucket.arnForObjects('*'), defs_bucket.bucketArn],
            principals: [new aws_iam_1.AnyPrincipal()],
            conditions: {
                Bool: {
                    'aws:SecureTransport': false,
                },
            },
        }));
        defs_bucket.addToResourcePolicy(new aws_iam_1.PolicyStatement({
            effect: aws_iam_1.Effect.ALLOW,
            actions: ['s3:GetObject', 's3:ListBucket'],
            resources: [defs_bucket.arnForObjects('*'), defs_bucket.bucketArn],
            principals: [new aws_iam_1.AnyPrincipal()],
            conditions: {
                StringEquals: {
                    'aws:SourceVpce': this._s3Gw.vpcEndpointId, // should be "vpce-058d523018345867d"
                },
            },
        }));
        defs_bucket.addToResourcePolicy(new aws_iam_1.PolicyStatement({
            effect: aws_iam_1.Effect.DENY,
            actions: ['s3:PutBucketPolicy', 's3:DeleteBucketPolicy'],
            resources: [defs_bucket.bucketArn],
            notPrincipals: [new aws_iam_1.AccountRootPrincipal()],
        }));
        this._s3Gw.addToPolicy(new aws_iam_1.PolicyStatement({
            effect: aws_iam_1.Effect.ALLOW,
            actions: ['s3:GetObject', 's3:ListBucket'],
            resources: [defs_bucket.arnForObjects('*'), defs_bucket.bucketArn],
            principals: [new aws_iam_1.AnyPrincipal()],
        }));
        this._scanFunction = new aws_lambda_1.DockerImageFunction(this, 'ServerlessClamscan', {
            code: aws_lambda_1.DockerImageCode.fromImageAsset(path.join(__dirname, '../assets/lambda/code/scan'), {
                buildArgs: {
                    // Only force update the docker layer cache once a day
                    CACHE_DATE: new Date().toDateString(),
                },
                extraHash: Date.now().toString(),
            }),
            onSuccess: this.resultDest,
            onFailure: this.errorDest,
            filesystem: aws_lambda_1.FileSystem.fromEfsAccessPoint(lambda_ap, this._efsMountPath),
            vpc: vpc,
            vpcSubnets: { subnets: vpc.subnets  },
            allowAllOutbound: false,
            timeout: aws_cdk_lib_1.Duration.minutes(15),
            memorySize: 10240,
            reservedConcurrentExecutions: props.reservedConcurrency,
            environment: {
                EFS_MOUNT_PATH: this._efsMountPath,
                EFS_DEF_PATH: this._efsDefsPath,
                DEFS_URL: defs_bucket.virtualHostedUrlForObject(),
                POWERTOOLS_METRICS_NAMESPACE: 'serverless-clamscan',
                POWERTOOLS_SERVICE_NAME: 'virus-scan',
            },
        });
        if (this._scanFunction.role) {
            const cfnScanRole = this._scanFunction.role.node.defaultChild;
            cfnScanRole.addMetadata('cdk_nag', {
                rules_to_suppress: [
                    {
                        id: 'AwsSolutions-IAM4',
                        reason: 'The AWSLambdaBasicExecutionRole does not provide permissions beyond uploading logs to CloudWatch. The AWSLambdaVPCAccessExecutionRole is required for functions with VPC access to manage elastic network interfaces.',
                    },
                ],
            });
            const cfnScanRoleChildren = this._scanFunction.role.node.children;
            for (const child of cfnScanRoleChildren) {
                const resource = child.node.defaultChild;
                if (resource != undefined && resource.cfnResourceType == 'AWS::IAM::Policy') {
                    resource.addMetadata('cdk_nag', {
                        rules_to_suppress: [
                            {
                                id: 'AwsSolutions-IAM5',
                                reason: 'The EFS mount point permissions are controlled through a condition which limit the scope of the * resources.',
                            },
                        ],
                    });
                }
            }
        }
        this._scanFunction.connections.allowToAnyIpv4(aws_ec2_1.Port.tcp(443), 'Allow outbound HTTPS traffic for S3 access.');
        defs_bucket.grantRead(this._scanFunction);
        const download_defs = new aws_lambda_1.DockerImageFunction(this, 'DownloadDefs', {
            code: aws_lambda_1.DockerImageCode.fromImageAsset(path.join(__dirname, '../assets/lambda/code/download_defs'), {
                buildArgs: {
                    // Only force update the docker layer cache once a day
                    CACHE_DATE: new Date().toDateString(),
                },
                extraHash: Date.now().toString(),
            }),
            timeout: aws_cdk_lib_1.Duration.minutes(5),
            memorySize: 1024,
            environment: {
                DEFS_BUCKET: defs_bucket.bucketName,
                POWERTOOLS_SERVICE_NAME: 'freshclam-update',
            },
        });
        const stack = aws_cdk_lib_1.Stack.of(this);
        if (download_defs.role) {
            const download_defs_role = `arn:${stack.partition}:sts::${stack.account}:assumed-role/${download_defs.role.roleName}/${download_defs.functionName}`;
            const download_defs_assumed_principal = new aws_iam_1.ArnPrincipal(download_defs_role);
            defs_bucket.addToResourcePolicy(new aws_iam_1.PolicyStatement({
                effect: aws_iam_1.Effect.DENY,
                actions: ['s3:PutObject*'],
                resources: [defs_bucket.arnForObjects('*')],
                notPrincipals: [download_defs.role, download_defs_assumed_principal],
            }));
            defs_bucket.grantReadWrite(download_defs);
            const cfnDownloadRole = download_defs.role.node.defaultChild;
            cfnDownloadRole.addMetadata('cdk_nag', {
                rules_to_suppress: [
                    {
                        id: 'AwsSolutions-IAM4',
                        reason: 'The AWSLambdaBasicExecutionRole does not provide permissions beyond uploading logs to CloudWatch.',
                    },
                ],
            });
            const cfnDownloadRoleChildren = download_defs.role.node.children;
            for (const child of cfnDownloadRoleChildren) {
                const resource = child.node.defaultChild;
                if (resource != undefined && resource.cfnResourceType == 'AWS::IAM::Policy') {
                    resource.addMetadata('cdk_nag', {
                        rules_to_suppress: [
                            {
                                id: 'AwsSolutions-IAM5',
                                reason: 'The function is allowed to perform operations on all prefixes in the specified bucket.',
                            },
                        ],
                    });
                }
            }
        }
        new aws_events_1.Rule(this, 'VirusDefsUpdateRule', {
            schedule: aws_events_1.Schedule.rate(aws_cdk_lib_1.Duration.hours(12)),
            targets: [new aws_events_targets_1.LambdaFunction(download_defs)],
        });
        const init_defs_cr = new aws_lambda_1.Function(this, 'InitDefs', {
            runtime: aws_lambda_1.Runtime.PYTHON_3_8,
            code: aws_lambda_1.Code.fromAsset(path.join(__dirname, '../assets/lambda/code/initialize_defs_cr')),
            handler: 'lambda.lambda_handler',
            timeout: aws_cdk_lib_1.Duration.minutes(5),
        });
        download_defs.grantInvoke(init_defs_cr);
        if (init_defs_cr.role) {
            const cfnScanRole = init_defs_cr.role.node.defaultChild;
            cfnScanRole.addMetadata('cdk_nag', {
                rules_to_suppress: [
                    {
                        id: 'AwsSolutions-IAM4',
                        reason: 'The AWSLambdaBasicExecutionRole does not provide permissions beyond uploading logs to CloudWatch.',
                    },
                ],
            });
        }
        new aws_cdk_lib_1.CustomResource(this, 'InitDefsCr', {
            serviceToken: init_defs_cr.functionArn,
            properties: {
                FnName: download_defs.functionName,
            },
        });
        if (props.buckets) {
            props.buckets.forEach((bucket) => {
                this.addSourceBucket(bucket);
            });
        }
    }
    /**
     * Sets the specified S3 Bucket as a s3:ObjectCreate* for the ClamAV function.
     *
     * Grants the ClamAV function permissions to get and tag objects.
     * Adds a bucket policy to disallow GetObject operations on files that are tagged 'IN PROGRESS', 'INFECTED', or 'ERROR'.
     *
     * @param bucket The bucket to add the scanning bucket policy and s3:ObjectCreate* trigger to.
     * @stability stable
     */
    addSourceBucket(bucket) {
        this._scanFunction.addEventSource(new aws_lambda_event_sources_1.S3EventSource(bucket, { events: [aws_s3_1.EventType.OBJECT_CREATED] }));
        bucket.grantRead(this._scanFunction);
        this._scanFunction.addToRolePolicy(new aws_iam_1.PolicyStatement({
            effect: aws_iam_1.Effect.ALLOW,
            actions: ['s3:PutObjectTagging', 's3:PutObjectVersionTagging'],
            resources: [bucket.arnForObjects('*')],
        }));
        if (this._scanFunction.role) {
            const stack = aws_cdk_lib_1.Stack.of(this);
            const scan_assumed_role = `arn:${stack.partition}:sts::${stack.account}:assumed-role/${this._scanFunction.role.roleName}/${this._scanFunction.functionName}`;
            const scan_assumed_principal = new aws_iam_1.ArnPrincipal(scan_assumed_role);
            this._s3Gw.addToPolicy(new aws_iam_1.PolicyStatement({
                effect: aws_iam_1.Effect.ALLOW,
                actions: ['s3:GetObject*', 's3:GetBucket*', 's3:List*'],
                resources: [bucket.bucketArn, bucket.arnForObjects('*')],
                principals: [this._scanFunction.role, scan_assumed_principal],
            }));
            this._s3Gw.addToPolicy(new aws_iam_1.PolicyStatement({
                effect: aws_iam_1.Effect.ALLOW,
                actions: ['s3:PutObjectTagging', 's3:PutObjectVersionTagging'],
                resources: [bucket.arnForObjects('*')],
                principals: [this._scanFunction.role, scan_assumed_principal],
            }));
            // Need the assumed role for the not Principal Action with Lambda
            bucket.addToResourcePolicy(new aws_iam_1.PolicyStatement({
                effect: aws_iam_1.Effect.DENY,
                actions: ['s3:GetObject'],
                resources: [bucket.arnForObjects('*')],
                notPrincipals: [this._scanFunction.role, scan_assumed_principal],
                conditions: {
                    StringEquals: {
                        's3:ExistingObjectTag/scan-status': [
                            'IN PROGRESS',
                            'INFECTED',
                            'ERROR',
                        ],
                    },
                },
            }));
        }
    }
}
exports.ServerlessClamscan = ServerlessClamscan;
_a = JSII_RTTI_SYMBOL_1;
ServerlessClamscan[_a] = { fqn: "cdk-serverless-clamscan.ServerlessClamscan", version: "2.1.8" };
//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoiaW5kZXguanMiLCJzb3VyY2VSb290IjoiIiwic291cmNlcyI6WyIuLi9zcmMvaW5kZXgudHMiXSwibmFtZXMiOltdLCJtYXBwaW5ncyI6Ijs7Ozs7QUFBQSxxRUFBcUU7QUFDckUsc0NBQXNDO0FBRXRDLDZCQUE2QjtBQUM3Qiw2Q0FNcUI7QUFDckIsaURBTzZCO0FBQzdCLGlEQUFtRjtBQUNuRix1REFBa0U7QUFDbEUsdUVBQWdFO0FBQ2hFLGlEQU82QjtBQUM3Qix1REFRZ0M7QUFDaEMsaUZBRzZDO0FBQzdDLG1GQUFxRTtBQUNyRSwrQ0FBeUU7QUFDekUsaURBQXVFO0FBQ3ZFLDJDQUF1Qzs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7QUEyQnZDLE1BQWEsa0JBQW1CLFNBQVEsc0JBQVM7Ozs7Ozs7OztJQWdDL0MsWUFBWSxLQUFnQixFQUFFLEVBQVUsRUFBRSxLQUE4Qjs7UUFDdEUsS0FBSyxDQUFDLEtBQUssRUFBRSxFQUFFLENBQUMsQ0FBQztRQU5YLGlCQUFZLEdBQUcsU0FBUyxDQUFDO1FBQ3pCLGtCQUFhLEdBQUcsT0FBTyxJQUFJLENBQUMsWUFBWSxFQUFFLENBQUM7UUFDM0MsaUJBQVksR0FBRyxpQkFBaUIsQ0FBQztRQU12QyxJQUFJLENBQUMsS0FBSyxDQUFDLFFBQVEsRUFBRTtZQUNuQixJQUFJLENBQUMsU0FBUyxHQUFHLElBQUkscUJBQVEsQ0FBQyxJQUFJLEVBQUUsZUFBZSxDQUFDLENBQUM7WUFDckQsSUFBSSxDQUFDLFVBQVUsR0FBRyxJQUFJLGdEQUFzQixDQUFDLElBQUksQ0FBQyxTQUFTLENBQUMsQ0FBQztZQUM3RCxJQUFJLENBQUMsWUFBWSxHQUFHLElBQUksaUJBQUksQ0FBQyxJQUFJLEVBQUUsY0FBYyxFQUFFO2dCQUNqRCxRQUFRLEVBQUUsSUFBSSxDQUFDLFNBQVM7Z0JBQ3hCLFdBQVcsRUFBRSwwQ0FBMEM7Z0JBQ3ZELFlBQVksRUFBRTtvQkFDWixNQUFNLEVBQUU7d0JBQ04sZUFBZSxFQUFFOzRCQUNmLE1B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SCxDQUFDO1lBRUYsaUVBQWlFO1lBQ2pFLE1BQU0sQ0FBQyxtQkFBbUIsQ0FDeEIsSUFBSSx5QkFBZSxDQUFDO2dCQUNsQixNQUFNLEVBQUUsZ0JBQU0sQ0FBQyxJQUFJO2dCQUNuQixPQUFPLEVBQUUsQ0FBQyxjQUFjLENBQUM7Z0JBQ3pCLFNBQVMsRUFBRSxDQUFDLE1BQU0sQ0FBQyxhQUFhLENBQUMsR0FBRyxDQUFDLENBQUM7Z0JBQ3RDLGFBQWEsRUFBRSxDQUFDLElBQUksQ0FBQyxhQUFhLENBQUMsSUFBSSxFQUFFLHNCQUFzQixDQUFDO2dCQUNoRSxVQUFVLEVBQUU7b0JBQ1YsWUFBWSxFQUFFO3dCQUNaLGtDQUFrQyxFQUFFOzRCQUNsQyxhQUFhOzRCQUNiLFVBQVU7NEJBQ1YsT0FBTzt5QkFDUjtxQkFDRjtpQkFDRjthQUNGLENBQUMsQ0FDSCxDQUFDO1NBQ0g7SUFDSCxDQUFDOztBQTNiSCxnREE0YkMiLCJzb3VyY2VzQ29udGVudCI6WyIvLyBDb3B5cmlnaHQgQW1hem9uLmNvbSwgSW5jLiBvciBpdHMgYWZmaWxpYXRlcy4gQWxsIFJpZ2h0cyBSZXNlcnZlZC5cbi8vIFNQRFgtTGljZW5zZS1JZGVudGlmaWVyOiBBcGFjaGUtMi4wXG5cbmltcG9ydCAqIGFzIHBhdGggZnJvbSAncGF0aCc7XG5pbXBvcnQge1xuICBEdXJhdGlvbixcbiAgQ3VzdG9tUmVzb3VyY2UsXG4gIFJlbW92YWxQb2xpY3ksXG4gIFN0YWNrLFxuICBDZm5SZXNvdXJjZSxcbn0gZnJvbSAnYXdzLWNkay1saWInO1xuaW1wb3J0IHtcbiAgVnBjLFxuICBTdWJuZXRUeXBlLFxuICBHYXRld2F5VnBjRW5kcG9pbnQsXG4gIEdhdGV3YXlWcGNFbmRwb2ludEF3c1NlcnZpY2UsXG4gIFBvcnQsXG4gIFNlY3VyaXR5R3JvdXAsXG59IGZyb20gJ2F3cy1jZGstbGliL2F3cy1lYzInO1xuaW1wb3J0IHsgRmlsZVN5c3RlbSwgTGlmZWN5Y2xlUG9saWN5LCBQZXJmb3JtYW5jZU1vZGUgfSBmcm9tICdhd3MtY2RrLWxpYi9hd3MtZWZzJztcbmltcG9ydCB7IEV2ZW50QnVzLCBSdWxlLCBTY2hlZHVsZSB9IGZyb20gJ2F3cy1jZGstbGliL2F3cy1ldmVudHMnO1xuaW1wb3J0IHsgTGFtYmRhRnVuY3Rpb24gfSBmcm9tICdhd3MtY2RrLWxpYi9hd3MtZXZlbnRzLXRhcmdldHMnO1xuaW1wb3J0IHtcbiAgRWZmZWN0LFxuICBQb2xpY3lTdGF0ZW1lbnQsXG4gIEFyblByaW5jaXBhbCxcbiAgQW55UHJpbmNpcGFsLFxuICBBY2NvdW50Um9vdFByaW5jaXBhbCxcbiAgQ2ZuUm9sZSxcbn0gZnJvbSAnYXdzLWNkay1saWIvYXdzLWlhbSc7XG5pbXBvcnQge1xuICBEb2NrZXJJbWFnZUNvZGUsXG4gIERvY2tlckltYWdlRnVuY3Rpb24sXG4gIEZ1bmN0aW9uLFxuICBJRGVzdGluYXRpb24sXG4gIEZpbGVTeXN0ZW0gYXMgTGFtYmRhRmlsZVN5c3RlbSxcbiAgUnVudGltZSxcbiAgQ29kZSxcbn0gZnJvbSAnYXdzLWNkay1saWIvYXdzLWxhbWJkYSc7XG5pbXBvcnQge1xuICBFdmVudEJyaWRnZURlc3RpbmF0aW9uLFxuICBTcXNEZXN0aW5hdGlvbixcbn0gZnJvbSAnYXdzLWNkay1saWIvYXdzLWxhbWJkYS1kZXN0aW5hdGlvbnMnO1xuaW1wb3J0IHsgUzNFdmVudFNvdXJjZSB9IGZyb20gJ2F3cy1jZGstbGliL2F3cy1sYW1iZGEtZXZlbnQtc291cmNlcyc7XG5pbXBvcnQgeyBCdWNrZXQsIEJ1Y2tldEVuY3J5cHRpb24sIEV2ZW50VHlwZSB9IGZyb20gJ2F3cy1jZGstbGliL2F3cy1zMyc7XG5pbXBvcnQgeyBDZm5RdWV1ZSwgUXVldWUsIFF1ZXVlRW5jcnlwdGlvbiB9IGZyb20gJ2F3cy1jZGstbGliL2F3cy1zcXMnO1xuaW1wb3J0IHsgQ29uc3RydWN0IH0gZnJvbSAnY29uc3RydWN0cyc7XG5cbiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgXG5leHBvcnQgaW50ZXJmYWNlIFNlcnZlcmxlc3NDbGFtc2NhbkxvZ2dpbmdQcm9wcyB7XG4gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgXG4gIHJlYWRvbmx5IGxvZ3NCdWNrZXQ/OiBib29sZWFuIHwgQnVja2V0O1xuICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICBcbiAgcmVhZG9ubHkgbG9nc1ByZWZpeD86IHN0cmluZztcbn1cblxuICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIFxuZXhwb3J0IGludGVyZmFjZSBTZXJ2ZXJsZXNzQ2xhbXNjYW5Qcm9wcyB7XG4gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgXG4gIHJlYWRvbmx5IGJ1Y2tldHM/OiBCdWNrZXRbXTtcbiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIFxuICByZWFkb25seSByZXNlcnZlZENvbmN1cnJlbmN5PzogbnVtYmVyO1xuICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgXG4gIHJlYWRvbmx5IG9uUmVzdWx0PzogSURlc3RpbmF0aW9uO1xuICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIFxuICByZWFkb25seSBvbkVycm9yPzogSURlc3RpbmF0aW9uO1xuICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgXG4gIHJlYWRvbmx5IGVmc0VuY3J5cHRpb24/OiBib29sZWFuO1xuICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgXG4gIHJlYWRvbmx5IGRlZnNCdWNrZXRBY2Nlc3NMb2dzQ29uZmlnPzogU2VydmVybGVzc0NsYW1zY2FuTG9nZ2luZ1Byb3BzO1xufVxuXG4gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICBcbmV4cG9ydCBjbGFzcyBTZXJ2ZXJsZXNzQ2xhbXNjYW4gZXh0ZW5kcyBDb25zdHJ1Y3Qge1xuICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICBcbiAgcHVibGljIHJlYWRvbmx5IGVycm9yRGVzdDogSURlc3RpbmF0aW9uO1xuXG4gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICBcbiAgcHVibGljIHJlYWRvbmx5IHJlc3VsdERlc3Q6IElEZXN0aW5hdGlvbjtcblxuICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIFxuICBwdWJsaWMgcmVhZG9ubHkgZXJyb3JRdWV1ZT86IFF1ZXVlO1xuXG4gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgXG4gIHB1YmxpYyByZWFkb25seSBlcnJvckRlYWRMZXR0ZXJRdWV1ZT86IFF1ZXVlO1xuXG4gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIFxuICBwdWJsaWMgcmVhZG9ubHkgcmVzdWx0QnVzPzogRXZlbnRCdXM7XG5cbiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgXG4gIHB1YmxpYyByZWFkb25seSBjbGVhblJ1bGU/OiBSdWxlO1xuXG4gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIFxuICBwdWJsaWMgcmVhZG9ubHkgaW5mZWN0ZWRSdWxlPzogUnVsZTtcblxuICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICBcbiAgcHVibGljIHJlYWRvbmx5IGRlZnNBY2Nlc3NMb2dzQnVja2V0PzogQnVja2V0O1xuXG4gIHByaXZhdGUgX3NjYW5GdW5jdGlvbjogRG9ja2VySW1hZ2VGdW5jdGlvbjtcbiAgcHJpdmF0ZSBfczNHdzogR2F0ZXdheVZwY0VuZHBvaW50O1xuICBwcml2YXRlIF9lZnNSb290UGF0aCA9ICcvbGFtYmRhJztcbiAgcHJpdmF0ZSBfZWZzTW91bnRQYXRoID0gYC9tbnQke3RoaXMuX2Vmc1Jvb3RQYXRofWA7XG4gIHByaXZhdGUgX2Vmc0RlZnNQYXRoID0gJ3ZpcnVzX2RhdGFiYXNlLyc7XG5cbiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIFxuICBjb25zdHJ1Y3RvcihzY29wZTogQ29uc3RydWN0LCBpZDogc3RyaW5nLCBwcm9wczogU2VydmVybGVzc0NsYW1zY2FuUHJvcHMpIHtcbiAgICBzdXBlcihzY29wZSwgaWQpO1xuXG4gICAgaWYgKCFwcm9wcy5vblJlc3VsdCkge1xuICAgICAgdGhpcy5yZXN1bHRCdXMgPSBuZXcgRXZlbnRCdXModGhpcywgJ1NjYW5SZXN1bHRCdXMnKTtcbiAgICAgIHRoaXMucmVzdWx0RGVzdCA9IG5ldyBFdmVudEJyaWRnZURlc3RpbmF0aW9uKHRoaXMucmVzdWx0QnVzKTtcbiAgICAgIHRoaXMuaW5mZWN0ZWRSdWxlID0gbmV3IFJ1bGUodGhpcywgJ0luZmVjdGVkUnVsZScsIHtcbiAgICAgICAgZXZlbnRCdXM6IHRoaXMucmVzdWx0QnVzLFxuICAgICAgICBkZXNjcmlwdGlvbjogJ0V2ZW50IGZvciB3aGVuIGEgZmlsZSBpcyBtYXJrZWQgSU5GRUNURUQnLFxuICAgICAgICBldmVudFBhdHRlcm46IHtcbiAgICAgICAgICBkZXRhaWw6IHtcbiAgICAgICAgICAgIHJlc3BvbnNlUGF5bG9hZDoge1xuICAgICAgICAgICAgICBzb3VyY2U6IFsnc2VydmVybGVzcy1jbGFtc2NhbiddLFxuICAgICAgICAgICAgICBzdGF0dXM6IFsnSU5GRUNURUQnXSxcbiAgICAgICAgICAgIH0sXG4gICAgICAgICAgfSxcbiAgICAgICAgfSxcbiAgICAgIH0pO1xuICAgICAgdGhpcy5jbGVhblJ1bGUgPSBuZXcgUnVsZSh0aGlzLCAnQ2xlYW5SdWxlJywge1xuICAgICAgICBldmVudEJ1czogdGhpcy5yZXN1bHRCdXMsXG4gICAgICAgIGRlc2NyaXB0aW9uOiAnRXZlbnQgZm9yIHdoZW4gYSBmaWxlIGlzIG1hcmtlZCBDTEVBTicsXG4gICAgICAgIGV2ZW50UGF0dGVybjoge1xuICAgICAgICAgIGRldGFpbDoge1xuICAgICAgICAgICAgcmVzcG9uc2VQYXlsb2FkOiB7XG4gICAgICAgICAgICAgIHNvdXJjZTogWydzZXJ2ZXJsZXNzLWNsYW1zY2FuJ10sXG4gICAgICAgICAgICAgIHN0YXR1czogWydDTEVBTiddLFxuICAgICAgICAgICAgfSxcbiAgICAgICAgICB9LFxuICAgICAgICB9LFxuICAgICAgfSk7XG4gICAgfSBlbHNlIHtcbiAgICAgIHRoaXMucmVzdWx0RGVzdCA9IHByb3BzLm9uUmVzdWx0O1xuICAgIH1cblxuICAgIGlmICghcHJvcHMub25FcnJvcikge1xuICAgICAgdGhpcy5lcnJvckRlYWRMZXR0ZXJRdWV1ZSA9IG5ldyBRdWV1ZSh0aGlzLCAnU2NhbkVycm9yRGVhZExldHRlclF1ZXVlJywge1xuICAgICAgICBlbmNyeXB0aW9uOiBRdWV1ZUVuY3J5cHRpb24uS01TX01BTkFHRUQsXG4gICAgICB9KTtcbiAgICAgIHRoaXMuZXJyb3JRdWV1ZSA9IG5ldyBRdWV1ZSh0aGlzLCAnU2NhbkVycm9yUXVldWUnLCB7XG4gICAgICAgIGVuY3J5cHRpb246IFF1ZXVlRW5jcnlwdGlvbi5LTVNfTUFOQUdFRCxcbiAgICAgICAgZGVhZExldHRlclF1ZXVlOiB7XG4gICAgICAgICAgbWF4UmVjZWl2ZUNvdW50OiAzLFxuICAgICAgICAgIHF1ZXVlOiB0aGlzLmVycm9yRGVhZExldHRlclF1ZXVlLFxuICAgICAgICB9LFxuICAgICAgfSk7XG4gICAgICB0aGlzLmVycm9yRGVzdCA9IG5ldyBTcXNEZXN0aW5hdGlvbih0aGlzLmVycm9yUXVldWUpO1xuICAgICAgY29uc3QgY2ZuRGxxID0gdGhpcy5lcnJvckRlYWRMZXR0ZXJRdWV1ZS5ub2RlLmRlZmF1bHRDaGlsZCBhcyBDZm5RdWV1ZTtcbiAgICAgIGNmbkRscS5hZGRNZXRhZGF0YSgnY2RrX25hZycsIHtcbiAgICAgICAgcnVsZXNfdG9fc3VwcHJlc3M6IFtcbiAgICAgICAgICB7IGlkOiAnQXdzU29sdXRpb25zLVNRUzMnLCByZWFzb246ICdUaGlzIHF1ZXVlIGlzIGEgRExRLicgfSxcbiAgICAgICAgXSxcbiAgICAgIH0pO1xuICAgIH0gZWxzZSB7XG4gICAgICB0aGlzLmVycm9yRGVzdCA9IHByb3BzLm9uRXJyb3I7XG4gICAgfVxuXG4gICAgY29uc3QgdnBjID0gbmV3IFZwYyh0aGlzLCAnU2NhblZQQycsIHtcbiAgICAgIHN1Ym5ldENvbmZpZ3VyYXRpb246IFtcbiAgICAgICAge1xuICAgICAgICAgIHN1Ym5ldFR5cGU6IFN1Ym5ldFR5cGUuUFJJVkFURV9JU09MQVRFRCxcbiAgICAgICAgICBuYW1lOiAnSXNvbGF0ZWQnLFxuICAgICAgICB9LFxuICAgICAgXSxcbiAgICB9KTtcblxuICAgIHZwYy5hZGRGbG93TG9nKCdGbG93TG9ncycpO1xuXG4gICAgdGhpcy5fczNHdyA9IHZwYy5hZGRHYXRld2F5RW5kcG9pbnQoJ1MzRW5kcG9pbnQnLCB7XG4gICAgICBzZXJ2aWNlOiBHYXRld2F5VnBjRW5kcG9pbnRBd3NTZXJ2aWNlLlMzLFxuICAgIH0pO1xuXG4gICAgY29uc3QgZmlsZVN5c3RlbSA9IG5ldyBGaWxlU3lzdGVtKHRoaXMsICdTY2FuRmlsZVN5c3RlbScsIHtcbiAgICAgIHZwYzogdnBjLFxuICAgICAgZW5jcnlwdGVkOiBwcm9wcy5lZnNFbmNyeXB0aW9uID09PSBmYWxzZSA/IGZhbHNlIDogdHJ1ZSxcbiAgICAgIGxpZmVjeWNsZVBvbGljeTogTGlmZWN5Y2xlUG9saWN5LkFGVEVSXzdfREFZUyxcbiAgICAgIHBlcmZvcm1hbmNlTW9kZTogUGVyZm9ybWFuY2VNb2RlLkdFTkVSQUxfUFVSUE9TRSxcbiAgICAgIHJlbW92YWxQb2xpY3k6IFJlbW92YWxQb2xpY3kuREVTVFJPWSxcbiAgICAgIHNlY3VyaXR5R3JvdXA6IG5ldyBTZWN1cml0eUdyb3VwKHRoaXMsICdTY2FuRmlsZVN5c3RlbVNlY3VyaXR5R3JvdXAnLCB7XG4gICAgICAgIHZwYzogdnBjLFxuICAgICAgICBhbGxvd0FsbE91dGJvdW5kOiBmYWxzZSxcbiAgICAgIH0pLFxuICAgIH0pO1xuXG4gICAgY29uc3QgbGFtYmRhX2FwID0gZmlsZVN5c3RlbS5hZGRBY2Nlc3NQb2ludCgnU2NhbkxhbWJkYUFQJywge1xuICAgICAgY3JlYXRlQWNsOiB7XG4gICAgICAgIG93bmVyR2lkOiAnMTAwMCcsXG4gICAgICAgIG93bmVyVWlkOiAnMTAwMCcsXG4gICAgICAgIHBlcm1pc3Npb25zOiAnNzU1JyxcbiAgICAgIH0sXG4gICAgICBwb3NpeFVzZXI6IHtcbiAgICAgICAgZ2lkOiAnMTAwMCcsXG4gICAgICAgIHVpZDogJzEwMDAnLFxuICAgICAgfSxcbiAgICAgIHBhdGg6IHRoaXMuX2Vmc1Jvb3RQYXRoLFxuICAgIH0pO1xuXG4gICAgY29uc3QgbG9nc19idWNrZXQgPSBwcm9wcy5kZWZzQnVja2V0QWNjZXNzTG9nc0NvbmZpZz8ubG9nc0J1Y2tldDtcbiAgICBjb25zdCBsb2dzX2J1Y2tldF9wcmVmaXggPSBwcm9wcy5kZWZzQnVja2V0QWNjZXNzTG9nc0NvbmZpZz8ubG9nc1ByZWZpeDtcbiAgICBpZiAobG9nc19idWNrZXQgPT09IHRydWUgfHwgbG9nc19idWNrZXQgPT09IHVuZGVmaW5lZCkge1xuICAgICAgdGhpcy5kZWZzQWNjZXNzTG9nc0J1Y2tldCA9IG5ldyBCdWNrZXQoXG4gICAgICAgIHRoaXMsXG4gICAgICAgICdWaXJ1c0RlZnNBY2Nlc3NMb2dzQnVja2V0JyxcbiAgICAgICAge1xuICAgICAgICAgIGVuY3J5cHRpb246IEJ1Y2tldEVuY3J5cHRpb24uUzNfTUFOQUdFRCxcbiAgICAgICAgICByZW1vdmFsUG9saWN5OiBSZW1vdmFsUG9saWN5LlJFVEFJTixcbiAgICAgICAgICBzZXJ2ZXJBY2Nlc3NMb2dzUHJlZml4OiAnYWNjZXNzLWxvZ3MtYnVja2V0LWxvZ3MnLFxuICAgICAgICAgIGJsb2NrUHVibGljQWNjZXNzOiB7XG4gICAgICAgICAgICBibG9ja1B1YmxpY0FjbHM6IHRydWUsXG4gICAgICAgICAgICBibG9ja1B1YmxpY1BvbGljeTogdHJ1ZSxcbiAgICAgICAgICAgIGlnbm9yZVB1YmxpY0FjbHM6IHRydWUsXG4gICAgICAgICAgICByZXN0cmljdFB1YmxpY0J1Y2tldHM6IHRydWUsXG4gICAgICAgICAgfSxcbiAgICAgICAgfSxcbiAgICAgICk7XG4gICAgICB0aGlzLmRlZnNBY2Nlc3NMb2dzQnVja2V0LmFkZFRvUmVzb3VyY2VQb2xpY3koXG4gICAgICAgIG5ldyBQb2xpY3lTdGF0ZW1lbnQoe1xuICAgICAgICAgIGVmZmVjdDogRWZmZWN0LkRFTlksXG4gICAgICAgICAgYWN0aW9uczogWydzMzoqJ10sXG4gICAgICAgICAgcmVzb3VyY2VzOiBbXG4gICAgICAgICAgICB0aGlzLmRlZnNBY2Nlc3NMb2dzQnVja2V0LmFybkZvck9iamVjdHMoJyonKSxcbiAgICAgICAgICAgIHRoaXMuZGVmc0FjY2Vzc0xvZ3NCdWNrZXQuYnVja2V0QXJuLFxuICAgICAgICAgIF0sXG4gICAgICAgICAgcHJpbmNpcGFsczogW25ldyBBbnlQcmluY2lwYWwoKV0sXG4gICAgICAgICAgY29uZGl0aW9uczoge1xuICAgICAgICAgICAgQm9vbDoge1xuICAgICAgICAgICAgICAnYXdzOlNlY3VyZVRyYW5zcG9ydCc6IGZhbHNlLFxuICAgICAgICAgICAgfSxcbiAgICAgICAgICB9LFxuICAgICAgICB9KSxcbiAgICAgICk7XG4gICAgfSBlbHNlIGlmIChsb2dzX2J1Y2tldCAhPSBmYWxzZSkge1xuICAgICAgdGhpcy5kZWZzQWNjZXNzTG9nc0J1Y2tldCA9IGxvZ3NfYnVja2V0O1xuICAgIH1cblxuICAgIGNvbnN0IGRlZnNfYnVja2V0ID0gbmV3IEJ1Y2tldCh0aGlzLCAnVmlydXNEZWZzQnVja2V0Jywge1xuICAgICAgZW5jcnlwdGlvbjogQnVja2V0RW5jcnlwdGlvbi5TM19NQU5BR0VELFxuICAgICAgcmVtb3ZhbFBvbGljeTogUmVtb3ZhbFBvbGljeS5ERVNUUk9ZLFxuICAgICAgYXV0b0RlbGV0ZU9iamVjdHM6IHRydWUsXG4gICAgICBzZXJ2ZXJBY2Nlc3NMb2dzQnVja2V0OiB0aGlzLmRlZnNBY2Nlc3NMb2dzQnVja2V0LFxuICAgICAgc2VydmVyQWNjZXNzTG9nc1ByZWZpeDpcbiAgICAgICAgbG9nc19idWNrZXQgPT09IGZhbHNlID8gdW5kZWZpbmVkIDogbG9nc19idWNrZXRfcHJlZml4LFxuICAgICAgYmxvY2tQdWJsaWNBY2Nlc3M6IHtcbiAgICAgICAgYmxvY2tQdWJsaWNBY2xzOiB0cnVlLFxuICAgICAgICBibG9ja1B1YmxpY1BvbGljeTogdHJ1ZSxcbiAgICAgICAgaWdub3JlUHVibGljQWNsczogdHJ1ZSxcbiAgICAgICAgcmVzdHJpY3RQdWJsaWNCdWNrZXRzOiB0cnVlLFxuICAgICAgfSxcbiAgICB9KTtcblxuICAgIGRlZnNfYnVja2V0LmFkZFRvUmVzb3VyY2VQb2xpY3koXG4gICAgICBuZXcgUG9saWN5U3RhdGVtZW50KHtcbiAgICAgICAgZWZmZWN0OiBFZmZlY3QuREVOWSxcbiAgICAgICAgYWN0aW9uczogWydzMzoqJ10sXG4gICAgICAgIHJlc291cmNlczogW2RlZnNfYnVja2V0LmFybkZvck9iamVjdHMoJyonKSwgZGVmc19idWNrZXQuYnVja2V0QXJuXSxcbiAgICAgICAgcHJpbmNpcGFsczogW25ldyBBbnlQcmluY2lwYWwoKV0sXG4gICAgICAgIGNvbmRpdGlvbnM6IHtcbiAgICAgICAgICBCb29sOiB7XG4gICAgICAgICAgICAnYXdzOlNlY3VyZVRyYW5zcG9ydCc6IGZhbHNlLFxuICAgICAgICAgIH0sXG4gICAgICAgIH0sXG4gICAgICB9KSxcbiAgICApO1xuICAgIGRlZnNfYnVja2V0LmFkZFRvUmVzb3VyY2VQb2xpY3koXG4gICAgICBuZXcgUG9saWN5U3RhdGVtZW50KHtcbiAgICAgICAgZWZmZWN0OiBFZmZlY3QuQUxMT1csXG4gICAgICAgIGFjdGlvbnM6IFsnczM6R2V0T2JqZWN0JywgJ3MzOkxpc3RCdWNrZXQnXSxcbiAgICAgICAgcmVzb3VyY2VzOiBbZGVmc19idWNrZXQuYXJuRm9yT2JqZWN0cygnKicpLCBkZWZzX2J1Y2tldC5idWNrZXRBcm5dLFxuICAgICAgICBwcmluY2lwYWxzOiBbbmV3IEFueVByaW5jaXBhbCgpXSxcbiAgICAgICAgY29uZGl0aW9uczoge1xuICAgICAgICAgIFN0cmluZ0VxdWFsczoge1xuICAgICAgICAgICAgJ2F3czpTb3VyY2VWcGNlJzogdGhpcy5fczNHdy52cGNFbmRwb2ludElkLFxuICAgICAgICAgIH0sXG4gICAgICAgIH0sXG4gICAgICB9KSxcbiAgICApO1xuICAgIGRlZnNfYnVja2V0LmFkZFRvUmVzb3VyY2VQb2xpY3koXG4gICAgICBuZXcgUG9saWN5U3RhdGVtZW50KHtcbiAgICAgICAgZWZmZWN0OiBFZmZlY3QuREVOWSxcbiAgICAgICAgYWN0aW9uczogWydzMzpQdXRCdWNrZXRQb2xpY3knLCAnczM6RGVsZXRlQnVja2V0UG9saWN5J10sXG4gICAgICAgIHJlc291cmNlczogW2RlZnNfYnVja2V0LmJ1Y2tldEFybl0sXG4gICAgICAgIG5vdFByaW5jaXBhbHM6IFtuZXcgQWNjb3VudFJvb3RQcmluY2lwYWwoKV0sXG4gICAgICB9KSxcbiAgICApO1xuICAgIHRoaXMuX3MzR3cuYWRkVG9Qb2xpY3koXG4gICAgICBuZXcgUG9saWN5U3RhdGVtZW50KHtcbiAgICAgICAgZWZmZWN0OiBFZmZlY3QuQUxMT1csXG4gICAgICAgIGFjdGlvbnM6IFsnczM6R2V0T2JqZWN0JywgJ3MzOkxpc3RCdWNrZXQnXSxcbiAgICAgICAgcmVzb3VyY2VzOiBbZGVmc19idWNrZXQuYXJuRm9yT2JqZWN0cygnKicpLCBkZWZzX2J1Y2tldC5idWNrZXRBcm5dLFxuICAgICAgICBwcmluY2lwYWxzOiBbbmV3IEFueVByaW5jaXBhbCgpXSxcbiAgICAgIH0pLFxuICAgICk7XG5cbiAgICB0aGlzLl9zY2FuRnVuY3Rpb24gPSBuZXcgRG9ja2VySW1hZ2VGdW5jdGlvbih0aGlzLCAnU2VydmVybGVzc0NsYW1zY2FuJywge1xuICAgICAgY29kZTogRG9ja2VySW1hZ2VDb2RlLmZyb21JbWFnZUFzc2V0KFxuICAgICAgICBwYXRoLmpvaW4oX19kaXJuYW1lLCAnLi4vYXNzZXRzL2xhbWJkYS9jb2RlL3NjYW4nKSxcbiAgICAgICAge1xuICAgICAgICAgIGJ1aWxkQXJnczoge1xuICAgICAgICAgICAgLy8gT25seSBmb3JjZSB1cGRhdGUgdGhlIGRvY2tlciBsYXllciBjYWNoZSBvbmNlIGEgZGF5XG4gICAgICAgICAgICBDQUNIRV9EQVRFOiBuZXcgRGF0ZSgpLnRvRGF0ZVN0cmluZygpLFxuICAgICAgICAgIH0sXG4gICAgICAgICAgZXh0cmFIYXNoOiBEYXRlLm5vdygpLnRvU3RyaW5nKCksXG4gICAgICAgIH0sXG4gICAgICApLFxuICAgICAgb25TdWNjZXNzOiB0aGlzLnJlc3VsdERlc3QsXG4gICAgICBvbkZhaWx1cmU6IHRoaXMuZXJyb3JEZXN0LFxuICAgICAgZmlsZXN5c3RlbTogTGFtYmRhRmlsZVN5c3RlbS5mcm9tRWZzQWNjZXNzUG9pbnQoXG4gICAgICAgIGxhbWJkYV9hcCxcbiAgICAgICAgdGhpcy5fZWZzTW91bnRQYXRoLFxuICAgICAgKSxcbiAgICAgIHZwYzogdnBjLFxuICAgICAgdnBjU3VibmV0czogeyBzdWJuZXRzOiB2cGMuaXNvbGF0ZWRTdWJuZXRzIH0sXG4gICAgICBhbGxvd0FsbE91dGJvdW5kOiBmYWxzZSxcbiAgICAgIHRpbWVvdXQ6IER1cmF0aW9uLm1pbnV0ZXMoMTUpLFxuICAgICAgbWVtb3J5U2l6ZTogMTAyNDAsXG4gICAgICByZXNlcnZlZENvbmN1cnJlbnRFeGVjdXRpb25zOiBwcm9wcy5yZXNlcnZlZENvbmN1cnJlbmN5LFxuICAgICAgZW52aXJvbm1lbnQ6IHtcbiAgICAgICAgRUZTX01PVU5UX1BBVEg6IHRoaXMuX2Vmc01vdW50UGF0aCxcbiAgICAgICAgRUZTX0RFRl9QQVRIOiB0aGlzLl9lZnNEZWZzUGF0aCxcbiAgICAgICAgREVGU19VUkw6IGRlZnNfYnVja2V0LnZpcnR1YWxIb3N0ZWRVcmxGb3JPYmplY3QoKSxcbiAgICAgICAgUE9XRVJUT09MU19NRVRSSUNTX05BTUVTUEFDRTogJ3NlcnZlcmxlc3MtY2xhbXNjYW4nLFxuICAgICAgICBQT1dFUlRPT0xTX1NFUlZJQ0VfTkFNRTogJ3ZpcnVzLXNjYW4nLFxuICAgICAgfSxcbiAgICB9KTtcbiAgICBpZiAodGhpcy5fc2NhbkZ1bmN0aW9uLnJvbGUpIHtcbiAgICAgIGNvbnN0IGNmblNjYW5Sb2xlID0gdGhpcy5fc2NhbkZ1bmN0aW9uLnJvbGUubm9kZS5kZWZhdWx0Q2hpbGQgYXMgQ2ZuUm9sZTtcbiAgICAgIGNmblNjYW5Sb2xlLmFkZE1ldGFkYXRhKCdjZGtfbmFnJywge1xuICAgICAgICBydWxlc190b19zdXBwcmVzczogW1xuICAgICAgICAgIHtcbiAgICAgICAgICAgIGlkOiAnQXdzU29sdXRpb25zLUlBTTQnLFxuICAgICAgICAgICAgcmVhc29uOlxuICAgICAgICAgICAgICAnVGhlIEFXU0xhbWJkYUJhc2ljRXhlY3V0aW9uUm9sZSBkb2VzIG5vdCBwcm92aWRlIHBlcm1pc3Npb25zIGJleW9uZCB1cGxvYWRpbmcgbG9ncyB0byBDbG91ZFdhdGNoLiBUaGUgQVdTTGFtYmRhVlBDQWNjZXNzRXhlY3V0aW9uUm9sZSBpcyByZXF1aXJlZCBmb3IgZnVuY3Rpb25zIHdpdGggVlBDIGFjY2VzcyB0byBtYW5hZ2UgZWxhc3RpYyBuZXR3b3JrIGludGVyZmFjZXMuJyxcbiAgICAgICAgICB9LFxuICAgICAgICBdLFxuICAgICAgfSk7XG4gICAgICBjb25zdCBjZm5TY2FuUm9sZUNoaWxkcmVuID0gdGhpcy5fc2NhbkZ1bmN0aW9uLnJvbGUubm9kZS5jaGlsZHJlbjtcbiAgICAgIGZvciAoY29uc3QgY2hpbGQgb2YgY2ZuU2NhblJvbGVDaGlsZHJlbikge1xuICAgICAgICBjb25zdCByZXNvdXJjZSA9IGNoaWxkLm5vZGUuZGVmYXVsdENoaWxkIGFzIENmblJlc291cmNlO1xuICAgICAgICBpZiAocmVzb3VyY2UgIT0gdW5kZWZpbmVkICYmIHJlc291cmNlLmNmblJlc291cmNlVHlwZSA9PSAnQVdTOjpJQU06OlBvbGljeScpIHtcbiAgICAgICAgICByZXNvdXJjZS5hZGRNZXRhZGF0YSgnY2RrX25hZycsIHtcbiAgICAgICAgICAgIHJ1bGVzX3RvX3N1cHByZXNzOiBbXG4gICAgICAgICAgICAgIHtcbiAgICAgICAgICAgICAgICBpZDogJ0F3c1NvbHV0aW9ucy1JQU01JyxcbiAgICAgICAgICAgICAgICByZWFzb246XG4gICAgICAgICAgICAgICAgICAnVGhlIEVGUyBtb3VudCBwb2ludCBwZXJtaXNzaW9ucyBhcmUgY29udHJvbGxlZCB0aHJvdWdoIGEgY29uZGl0aW9uIHdoaWNoIGxpbWl0IHRoZSBzY29wZSBvZiB0aGUgKiByZXNvdXJjZXMuJyxcbiAgICAgICAgICAgICAgfSxcbiAgICAgICAgICAgIF0sXG4gICAgICAgICAgfSk7XG4gICAgICAgIH1cbiAgICAgIH1cbiAgICB9XG4gICAgdGhpcy5fc2NhbkZ1bmN0aW9uLmNvbm5lY3Rpb25zLmFsbG93VG9BbnlJcHY0KFxuICAgICAgUG9ydC50Y3AoNDQzKSxcbiAgICAgICdBbGxvdyBvdXRib3VuZCBIVFRQUyB0cmFmZmljIGZvciBTMyBhY2Nlc3MuJyxcbiAgICApO1xuICAgIGRlZnNfYnVja2V0LmdyYW50UmVhZCh0aGlzLl9zY2FuRnVuY3Rpb24pO1xuXG4gICAgY29uc3QgZG93bmxvYWRfZGVmcyA9IG5ldyBEb2NrZXJJbWFnZUZ1bmN0aW9uKHRoaXMsICdEb3dubG9hZERlZnMnLCB7XG4gICAgICBjb2RlOiBEb2NrZXJJbWFnZUNvZGUuZnJvbUltYWdlQXNzZXQoXG4gICAgICAgIHBhdGguam9pbihfX2Rpcm5hbWUsICcuLi9hc3NldHMvbGFtYmRhL2NvZGUvZG93bmxvYWRfZGVmcycpLFxuICAgICAgICB7XG4gICAgICAgICAgYnVpbGRBcmdzOiB7XG4gICAgICAgICAgICAvLyBPbmx5IGZvcmNlIHVwZGF0ZSB0aGUgZG9ja2VyIGxheWVyIGNhY2hlIG9uY2UgYSBkYXlcbiAgICAgICAgICAgIENBQ0hFX0RBVEU6IG5ldyBEYXRlKCkudG9EYXRlU3RyaW5nKCksXG4gICAgICAgICAgfSxcbiAgICAgICAgICBleHRyYUhhc2g6IERhdGUubm93KCkudG9TdHJpbmcoKSxcbiAgICAgICAgfSxcbiAgICAgICksXG4gICAgICB0aW1lb3V0OiBEdXJhdGlvbi5taW51dGVzKDUpLFxuICAgICAgbWVtb3J5U2l6ZTogMTAyNCxcbiAgICAgIGVudmlyb25tZW50OiB7XG4gICAgICAgIERFRlNfQlVDS0VUOiBkZWZzX2J1Y2tldC5idWNrZXROYW1lLFxuICAgICAgICBQT1dFUlRPT0xTX1NFUlZJQ0VfTkFNRTogJ2ZyZXNoY2xhbS11cGRhdGUnLFxuICAgICAgfSxcbiAgICB9KTtcbiAgICBjb25zdCBzdGFjayA9IFN0YWNrLm9mKHRoaXMpO1xuXG4gICAgaWYgKGRvd25sb2FkX2RlZnMucm9sZSkge1xuICAgICAgY29uc3QgZG93bmxvYWRfZGVmc19yb2xlID0gYGFybjoke3N0YWNrLnBhcnRpdGlvbn06c3RzOjoke3N0YWNrLmFjY291bnR9OmFzc3VtZWQtcm9sZS8ke2Rvd25sb2FkX2RlZnMucm9sZS5yb2xlTmFtZX0vJHtkb3dubG9hZF9kZWZzLmZ1bmN0aW9uTmFtZX1gO1xuICAgICAgY29uc3QgZG93bmxvYWRfZGVmc19hc3N1bWVkX3ByaW5jaXBhbCA9IG5ldyBBcm5QcmluY2lwYWwoXG4gICAgICAgIGRvd25sb2FkX2RlZnNfcm9sZSxcbiAgICAgICk7XG4gICAgICBkZWZzX2J1Y2tldC5hZGRUb1Jlc291cmNlUG9saWN5KFxuICAgICAgICBuZXcgUG9saWN5U3RhdGVtZW50KHtcbiAgICAgICAgICBlZmZlY3Q6IEVmZmVjdC5ERU5ZLFxuICAgICAgICAgIGFjdGlvbnM6IFsnczM6UHV0T2JqZWN0KiddLFxuICAgICAgICAgIHJlc291cmNlczogW2RlZnNfYnVja2V0LmFybkZvck9iamVjdHMoJyonKV0sXG4gICAgICAgICAgbm90UHJpbmNpcGFsczogW2Rvd25sb2FkX2RlZnMucm9sZSwgZG93bmxvYWRfZGVmc19hc3N1bWVkX3ByaW5jaXBhbF0sXG4gICAgICAgIH0pLFxuICAgICAgKTtcbiAgICAgIGRlZnNfYnVja2V0LmdyYW50UmVhZFdyaXRlKGRvd25sb2FkX2RlZnMpO1xuICAgICAgY29uc3QgY2ZuRG93bmxvYWRSb2xlID0gZG93bmxvYWRfZGVmcy5yb2xlLm5vZGUuZGVmYXVsdENoaWxkIGFzIENmblJvbGU7XG4gICAgICBjZm5Eb3dubG9hZFJvbGUuYWRkTWV0YWRhdGEoJ2Nka19uYWcnLCB7XG4gICAgICAgIHJ1bGVzX3RvX3N1cHByZXNzOiBbXG4gICAgICAgICAge1xuICAgICAgICAgICAgaWQ6ICdBd3NTb2x1dGlvbnMtSUFNNCcsXG4gICAgICAgICAgICByZWFzb246XG4gICAgICAgICAgICAgICdUaGUgQVdTTGFtYmRhQmFzaWNFeGVjdXRpb25Sb2xlIGRvZXMgbm90IHByb3ZpZGUgcGVybWlzc2lvbnMgYmV5b25kIHVwbG9hZGluZyBsb2dzIHRvIENsb3VkV2F0Y2guJyxcbiAgICAgICAgICB9LFxuICAgICAgICBdLFxuICAgICAgfSk7XG4gICAgICBjb25zdCBjZm5Eb3dubG9hZFJvbGVDaGlsZHJlbiA9IGRvd25sb2FkX2RlZnMucm9sZS5ub2RlLmNoaWxkcmVuO1xuICAgICAgZm9yIChjb25zdCBjaGlsZCBvZiBjZm5Eb3dubG9hZFJvbGVDaGlsZHJlbikge1xuICAgICAgICBjb25zdCByZXNvdXJjZSA9IGNoaWxkLm5vZGUuZGVmYXVsdENoaWxkIGFzIENmblJlc291cmNlO1xuICAgICAgICBpZiAocmVzb3VyY2UgIT0gdW5kZWZpbmVkICYmIHJlc291cmNlLmNmblJlc291cmNlVHlwZSA9PSAnQVdTOjpJQU06OlBvbGljeScpIHtcbiAgICAgICAgICByZXNvdXJjZS5hZGRNZXRhZGF0YSgnY2RrX25hZycsIHtcbiAgICAgICAgICAgIHJ1bGVzX3RvX3N1cHByZXNzOiBbXG4gICAgICAgICAgICAgIHtcbiAgICAgICAgICAgICAgICBpZDogJ0F3c1NvbHV0aW9ucy1JQU01JyxcbiAgICAgICAgICAgICAgICByZWFzb246XG4gICAgICAgICAgICAgICAgICAnVGhlIGZ1bmN0aW9uIGlzIGFsbG93ZWQgdG8gcGVyZm9ybSBvcGVyYXRpb25zIG9uIGFsbCBwcmVmaXhlcyBpbiB0aGUgc3BlY2lmaWVkIGJ1Y2tldC4nLFxuICAgICAgICAgICAgICB9LFxuICAgICAgICAgICAgXSxcbiAgICAgICAgICB9KTtcbiAgICAgICAgfVxuICAgICAgfVxuICAgIH1cblxuICAgIG5ldyBSdWxlKHRoaXMsICdWaXJ1c0RlZnNVcGRhdGVSdWxlJywge1xuICAgICAgc2NoZWR1bGU6IFNjaGVkdWxlLnJhdGUoRHVyYXRpb24uaG91cnMoMTIpKSxcbiAgICAgIHRhcmdldHM6IFtuZXcgTGFtYmRhRnVuY3Rpb24oZG93bmxvYWRfZGVmcyldLFxuICAgIH0pO1xuXG4gICAgY29uc3QgaW5pdF9kZWZzX2NyID0gbmV3IEZ1bmN0aW9uKHRoaXMsICdJbml0RGVmcycsIHtcbiAgICAgIHJ1bnRpbWU6IFJ1bnRpbWUuUFlUSE9OXzNfOCxcbiAgICAgIGNvZGU6IENvZGUuZnJvbUFzc2V0KFxuICAgICAgICBwYXRoLmpvaW4oX19kaXJuYW1lLCAnLi4vYXNzZXRzL2xhbWJkYS9jb2RlL2luaXRpYWxpemVfZGVmc19jcicpLFxuICAgICAgKSxcbiAgICAgIGhhbmRsZXI6ICdsYW1iZGEubGFtYmRhX2hhbmRsZXInLFxuICAgICAgdGltZW91dDogRHVyYXRpb24ubWludXRlcyg1KSxcbiAgICB9KTtcbiAgICBkb3dubG9hZF9kZWZzLmdyYW50SW52b2tlKGluaXRfZGVmc19jcik7XG4gICAgaWYgKGluaXRfZGVmc19jci5yb2xlKSB7XG4gICAgICBjb25zdCBjZm5TY2FuUm9sZSA9IGluaXRfZGVmc19jci5yb2xlLm5vZGUuZGVmYXVsdENoaWxkIGFzIENmblJvbGU7XG4gICAgICBjZm5TY2FuUm9sZS5hZGRNZXRhZGF0YSgnY2RrX25hZycsIHtcbiAgICAgICAgcnVsZXNfdG9fc3VwcHJlc3M6IFtcbiAgICAgICAgICB7XG4gICAgICAgICAgICBpZDogJ0F3c1NvbHV0aW9ucy1JQU00JyxcbiAgICAgICAgICAgIHJlYXNvbjpcbiAgICAgICAgICAgICAgJ1RoZSBBV1NMYW1iZGFCYXNpY0V4ZWN1dGlvblJvbGUgZG9lcyBub3QgcHJvdmlkZSBwZXJtaXNzaW9ucyBiZXlvbmQgdXBsb2FkaW5nIGxvZ3MgdG8gQ2xvdWRXYXRjaC4nLFxuICAgICAgICAgIH0sXG4gICAgICAgIF0sXG4gICAgICB9KTtcbiAgICB9XG4gICAgbmV3IEN1c3RvbVJlc291cmNlKHRoaXMsICdJbml0RGVmc0NyJywge1xuICAgICAgc2VydmljZVRva2VuOiBpbml0X2RlZnNfY3IuZnVuY3Rpb25Bcm4sXG4gICAgICBwcm9wZXJ0aWVzOiB7XG4gICAgICAgIEZuTmFtZTogZG93bmxvYWRfZGVmcy5mdW5jdGlvbk5hbWUsXG4gICAgICB9LFxuICAgIH0pO1xuXG4gICAgaWYgKHByb3BzLmJ1Y2tldHMpIHtcbiAgICAgIHByb3BzLmJ1Y2tldHMuZm9yRWFjaCgoYnVja2V0KSA9PiB7XG4gICAgICAgIHRoaXMuYWRkU291cmNlQnVja2V0KGJ1Y2tldCk7XG4gICAgICB9KTtcbiAgICB9XG4gIH1cblxuICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICBcbiAgYWRkU291cmNlQnVja2V0KGJ1Y2tldDogQnVja2V0KSB7XG4gICAgdGhpcy5fc2NhbkZ1bmN0aW9uLmFkZEV2ZW50U291cmNlKFxuICAgICAgbmV3IFMzRXZlbnRTb3VyY2UoYnVja2V0LCB7IGV2ZW50czogW0V2ZW50VHlwZS5PQkpFQ1RfQ1JFQVRFRF0gfSksXG4gICAgKTtcbiAgICBidWNrZXQuZ3JhbnRSZWFkKHRoaXMuX3NjYW5GdW5jdGlvbik7XG4gICAgdGhpcy5fc2NhbkZ1bmN0aW9uLmFkZFRvUm9sZVBvbGljeShcbiAgICAgIG5ldyBQb2xpY3lTdGF0ZW1lbnQoe1xuICAgICAgICBlZmZlY3Q6IEVmZmVjdC5BTExPVyxcbiAgICAgICAgYWN0aW9uczogWydzMzpQdXRPYmplY3RUYWdnaW5nJywgJ3MzOlB1dE9iamVjdFZlcnNpb25UYWdnaW5nJ10sXG4gICAgICAgIHJlc291cmNlczogW2J1Y2tldC5hcm5Gb3JPYmplY3RzKCcqJyldLFxuICAgICAgfSksXG4gICAgKTtcblxuICAgIGlmICh0aGlzLl9zY2FuRnVuY3Rpb24ucm9sZSkge1xuICAgICAgY29uc3Qgc3RhY2sgPSBTdGFjay5vZih0aGlzKTtcbiAgICAgIGNvbnN0IHNjYW5fYXNzdW1lZF9yb2xlID0gYGFybjoke3N0YWNrLnBhcnRpdGlvbn06c3RzOjoke3N0YWNrLmFjY291bnR9OmFzc3VtZWQtcm9sZS8ke3RoaXMuX3NjYW5GdW5jdGlvbi5yb2xlLnJvbGVOYW1lfS8ke3RoaXMuX3NjYW5GdW5jdGlvbi5mdW5jdGlvbk5hbWV9YDtcbiAgICAgIGNvbnN0IHNjYW5fYXNzdW1lZF9wcmluY2lwYWwgPSBuZXcgQXJuUHJpbmNpcGFsKHNjYW5fYXNzdW1lZF9yb2xlKTtcbiAgICAgIHRoaXMuX3MzR3cuYWRkVG9Qb2xpY3koXG4gICAgICAgIG5ldyBQb2xpY3lTdGF0ZW1lbnQoe1xuICAgICAgICAgIGVmZmVjdDogRWZmZWN0LkFMTE9XLFxuICAgICAgICAgIGFjdGlvbnM6IFsnczM6R2V0T2JqZWN0KicsICdzMzpHZXRCdWNrZXQqJywgJ3MzOkxpc3QqJ10sXG4gICAgICAgICAgcmVzb3VyY2VzOiBbYnVja2V0LmJ1Y2tldEFybiwgYnVja2V0LmFybkZvck9iamVjdHMoJyonKV0sXG4gICAgICAgICAgcHJpbmNpcGFsczogW3RoaXMuX3NjYW5GdW5jdGlvbi5yb2xlLCBzY2FuX2Fzc3VtZWRfcHJpbmNpcGFsXSxcbiAgICAgICAgfSksXG4gICAgICApO1xuICAgICAgdGhpcy5fczNHdy5hZGRUb1BvbGljeShcbiAgICAgICAgbmV3IFBvbGljeVN0YXRlbWVudCh7XG4gICAgICAgICAgZWZmZWN0OiBFZmZlY3QuQUxMT1csXG4gICAgICAgICAgYWN0aW9uczogWydzMzpQdXRPYmplY3RUYWdnaW5nJywgJ3MzOlB1dE9iamVjdFZlcnNpb25UYWdnaW5nJ10sXG4gICAgICAgICAgcmVzb3VyY2VzOiBbYnVja2V0LmFybkZvck9iamVjdHMoJyonKV0sXG4gICAgICAgICAgcHJpbmNpcGFsczogW3RoaXMuX3NjYW5GdW5jdGlvbi5yb2xlLCBzY2FuX2Fzc3VtZWRfcHJpbmNpcGFsXSxcbiAgICAgICAgfSksXG4gICAgICApO1xuXG4gICAgICAvLyBOZWVkIHRoZSBhc3N1bWVkIHJvbGUgZm9yIHRoZSBub3QgUHJpbmNpcGFsIEFjdGlvbiB3aXRoIExhbWJkYVxuICAgICAgYnVja2V0LmFkZFRvUmVzb3VyY2VQb2xpY3koXG4gICAgICAgIG5ldyBQb2xpY3lTdGF0ZW1lbnQoe1xuICAgICAgICAgIGVmZmVjdDogRWZmZWN0LkRFTlksXG4gICAgICAgICAgYWN0aW9uczogWydzMzpHZXRPYmplY3QnXSxcbiAgICAgICAgICByZXNvdXJjZXM6IFtidWNrZXQuYXJuRm9yT2JqZWN0cygnKicpXSxcbiAgICAgICAgICBub3RQcmluY2lwYWxzOiBbdGhpcy5fc2NhbkZ1bmN0aW9uLnJvbGUsIHNjYW5fYXNzdW1lZF9wcmluY2lwYWxdLFxuICAgICAgICAgIGNvbmRpdGlvbnM6IHtcbiAgICAgICAgICAgIFN0cmluZ0VxdWFsczoge1xuICAgICAgICAgICAgICAnczM6RXhpc3RpbmdPYmplY3RUYWcvc2Nhbi1zdGF0dXMnOiBbXG4gICAgICAgICAgICAgICAgJ0lOIFBST0dSRVNTJyxcbiAgICAgICAgICAgICAgICAnSU5GRUNURUQnLFxuICAgICAgICAgICAgICAgICdFUlJPUicsXG4gICAgICAgICAgICAgIF0sXG4gICAgICAgICAgICB9LFxuICAgICAgICAgIH0sXG4gICAgICAgIH0pLFxuICAgICAgKTtcbiAgICB9XG4gIH1cbn1cbiJdfQ==