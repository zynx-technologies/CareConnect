environment  = "dev"
location     = "australiaeast"
vnet_cidr    = "10.40.0.0/22"
database_sku = "GP_S_Gen5_1"
demo_data    = true
auth_mode    = "database"
# Serverless: pauses after 60 idle minutes, so the database costs only storage overnight and at weekends.
database_auto_pause_minutes = 60
