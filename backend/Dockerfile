# Build the Spring Boot application
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw

COPY src/ src/

RUN ./mvnw --batch-mode -DskipTests clean package

# Run the application
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/essential-butterfly-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
