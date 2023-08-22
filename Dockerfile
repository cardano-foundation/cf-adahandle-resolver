FROM openjdk:18-jdk-slim AS build

WORKDIR /app
COPY pom.xml /app/pom.xml
COPY mvnw /app/mvnw
COPY .mvn /app/.mvn

RUN ./mvnw verify clean --fail-never
COPY . /app
RUN ./mvnw clean package

FROM openjdk:18-jdk-slim AS runtime
COPY --from=build /app/target/*.jar /app/adahandle-resolver.jar

WORKDIR /app
ENTRYPOINT ["java", "-jar", "adahandle-resolver.jar"]