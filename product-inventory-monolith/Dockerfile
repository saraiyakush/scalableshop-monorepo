# Stage 1: Build the application
FROM eclipse-temurin:17-jdk-focal AS builder

WORKDIR /app

# Copy Gradle wrapper and configuration files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Copy the source code
COPY src src

# Make gradlew executable
RUN chmod +x gradlew

# Build the application - this runs 'gradlew bootJar'
# Use --no-daemon to prevent issues in Docker builds
# Use --refresh-dependencies to ensure latest dependencies are pulled
RUN ./gradlew bootJar --no-daemon --refresh-dependencies

# Stage 2: Create the final image
# Use a smaller JRE image for the final production image
FROM eclipse-temurin:17-jre-focal AS final

WORKDIR /app

# Copy the built JAR from the 'builder' stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose the port your Spring Boot app runs on
EXPOSE 8080

# Command to run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
