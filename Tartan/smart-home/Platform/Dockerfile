# Get the Gradle image so we can build JAR file first
FROM gradle:8.6-jdk21-jammy AS build

COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle shadowJar

# Get the Java image
FROM eclipse-temurin:21

# Install JavaFX
# Comment these out if you need to install it
# RUN apt-get update
# RUN apt-get install -y --no-install-recommends openjfx


WORKDIR /app
COPY --from=build /home/gradle/src/build/libs/tartan-1.0-SNAPSHOT.jar /app
COPY config.docker.yml /app
EXPOSE 8080
EXPOSE 8081

CMD ["java", "-jar","tartan-1.0-SNAPSHOT.jar","server","config.docker.yml"]

