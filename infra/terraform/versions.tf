terraform {
  required_version = ">= 1.9"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 5.12"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.7"
    }
  }

  # Values are passed by the pipeline. See infra/bootstrap.sh.
  backend "azurerm" {}
}

provider "azurerm" {
  features {}
}
