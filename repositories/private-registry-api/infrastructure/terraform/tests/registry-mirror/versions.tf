terraform {
  required_version = ">= 1.11, < 2.0"

  required_providers {
    null = {
      source  = "hashicorp/null"
      version = "3.3.0"
    }
  }
}
