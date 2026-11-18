output "resource_group" {
  value = azurerm_resource_group.main.name
}

output "portal_url" {
  value = "https://${azurerm_container_app.web.ingress[0].fqdn}"
}

output "bootstrap_admin_password" {
  description = "First password for the CareConnect account admin, when auth_mode is database. It must be changed at first sign-in."
  value       = random_password.bootstrap_admin.result
  sensitive   = true
}
