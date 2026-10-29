FROM eclipse-temurin:25-jdk AS build

WORKDIR /app
COPY pom.xml /app/pom.xml
COPY mvnw /app/mvnw
COPY .mvn /app/.mvn

RUN ./mvnw verify clean --fail-never
COPY . /app
RUN ./mvnw clean package -DskipTests

# Temurin 25 does not publish a ubi9-minimal image. ubi10-minimal is the current minimal UBI JRE.
FROM eclipse-temurin:25-jre-ubi10-minimal AS runtime
WORKDIR /app
COPY --from=build /app/target/*.jar /app/cf-adahandle-resolver.jar
ENTRYPOINT ["java", "-jar", "cf-adahandle-resolver.jar"]