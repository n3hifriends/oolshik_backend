FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline \
    -Dmaven.wagon.http.retryHandler.count=3 \
    -Dmaven.wagon.http.retryHandler.requestSentEnabled=true

COPY src/ src/
RUN ./mvnw -q -Dmaven.test.skip=true package \
    -Dmaven.wagon.http.retryHandler.count=3 \
    -Dmaven.wagon.http.retryHandler.requestSentEnabled=true

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 spring \
    && mkdir -p /app/data/audio \
    && chown -R spring:spring /app
WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS=""

COPY --chown=spring:spring --from=build /workspace/target/oolshik-backend-0.0.1-SNAPSHOT.jar /app/app.jar
COPY --chown=spring:spring docker/api-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

USER spring
EXPOSE 8080
ENTRYPOINT ["/app/docker-entrypoint.sh"]
