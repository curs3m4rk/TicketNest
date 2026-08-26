FROM maven:3.9.11-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
RUN mvn --batch-mode --no-transfer-progress package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup --system ticketnest \
    && adduser --system --ingroup ticketnest ticketnest

WORKDIR /app

COPY --from=build --chown=ticketnest:ticketnest /workspace/target/ticketnest-*.jar app.jar

USER ticketnest

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
