
# Build the backend using Java 17 and Maven
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw

COPY src/ src/

# Tests are handled separately by GitHub Actions.
# Skip test compilation during Docker image packaging.
RUN ./mvnw --batch-mode -Dmaven.test.skip=true clean package

# Run the packaged application
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/essential-butterfly-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
