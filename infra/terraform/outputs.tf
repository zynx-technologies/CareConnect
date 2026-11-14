output "resource_group" {
  value = azurerm_resource_group.main.name
}

output "portal_url" {
  value = "https://${azurerm_container_app.web.ingress[0].fqdn}"
}
