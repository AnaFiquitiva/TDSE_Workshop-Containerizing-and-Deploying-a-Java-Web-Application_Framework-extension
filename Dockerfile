# Build stage: compiles the framework and runs its tests inside the image build,
# so the image does not depend on a local JDK or Maven installation.
FROM maven:3.9-amazoncorretto-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package

# Runtime stage: only the JRE and the fat JAR.
FROM amazoncorretto:21
WORKDIR /app
COPY --from=build /build/target/webframework-jar-with-dependencies.jar app.jar

ENV APP_ENV=production
ENV PORT=8080
ENV THREADS=16
EXPOSE 8080

# Exec form keeps java as PID 1, so it receives SIGTERM from `docker stop`
# directly and the framework's shutdown hook can drain in-flight requests.
ENTRYPOINT ["java", "-jar", "app.jar"]
