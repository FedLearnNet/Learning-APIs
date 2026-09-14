# Learning APIs - Parent Project

Welcome to the **Learning APIs** parent project! 
This repository organizes a multi-module Maven setup built with [Quarkus](https://quarkus.io/) to demonstrate various approaches for learning-based APIs.

- Designed for federated learning scenarios.
---

## Table of Contents

1. [Overview](#overview)
2. [Project Structure](#project-structure)
3. [Prerequisites](#prerequisites)
4. [How to Build](#how-to-build)
5. [How to Run](#how-to-run)
6. [Modules](#modules)
7. [Testing](#testing)

---

## Overview

The **Learning APIs** project is a parent POM that coordinates three Quarkus-based submodules:

- **Core-Learning-API**
- **Global-Learning-API**
- **Local-Learning-API**

Each module focuses on specific use cases and demonstrates how to structure and orchestrate learning-based services in a consistent manner using Quarkus.

### Documentation
- [Quarkus Documentation](https://quarkus.io/guides/)
- [Internal](https://wiki.feddb.cosy.bio/en/platform-dev/architecture/backend/learning-apis)
---

## Project Structure
- **Parent POM**: Defines shared configurations for Quarkus, compiler settings, plugin versions, etc.
- **Core-Learning-API**: Provides fundamental domain logic and reusable utilities across the project.
- **Global-Learning-API**: Global (UHAM) server, Implements globally accessible services or features that can be shared across environments.
- **Local-Learning-API**: Local (Hospital) server, Contains locally scoped services and scenarios.

---

## Prerequisites

1. **Java 25**  
   This project uses the [Maven Compiler Plugin](https://maven.apache.org/plugins/maven-compiler-plugin/) with a target release of 25.

2. **Maven 3.8+**  
   Ensure you have the required Maven version installed:
   ```bash
   mvn -v 
   ```
3. **Quarkus CLI (Optional)**  
   If you want to use the Quarkus CLI, install it by following the instructions [here](https://quarkus.io/get-started/).
4. **Development environment files**  
   Ensure you have the necessary environment files for development. These files mostly include secrets for the deployed test KeyCloak server.

## How to Build
To build the entire project, navigate to the root directory and run:
```bash
mvn clean install
```
This will compile all modules, run tests, and package them into JAR files.

### Docker
After building the project with `mvn clean install`, you can build the docker image.
To build Docker images for each module, change the command to the respective module directory and run:

```bash
docker build -t NAME -f $PROJECT/src/main/docker/Dockerfile.jvm $PROJECT
```

## How to Run
Each submodule has its own Quarkus application. You can start them individually.
1. Navigate to the desired module directory (e.g., `local-learning-api`).
2. Run the application in development mode:
```bash
./mvnw compile quarkus:dev
```
Or with CLI
```bash
quarkus dev
```
This will start the application and enable live coding, allowing you to see changes without restarting the server.

## Testing
To run tests for a specific module, navigate to that module's directory and execute:
```bash
mvn test
```


This repo contains public datasets:
1. Mimic-IV 2.2 demo: https://physionet.org/content/mimic-iv-demo/2.2/
2. US-130: https://archive.ics.uci.edu/dataset/296/diabetes+130-us+hospitals+for+years+1999-2008
