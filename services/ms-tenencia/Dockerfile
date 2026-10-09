# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN chmod +x mvnw
# Cache compartido de Maven entre microservicios y entre builds.
# Sin el, cada servicio vuelve a descargar todo el BOM de Spring Boot desde
# Maven Central y cualquier corte de red aborta el build completo.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -B -ntp -DskipTests \
      -Dmaven.wagon.http.retryHandler.count=5 \
      -Dmaven.wagon.httpconnectionManager.ttlSeconds=60 \
      dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -B -ntp -DskipTests clean package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --system --gid 10001 spring \
    && useradd --system --uid 10001 --gid spring --no-create-home spring

COPY --from=build /workspace/target/*.jar /app/app.jar

USER spring:spring

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
