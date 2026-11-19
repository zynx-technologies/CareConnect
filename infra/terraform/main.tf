locals {
  name       = "careconnect-${var.environment}"
  serverless = startswith(var.database_sku, "GP_S_")
  tags = {
    application = "careconnect"
    environment = var.environment
    managed_by  = "terraform"
  }
}

data "azurerm_container_registry" "shared" {
  name                = var.acr_name
  resource_group_name = var.acr_resource_group
}

resource "azurerm_resource_group" "main" {
  name     = "rg-${local.name}"
  location = var.location
  tags     = local.tags
}

resource "azurerm_log_analytics_workspace" "main" {
  name                = "log-${local.name}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "PerGB2018"
  retention_in_days   = var.log_retention_days
  tags                = local.tags
}

# ---------- Network ----------

resource "azurerm_virtual_network" "main" {
  name                = "vnet-${local.name}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  address_space       = [var.vnet_cidr]
  tags                = local.tags
}

resource "azurerm_subnet" "apps" {
  name                 = "snet-apps"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [cidrsubnet(var.vnet_cidr, 1, 0)]

  delegation {
    name = "container-apps"
    service_delegation {
      name    = "Microsoft.App/environments"
      actions = ["Microsoft.Network/virtualNetworks/subnets/join/action"]
    }
  }
}

resource "azurerm_subnet" "private_endpoints" {
  name                 = "snet-private-endpoints"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [cidrsubnet(var.vnet_cidr, 2, 2)]
}

resource "azurerm_private_dns_zone" "sql" {
  name                = "privatelink.database.windows.net"
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.tags
}

resource "azurerm_private_dns_zone_virtual_network_link" "sql" {
  name                = "sql"
  private_dns_zone_id = azurerm_private_dns_zone.sql.id
  virtual_network_id  = azurerm_virtual_network.main.id
}

# ---------- Identity ----------

resource "azurerm_user_assigned_identity" "app" {
  name                = "id-${local.name}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.tags
}

resource "azurerm_role_assignment" "acr_pull" {
  scope                = data.azurerm_container_registry.shared.id
  role_definition_name = "AcrPull"
  principal_id         = azurerm_user_assigned_identity.app.principal_id
  principal_type       = "ServicePrincipal"
}

# ---------- Database ----------

resource "random_string" "sql_suffix" {
  length  = 6
  upper   = false
  special = false
}

resource "azurerm_mssql_server" "main" {
  name                          = "sql-${local.name}-${random_string.sql_suffix.result}"
  location                      = azurerm_resource_group.main.location
  resource_group_name           = azurerm_resource_group.main.name
  version                       = "12.0"
  minimum_tls_version           = "1.2"
  public_network_access_enabled = false
  tags                          = local.tags

  # Entra-only sign-in, so there are no database passwords to manage. The app identity is the administrator
  # because it runs schema migrations at startup. Split this into separate roles before go-live.
  azuread_administrator {
    azuread_authentication_only = true
    login_username              = azurerm_user_assigned_identity.app.name
    object_id                   = azurerm_user_assigned_identity.app.principal_id
    tenant_id                   = var.entra_tenant_id
  }
}

resource "azurerm_mssql_database" "main" {
  name                 = "careconnect"
  server_id            = azurerm_mssql_server.main.id
  sku_name             = var.database_sku
  storage_account_type = "Geo"
  tags                 = local.tags

  # Serverless SKUs (GP_S_*) pause after this many idle minutes and bill only for storage while paused.
  # The first connection after a pause wakes the database, which takes about a minute.
  auto_pause_delay_in_minutes = local.serverless ? var.database_auto_pause_minutes : null
  min_capacity                = local.serverless ? var.database_min_capacity : null

  short_term_retention_policy {
    retention_days = 35
  }
}

resource "azurerm_private_endpoint" "sql" {
  name                = "pe-sql-${local.name}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  subnet_id           = azurerm_subnet.private_endpoints.id
  tags                = local.tags

  private_service_connection {
    name                           = "sql"
    private_connection_resource_id = azurerm_mssql_server.main.id
    subresource_names              = ["sqlServer"]
    is_manual_connection           = false
  }

  private_dns_zone_group {
    name                 = "sql"
    private_dns_zone_ids = [azurerm_private_dns_zone.sql.id]
  }
}

# ---------- Sign-in with CareConnect accounts ----------

# Used only when auth_mode is "database". Kept in Terraform state and passed to the API as Container App secrets.
resource "random_password" "jwt_secret" {
  length  = 64
  special = false
}

# The first administrator account, "admin". Its password must be changed at first sign-in.
resource "random_password" "bootstrap_admin" {
  length           = 20
  min_upper        = 2
  min_lower        = 2
  min_numeric      = 2
  min_special      = 2
  override_special = "!#%+-=?"
}

# ---------- Applications ----------

