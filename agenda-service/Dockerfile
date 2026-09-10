# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Primero solo el pom: la capa de dependencias se cachea si no cambia.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# No correr como root dentro del contenedor.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /build/target/agenda-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
