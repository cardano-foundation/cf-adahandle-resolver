FROM eclipse-temurin:21-jdk AS build

WORKDIR /app
COPY pom.xml /app/pom.xml
COPY mvnw /app/mvnw
COPY .mvn /app/.mvn

RUN ./mvnw verify clean --fail-never
COPY . /app
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:24.0.1_9-jre-ubi9-minimal AS runtime
WORKDIR /app
COPY --from=build /app/target/*.jar /app/cf-adahandle-resolver.jar
ENTRYPOINT ["java", "-jar", "cf-adahandle-resolver.jar"]