resource "azurerm_container_app_environment" "main" {
  name                       = "cae-${local.name}"
  location                   = azurerm_resource_group.main.location
  resource_group_name        = azurerm_resource_group.main.name
  logs_destination           = "log-analytics"
  log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id
  infrastructure_subnet_id   = azurerm_subnet.apps.id
  tags                       = local.tags

  workload_profile {
    name                  = "Consumption"
    workload_profile_type = "Consumption"
  }
}

resource "azurerm_container_app" "api" {
  name                         = "careconnect-api"
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
  workload_profile_name        = "Consumption"
  tags                         = local.tags

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.app.id]
  }

  registry {
    server   = data.azurerm_container_registry.shared.login_server
    identity = azurerm_user_assigned_identity.app.id
  }

  secret {
    name  = "jwt-secret"
    value = random_password.jwt_secret.result
  }
  secret {
    name  = "bootstrap-admin-password"
    value = random_password.bootstrap_admin.result
  }

  ingress {
    external_enabled = false
    target_port      = 8080
    transport        = "auto"
    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  template {
    min_replicas = var.api_min_replicas
    max_replicas = 5

    container {
      name   = "api"
      image  = "${data.azurerm_container_registry.shared.login_server}/${var.api_image}"
      cpu    = 1
      memory = "2Gi"

      env {
        name  = "DATABASE_URL"
        value = "jdbc:sqlserver://${azurerm_mssql_server.main.fully_qualified_domain_name}:1433;database=${azurerm_mssql_database.main.name};encrypt=true;trustServerCertificate=false;authentication=ActiveDirectoryManagedIdentity;user=${azurerm_user_assigned_identity.app.client_id}"
      }
      env {
        name  = "ENTRA_ISSUER_URI"
        value = "https://login.microsoftonline.com/${var.entra_tenant_id}/v2.0"
      }
      env {
        name  = "ENTRA_API_AUDIENCE"
        value = var.entra_api_client_id
      }
      env {
        name  = "AZURE_CLIENT_ID"
        value = azurerm_user_assigned_identity.app.client_id
      }
      env {
        name  = "CARECONNECT_AI_ENABLED"
        value = tostring(var.ai_enabled)
      }
      env {
        name  = "CARECONNECT_DEMO_DATA_ENABLED"
        value = tostring(var.demo_data)
      }
      env {
        name  = "CARECONNECT_AUTH_MODE"
        value = var.auth_mode
      }
      env {
        name        = "CARECONNECT_JWT_SECRET"
        secret_name = "jwt-secret"
      }
      env {
        name        = "CARECONNECT_BOOTSTRAP_ADMIN_PASSWORD"
        secret_name = "bootstrap-admin-password"
      }

      startup_probe {
        transport               = "HTTP"
        port                    = 8080
        path                    = "/actuator/health/liveness"
        interval_seconds        = 10
        failure_count_threshold = 30
      }
      liveness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/actuator/health/liveness"
      }
      readiness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/actuator/health/readiness"
      }
    }
  }

  depends_on = [
    azurerm_role_assignment.acr_pull,
    azurerm_private_endpoint.sql,
    azurerm_private_dns_zone_virtual_network_link.sql,
  ]
}

resource "azurerm_container_app" "web" {
  name                         = "careconnect-web"
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
  workload_profile_name        = "Consumption"
  tags                         = local.tags

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.app.id]
  }

  registry {
    server   = data.azurerm_container_registry.shared.login_server
    identity = azurerm_user_assigned_identity.app.id
  }

  ingress {
    external_enabled = true
    target_port      = 8080
    transport        = "auto"
    traffic_weight {
      latest_revision = true
      percentage      = 100
    }

    dynamic "ip_security_restriction" {
      for_each = var.allowed_cidrs
      content {
        name             = "allow-${ip_security_restriction.key}"
        action           = "Allow"
        ip_address_range = ip_security_restriction.value
      }
    }
  }

  template {
    min_replicas = var.web_min_replicas
    max_replicas = 5

    container {
      name   = "web"
      image  = "${data.azurerm_container_registry.shared.login_server}/${var.web_image}"
      cpu    = 0.25
      memory = "0.5Gi"

      env {
        name  = "API_HOST"
        value = azurerm_container_app.api.ingress[0].fqdn
      }
      env {
        name  = "ENTRA_SPA_CLIENT_ID"
        value = var.entra_spa_client_id
      }
      env {
        name  = "ENTRA_TENANT_ID"
        value = var.entra_tenant_id
      }
      env {
        name  = "AUTH_MODE"
        value = var.auth_mode
      }
      env {
        name  = "ENTRA_API_SCOPE"
        value = "api://${var.entra_api_client_id}/access_as_user"
      }

      liveness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/healthz"
      }
      readiness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/healthz"
      }
    }
  }

  depends_on = [azurerm_role_assignment.acr_pull]
}
