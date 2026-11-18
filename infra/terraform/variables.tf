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

variable "demo_data" {
  type        = bool
  description = "Loads synthetic demo patients, forecasts, and model evidence so every screen can be shown. Never in production."
  default     = false
  validation {
    condition     = !(var.demo_data && var.environment == "prod")
    error_message = "Synthetic demo data cannot be turned on in production."
  }
}

variable "auth_mode" {
  type        = string
  description = "How people sign in: entra (Microsoft Entra ID with MFA) or database (CareConnect accounts). Production must use entra."
  default     = "entra"
  validation {
    condition     = contains(["entra", "database"], var.auth_mode) && !(var.auth_mode == "database" && var.environment == "prod")
    error_message = "auth_mode must be entra or database, and production must use entra."
  }
}

variable "database_auto_pause_minutes" {
  type        = number
  description = "Serverless SKUs only: idle minutes before the database pauses (60 or more), or -1 to never pause."
  default     = 60
  validation {
    condition     = var.database_auto_pause_minutes == -1 || var.database_auto_pause_minutes >= 60
    error_message = "Use -1 (never pause) or at least 60 minutes."
  }
}

variable "database_min_capacity" {
  type        = number
  description = "Serverless SKUs only: the fewest vCores kept while the database is running."
  default     = 0.5
}
