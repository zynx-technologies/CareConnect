variable "environment" {
  type        = string
  description = "Environment name."
  validation {
    condition     = contains(["dev", "test", "prod"], var.environment)
    error_message = "environment must be dev, test, or prod."
  }
}

variable "location" {
  type        = string
  description = "Azure region. Patient data must stay in Australian regions; UK regions are for non-production synthetic data only."
  default     = "australiaeast"
  validation {
    condition     = contains(["australiaeast", "australiasoutheast"], var.location) || (var.environment != "prod" && contains(["uksouth", "ukwest"], var.location))
    error_message = "location must be an Australian region (UK regions are allowed outside production)."
  }
}

variable "acr_name" {
  type        = string
  description = "Name of the shared container registry created by infra/bootstrap.sh."
}

variable "acr_resource_group" {
  type        = string
  description = "Resource group of the shared container registry."
}

variable "api_image" {
  type        = string
  description = "API image and tag in the shared registry, such as careconnect-api:abc123."
}

variable "web_image" {
  type        = string
  description = "Web image and tag in the shared registry, such as careconnect-web:abc123."
}

variable "allowed_cidrs" {
  type        = list(string)
  description = "Networks allowed to reach the portal. Open internet access (0.0.0.0/0) is allowed outside production only, until Front Door + WAF is added."
  validation {
    condition     = length(var.allowed_cidrs) > 0 && (var.environment != "prod" || !contains(var.allowed_cidrs, "0.0.0.0/0"))
    error_message = "List at least one network. 0.0.0.0/0 is not allowed in production."
  }
}

variable "entra_tenant_id" {
  type        = string
  description = "Microsoft Entra tenant ID."
}

variable "entra_api_client_id" {
  type        = string
  description = "Client ID of the API app registration. Used as the token audience."
}

variable "entra_spa_client_id" {
  type        = string
  description = "Client ID of the portal app registration."
}

variable "ai_enabled" {
  type        = bool
  description = "AI drafting kill switch. Stays off until the clinical safety committee approves it for an environment."
  default     = false
}

variable "database_sku" {
  type        = string
  description = "Azure SQL service objective."
  default     = "S0"
}

variable "api_min_replicas" {
  type    = number
  default = 1
}

variable "web_min_replicas" {
  type    = number
  default = 1
}

variable "log_retention_days" {
  type    = number
  default = 90
}

variable "vnet_cidr" {
  type        = string
  description = "Address space for this environment. Must not overlap with hospital networks."
  default     = "10.40.0.0/22"
}
