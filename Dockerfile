FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/options-edge-feed-gateway-*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
