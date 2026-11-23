terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
  }

  # Remote state is configured at init time from backend.hcl.example:
  #   terraform init -backend-config=backend.hcl
  # The S3 bucket and DynamoDB lock table must be bootstrapped first (see README.md).
  # backend "s3" {}
}
