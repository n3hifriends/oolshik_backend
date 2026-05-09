sudo docker stop oolshik-api || true
sudo docker rm -f oolshik-api || true

aws ecr get-login-password --region ap-south-1 | sudo docker login --username AWS --password-stdin 653895707563.dkr.ecr.ap-south-1.amazonaws.com

sudo docker pull 653895707563.dkr.ecr.ap-south-1.amazonaws.com/oolshik-api:v2

sudo docker run -d \
 --name oolshik-api \
 --restart unless-stopped \
 -e APP_DB_MODE=rds \
 -p 8080:8080 \
 -e SPRING_PROFILES_ACTIVE=prod \
 -e APP_OTP_PROVIDER=dev \
 -e APP_OTP_DEV_ENABLED=true \
 -e AWS_REGION=ap-south-1 \
 -e SERVER_PORT=8080 \
 -e SPRING_DATASOURCE_URL='jdbc:postgresql://oolshik-dev-ap-south-1-rds.c1acsg0uu5qk.ap-south-1.rds.amazonaws.com:5432/oolshik?sslmode=require' \
 -e SPRING_DATASOURCE_USERNAME='oolshik_admin' \
 -e SPRING_DATASOURCE_PASSWORD='Ndroid11!' \
 -e APP_AUTH_GOOGLE_ENABLED=true \
 -e APP_AUTH_GOOGLE_ALLOWED_CLIENT_IDS='263296903071-l80n6ccefcn05s5apnobtlpl1cc0fd5t.apps.googleusercontent.com,263296903071-v73slipdgnp9ffj4vlnav47usqpf4l3t.apps.googleusercontent.com,263296903071-e6t5p5s4on6naqbkpieo1enrudr2d6ch.apps.googleusercontent.com' \
 -e APP_MESSAGING_KAFKA_ENABLED=true \
 -e KAFKA_BOOTSTRAP_SERVERS='10.20.0.13:9092' \
 -e KAFKA_CONSUMER_GROUP='oolshik-stt-backend' \
 -e KAFKA_TOPIC_STT_JOBS='stt.jobs' \
 -e KAFKA_TOPIC_STT_RESULTS='stt.results' \
 -e KAFKA_TOPIC_STT_DLQ='stt.jobs.dlq' \
 -e KAFKA_TOPIC_NOTIFICATION_EVENTS='notification.events' \
 653895707563.dkr.ecr.ap-south-1.amazonaws.com/oolshik-api:v2

sudo docker logs oolshik-api --tail 100
curl -i http://localhost:8080/health
