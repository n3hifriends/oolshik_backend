resource "digitalocean_project" "oolshik" {
  name        = var.project_name
  description = "Oolshik MVP deployment resources"
  environment = var.environment
  purpose     = "Web Application"
}

resource "digitalocean_vpc" "oolshik" {
  name        = var.vpc_name
  region      = var.region
  ip_range    = var.vpc_ip_range
  description = "Private network for Oolshik MVP resources in India"
}

resource "digitalocean_database_cluster" "postgres" {
  name                 = var.db_cluster_name
  engine               = "pg"
  version              = var.db_engine_version
  size                 = var.db_size
  region               = var.region
  node_count           = var.db_node_count
  private_network_uuid = digitalocean_vpc.oolshik.id
  project_id           = digitalocean_project.oolshik.id
  tags                 = ["oolshik", var.environment]

  maintenance_window {
    day  = "sunday"
    hour = "18:00"
  }
}

resource "digitalocean_database_db" "app" {
  cluster_id = digitalocean_database_cluster.postgres.id
  name       = var.db_name
}

resource "digitalocean_database_user" "app" {
  cluster_id = digitalocean_database_cluster.postgres.id
  name       = var.db_user_name
}
