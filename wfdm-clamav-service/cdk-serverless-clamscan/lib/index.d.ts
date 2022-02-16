import { EventBus, Rule } from 'aws-cdk-lib/aws-events';
import { IDestination } from 'aws-cdk-lib/aws-lambda';
import { Bucket } from 'aws-cdk-lib/aws-s3';
import { Queue } from 'aws-cdk-lib/aws-sqs';
import { Construct } from 'constructs';
/**
 * Interface for ServerlessClamscan Virus Definitions S3 Bucket Logging.
 *
 * @stability stable
 */
export interface ServerlessClamscanLoggingProps {
    /**
     * Destination bucket for the server access logs (Default: Creates a new S3 Bucket for access logs ).
     *
     * @stability stable
     */
    readonly logsBucket?: boolean | Bucket;
    /**
     * Optional log file prefix to use for the bucket's access logs, option is ignored if logs_bucket is set to false.
     *
     * @stability stable
     */
    readonly logsPrefix?: string;
}
/**
 * Interface for creating a ServerlessClamscan.
 *
 * @stability stable
 */
export interface ServerlessClamscanProps {
    /**
     * An optional list of S3 buckets to configure for ClamAV Virus Scanning;
     *
     * buckets can be added later by calling addSourceBucket.
     *
     * @stability stable
     */
    readonly buckets?: Bucket[];
    /**
     * Optionally set a reserved concurrency for the virus scanning Lambda.
     *
     * @see https://docs.aws.amazon.com/lambda/latest/operatorguide/reserved-concurrency.html
     * @stability stable
     */
    readonly reservedConcurrency?: number;
    /**
     * The Lambda Destination for files marked 'CLEAN' or 'INFECTED' based on the ClamAV Virus scan or 'N/A' for scans triggered by S3 folder creation events marked (Default: Creates and publishes to a new Event Bridge Bus if unspecified).
     *
     * @stability stable
     */
    readonly onResult?: IDestination;
    /**
     * The Lambda Destination for files that fail to scan and are marked 'ERROR' or stuck 'IN PROGRESS' due to a Lambda timeout (Default: Creates and publishes to a new SQS queue if unspecified).
     *
     * @stability stable
     */
    readonly onError?: IDestination;
    /**
     * Whether or not to enable encryption on EFS filesystem (Default: enabled).
     *
     * @stability stable
     */
    readonly efsEncryption?: boolean;
    /**
     * Whether or not to enable Access Logging for the Virus Definitions bucket, you can specify an existing bucket and prefix (Default: Creates a new S3 Bucket for access logs ).
     *
     * @stability stable
     */
    readonly defsBucketAccessLogsConfig?: ServerlessClamscanLoggingProps;
}
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
export declare class ServerlessClamscan extends Construct {
    /**
     * The Lambda Destination for failed on erred scans [ERROR, IN PROGRESS (If error is due to Lambda timeout)].
     *
     * @stability stable
     */
    readonly errorDest: IDestination;
    /**
     * The Lambda Destination for completed ClamAV scans [CLEAN, INFECTED].
     *
     * @stability stable
     */
    readonly resultDest: IDestination;
    /**
     * Conditional: The SQS Queue for erred scans if a failure (onError) destination was not specified.
     *
     * @stability stable
     */
    readonly errorQueue?: Queue;
    /**
     * Conditional: The SQS Dead Letter Queue for the errorQueue if a failure (onError) destination was not specified.
     *
     * @stability stable
     */
    readonly errorDeadLetterQueue?: Queue;
    /**
     * Conditional: The Event Bridge Bus for completed ClamAV scans if a success (onResult) destination was not specified.
     *
     * @stability stable
     */
    readonly resultBus?: EventBus;
    /**
     * Conditional: An Event Bridge Rule for files that are marked 'CLEAN' by ClamAV if a success destination was not specified.
     *
     * @stability stable
     */
    readonly cleanRule?: Rule;
    /**
     * Conditional: An Event Bridge Rule for files that are marked 'INFECTED' by ClamAV if a success destination was not specified.
     *
     * @stability stable
     */
    readonly infectedRule?: Rule;
    /**
     * Conditional: The Bucket for access logs for the virus definitions bucket if logging is enabled (defsBucketAccessLogsConfig).
     *
     * @stability stable
     */
    readonly defsAccessLogsBucket?: Bucket;
    private _scanFunction;
    private _s3Gw;
    private _efsRootPath;
    private _efsMountPath;
    private _efsDefsPath;
    /**
     * Creates a ServerlessClamscan construct.
     *
     * @param scope The parent creating construct (usually `this`).
     * @param id The construct's name.
     * @param props A `ServerlessClamscanProps` interface.
     * @stability stable
     */
    constructor(scope: Construct, id: string, props: ServerlessClamscanProps);
    /**
     * Sets the specified S3 Bucket as a s3:ObjectCreate* for the ClamAV function.
     *
     * Grants the ClamAV function permissions to get and tag objects.
     * Adds a bucket policy to disallow GetObject operations on files that are tagged 'IN PROGRESS', 'INFECTED', or 'ERROR'.
     *
     * @param bucket The bucket to add the scanning bucket policy and s3:ObjectCreate* trigger to.
     * @stability stable
     */
    addSourceBucket(bucket: Bucket): void;
}
