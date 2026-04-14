variable "do_token" {
  description = "DigitalOcean API token."
  type        = string
  sensitive   = true
}

variable "project_name" {
  description = "DigitalOcean project name."
  type        = string
  default     = "oolshik-mvp"
}

variable "environment" {
  description = "Environment label."
  type        = string
  default     = "prod"
}

variable "region" {
  description = "DigitalOcean region slug for VPC and managed database."
  type        = string
  default     = "blr1"
}

variable "vpc_name" {
  description = "VPC name."
  type        = string
  default     = "oolshik-blr1-vpc"
}

variable "vpc_ip_range" {
  description = "Private CIDR range for the VPC."
  type        = string
  default     = "10.20.0.0/24"
}

variable "db_cluster_name" {
  description = "Managed PostgreSQL cluster name."
  type        = string
  default     = "oolshik-pg"
}

variable "db_engine_version" {
  description = "Managed PostgreSQL major version."
  type        = string
  default     = "16"
}

variable "db_size" {
  description = "Managed PostgreSQL node size."
  type        = string
  default     = "db-s-1vcpu-1gb"
}

variable "db_node_count" {
  description = "Managed PostgreSQL node count."
  type        = number
  default     = 1
}

variable "db_name" {
  description = "Application database name."
  type        = string
  default     = "oolshik"
}

variable "db_user_name" {
  description = "Application database user."
  type        = string
  default     = "oolshik_app"
}
