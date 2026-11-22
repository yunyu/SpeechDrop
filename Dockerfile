# syntax=docker/dockerfile:1
FROM maven:3-eclipse-temurin-21-resolute AS build
WORKDIR /build

# Resolve backend and frontend dependencies before copying application sources.
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY frontend/package.json frontend/yarn.lock frontend/.yarnrc.yml ./frontend/
RUN mvn -B -ntp \
    'frontend:install-node-and-corepack@install node and corepack' \
    'frontend:corepack@yarn install'

# Pass revision metadata at build time when .git is unavailable.
COPY . .
ARG RENDER_GIT_COMMIT
RUN BUILD_VERSION="$RENDER_GIT_COMMIT" mvn -B -ntp package

FROM eclipse-temurin:21-jre-resolute
COPY --from=build /build/target/SpeechDrop-1.0-SNAPSHOT.jar /app/speechdrop.jar
COPY deploy/config.production.json /app/config.json
WORKDIR /var/data
EXPOSE 6901
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-jar", "/app/speechdrop.jar", "-conf", "/app/config.json"]
