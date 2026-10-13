# Etape de build : Maven et JDK absents de l'image finale.
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
# Copier les dependances avant le code conserve le cache Maven.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
# Le build echoue si un test unitaire echoue.
RUN mvn -B -ntp verify

# Alpine + JRE : pas de compilateur ni de cache Maven en production.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S -g 10001 appgroup && \
    adduser -S -D -H -u 10001 -G appgroup appuser
COPY --from=build --chown=10001:10001 /build/target/app.jar app.jar
# L'application fonctionne sans privileges root.
USER 10001:10001
ENV APP_PORT=8080 JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0"
EXPOSE 8080
# CMD est execute au demarrage, pas pendant docker build.
CMD ["java", "-jar", "/app/app.jar"]
