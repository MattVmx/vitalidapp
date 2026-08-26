FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY health-service-app/pom.xml ./pom.xml
RUN mvn -B -DskipTests dependency:go-offline

COPY health-service-app/src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=build --chown=spring:spring /workspace/target/health-service-app-0.0.1-SNAPSHOT.jar app.jar

ENV PORT=10000
ENV SPRING_PROFILES_ACTIVE=demo
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

EXPOSE 10000

USER spring
ENTRYPOINT ["java", "-jar", "app.jar"]
