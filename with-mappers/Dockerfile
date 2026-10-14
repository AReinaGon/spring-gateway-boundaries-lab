FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipITs=true verify

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/gateway-with-mappers.jar /app/gateway.jar
ENTRYPOINT ["java", "-jar", "/app/gateway.jar"]
