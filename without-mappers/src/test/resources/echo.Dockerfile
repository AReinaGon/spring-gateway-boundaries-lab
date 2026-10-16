FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY EchoServer.java .
RUN javac --release 21 EchoServer.java

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/EchoServer.class .
HEALTHCHECK --interval=2s --timeout=3s --retries=20 CMD ["java", "EchoServer", "health"]
ENTRYPOINT ["java", "EchoServer"]
