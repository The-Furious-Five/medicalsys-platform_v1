FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN chmod +x mvnw
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -q -DskipTests clean package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --system --gid 10001 spring \
    && useradd --system --uid 10001 --gid spring --no-create-home spring

COPY --from=build /workspace/target/*.jar /app/app.jar

USER spring:spring

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
