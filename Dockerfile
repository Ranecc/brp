# Root build: aggregator resolves brp-common -> brp-backend (multi-module).
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /src
COPY pom.xml .
COPY brp-common/pom.xml brp-common/
COPY brp-backend/pom.xml brp-backend/
RUN mvn -q -pl brp-backend -am dependency:go-offline
COPY brp-common/src brp-common/src
COPY brp-backend/src brp-backend/src
RUN mvn -q -pl brp-backend -am package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /src/brp-backend/target/brp-backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
