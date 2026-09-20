# =====================================================================
# notificaciones-service · build multi-stage
# =====================================================================

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Capa de dependencias separada del código: si solo cambia src/, esta
# capa se reutiliza y el build no vuelve a descargar todo Maven Central.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------------------------------------------------------------------

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
