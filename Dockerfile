FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -B clean package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/webframework-jar-with-dependencies.jar app.jar

ENV APP_ENV=production
EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
