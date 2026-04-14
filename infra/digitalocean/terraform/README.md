# DigitalOcean Terraform

This Terraform stack provisions the DigitalOcean foundation for the API-only MVP:

- a dedicated DigitalOcean project
- a BLR1 VPC
- a managed PostgreSQL cluster in BLR1
- an application database and database user

It intentionally does not create the App Platform application. The app is configured with [.do/app.yaml](/Users/nitinkalokhe/Ni3/spring_boot_proj/oolshik-backend-otp/.do/app.yaml) after you have the managed database connection details.

## Usage

```bash
cd infra/digitalocean/terraform
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform plan -var="do_token=$DIGITALOCEAN_ACCESS_TOKEN"
terraform apply -var="do_token=$DIGITALOCEAN_ACCESS_TOKEN"
```

After apply:

1. Read the DB outputs.
2. Replace the placeholder env values in [.do/app.yaml](/Users/nitinkalokhe/Ni3/spring_boot_proj/oolshik-backend-otp/.do/app.yaml).
3. Enable the `postgis` extension on the target database.
4. Deploy the app using `doctl apps update` or the GitHub Actions workflow.
