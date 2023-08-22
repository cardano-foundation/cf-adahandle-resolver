FROM openjdk:18-jdk-slim AS build

WORKDIR /app
COPY pom.xml /app/pom.xml
COPY mvnw /app/mvnw
COPY .mvn /app/.mvn

RUN ./mvnw verify clean --fail-never
COPY . /app
RUN ./mvnw clean package -DskipTests

FROM openjdk:18-jdk-slim AS runtime
WORKDIR /app
COPY --from=build /app/target/*.jar /app/adahandle-resolver.jar
COPY --from=build /app/config/application.properties /app/application.properties
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom --spring.config.location=classpath:file:/app/application-properties", "-jar", "adahandle-resolver.jar"]