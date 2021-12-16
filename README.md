# nr-bcws-opensearch
opensearch related code
Part of Wildfire One project in support of WFDM product
Intially will be to set up search indexing and support full text search capabilities to support WFDM.  This will also add Virus Scanning as a feature of the service.

## About NR AWS OpenSearch Stack

OpenSearch is a community-driven, open source search and analytics suite derived from Apache 2.0 licensed ElasticSearch 7.10.2 & Kibana 7.10.2.

Confluence: [AWS OpenSearch](https://apps.nrs.gov.bc.ca/int/confluence/x/GaRvBQ)
Url: [Production](https://apm.io.nrs.gov.bc.ca/_plugin/_dashboards)

### Built With

* [Amazon OpenSearch Service](https://aws.amazon.com/opensearch-service)
* [Amazon Lambda](https://aws.amazon.com/lambda/)
* [Apache Tika](https://tika.apache.org/)
* [ClamAV](http://www.clamav.net/)
* [Java](https://tika.apache.org/)
* [Terraform](https://www.terraform.io)
* [Terragrunt](https://terragrunt.gruntwork.io)

## (TBD) Getting Started

The product in deployed using Github actions. A Terraform cloud team server handles running the Terraform. A CI pipeline is setup to run static analysis of the Typescript.

Notes:

* Terraform is limited in the objects it can manage by the AWS Landing Zone permissions.
* AWS Secrets Manager holds the keycloak secrets in a secret named `<env>/nrdk/config/keycloak`.
* The folder `terragrunt/<env>` holds most of the environment specific configuration.

## Local Setup

If you want to run Terragrunt locally, you will need to setup a number of environment variables. Running the deployment locally is not recommended.

### AWS - Environment Variables

As documented in the [AWS CLI documentation](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-envvars.html) which can be obtained from the [Cloud PathFinder login page](https://oidc.gov.bc.ca/auth/realms/umafubc9/protocol/saml/clients/amazon-aws) and clicking on "Click for Credentials" of the appropriate project/environment.

- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_SESSION_TOKEN`
- `AWS_DEFAULT_REGION`

### Keycloak - Environment Variables

You will need a client service account with realm admin privilege.

- `KEYCLOAK_BASEURL`: The base URL ending with "/auth"
- `KEYCLOAK_REALM_NAME`
- `KEYCLOAK_CLIENT_ID`
- `KEYCLOAK_CLIENT_SECRET`

### Terraform cloud team token

You will need a terraform cloud team token, and have it setup in `~/terraform.d/credentials.tfrc.json`. The token is input using a secret for Github actions.

```
{
  "credentials": {
    "app.terraform.io": {
      "token": "<TERRAFORM TEAM TOKEN>"
    }
  }
}
```

# Principles
- Infrastructure as Code
- Configuration as Code
- GitOps:
  - Describe the entire system declaratively
  - Version the canonical desired system state in Git
  - Automatically apply approved changes to the desired state
  - Ensure correctness and alert on divergence with software agents

[![Lifecycle:Experimental](https://img.shields.io/badge/Lifecycle-Experimental-339999)](<Redirect-URL>)
