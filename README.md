# 🚀 Scalable Shop Monorepo

# 📖 Project Overview

Welcome to the Scalable Shop Monorepo! This repository hosts a microservices-based e-commerce application built with Spring Boot and Gradle. It's designed to demonstrate a scalable, event-driven architecture, focusing on patterns like the **Outbox Pattern** for reliable event publishing and atomicity across services.

The monorepo structure allows for centralized management of shared code, consistent build configurations, and simplified dependency management across multiple services, while maintaining the benefits of independent service deployment.


## 🧱 Architecture Highlights

* **Microservices:** The application is divided into independent, loosely coupled services.
* **Event-Driven:** Services communicate asynchronously via events, fostering high cohesion and low coupling.
* **Outbox Pattern:** Ensures transactional consistency between database operations and event publishing.
* **Spring Boot:** Rapid application development with Spring ecosystem.
* **Gradle Multi-Project Build:** Manages dependencies and builds for all services from a single root.


## 🧩 Core Services & Modules

This monorepo contains the following primary services and modules:

* `order-service`: Manages customer orders, including creation, status updates, and interaction with other services via events.
* `product-inventory-service`: Manages product stock and handles inventory reservations/deductions based on order events.
* `scalableshop-events`: A shared Gradle module containing common event DTOs (Data Transfer Objects) and contracts used across all services. This ensures type safety and consistency in event communication.


## 🛠️️ Technologies Used

* **Backend:** Java 17+, Spring Boot 3+
* **Build Tool:** Gradle 8+
* **Database:** PostgreSQL (for persistent data storage)
* **Message Broker:** RabbitMQ (for asynchronous event communication)
* **Testing:** JUnit 5, Mockito, AssertJ, JaCoCo, H2
* **Utility:** Project Lombok (for boilerplate code reduction)
* **Containerization:** Docker, Docker Compose (for local infrastructure setup)


## 🏁 Getting Started

Follow these steps to get the Scalable Shop Monorepo up and running on your local machine.

### 1. Clone the Repository

```bash
git clone https://github.com/your-username/scalable-shop-monorepo.git
cd scalable-shop-monorepo
```

### 2. Set up Local Infrastructure with Docker Compose

> The PostgresSQL database is not a part of Docker Compose. The project assumes you have a local instance of a PostgresSQL.

This project uses Docker Compose to manage its local message broker.

```bash
# Start the Docker Compose services in detached mode
docker compose up -d

# Verify that containers are running
docker compose ps
```


### 3. Build All Services

Use the Gradle Wrapper (`./gradlew`) to build all projects within the monorepo. This will compile code, run tests, and package JARs for each service.

```bash
./gradlew clean build
```


## ⚙️ Running Services

You can run individual services directly using Gradle.

### Running a Specific Service (e.g., Order Service)

```bash
./gradlew :order-service:bootRun
```

Replace `:order-service` with the path to the service you want to run (e.g., `:inventory-service`, `:payment-service`).


## 🗂️ Project Structure

The monorepo is structured as follows:

```
.
|-- gradlew                     # Gradle wrapper
|-- order-service               # Order service
|   |-- src
|   |   |-- main
|   |   `-- test
|   |-- build.gradle            # Order service specific build file
|   `-- settings.gradle
|-- product-catalog-service     # Under development
|   |-- src
|   |   |-- main
|   |   `-- test
|   |-- build.gradle
|   `-- settings.gradle
|-- product-inventory-service   # Inventory service
|   |-- src
|   |   |-- main
|   |   `-- test
|   |-- build.gradle            # Inventory service specific build file
|   `-- settings.gradle
|-- scalableshop-events         # Shared module for event DTOs and contracts
|   |-- src
|   |   `-- main
|   |-- build.gradlew           # Events specific build file
|   `-- settings.gradle
|-- build.gradle                # Root build file for global configurations
|-- docker-compose.yml          # Docker compose file to manage infrastructure
|-- gradlew
|-- gradlew.bat
|-- README.md                   # This README file
`-- settings.gradle             # Root project settings file with all modules
```


## ✅ Running Tests

### Run All Tests Across All Services

```bash
./gradlew test
```

### Run Tests for a Specific Service

```bash
./gradlew :order-service:test
```

### Generate JaCoCo Code Coverage Report

To generate the code coverage report for a specific service:

```bash
./gradlew :order-service:jacocoTestReport
```

You can find the generated report at `order-service/build/reports/jacoco/test/html/index.html`.


## 📄 License

This project is open-sourced under the MIT License. See the `LICENSE` file for details.
