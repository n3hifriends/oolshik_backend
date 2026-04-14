output "project_id" {
  value = digitalocean_project.oolshik.id
}

output "vpc_id" {
  value = digitalocean_vpc.oolshik.id
}

output "database_cluster_id" {
  value = digitalocean_database_cluster.postgres.id
}

output "database_host" {
  value = digitalocean_database_cluster.postgres.host
}

output "database_private_host" {
  value = digitalocean_database_cluster.postgres.private_host
}

output "database_port" {
  value = digitalocean_database_cluster.postgres.port
}

output "database_name" {
  value = digitalocean_database_db.app.name
}

output "database_user" {
  value = digitalocean_database_user.app.name
}

output "database_password" {
  value     = digitalocean_database_user.app.password
  sensitive = true
}
