# -------- Build Stage --------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests clean package

# -------- Runtime Stage --------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --create-home app

COPY --from=build /app/target/*.jar /app/app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh

RUN chmod 500 /app/docker-entrypoint.sh && chown -R app:app /app

EXPOSE 8080

ENTRYPOINT ["/app/docker-entrypoint.sh"]
