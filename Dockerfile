FROM eclipse-temurin:25-jdk AS build

WORKDIR /app
COPY pom.xml /app/pom.xml
COPY mvnw /app/mvnw
COPY .mvn /app/.mvn

RUN ./mvnw verify clean --fail-never
COPY . /app
RUN ./mvnw clean package -DskipTests

# Ubuntu 24.04 (noble) JRE. Not ubi10-minimal: UBI 10 requires an x86-64-v3 CPU (AVX2) and
# fails at startup on older hosts and generic VM CPU models with
# "Fatal glibc error: CPU does not support x86-64-v3". Temurin 25 has no ubi9-minimal image.
FROM eclipse-temurin:25-jre-noble AS runtime
WORKDIR /app
COPY --from=build /app/target/*.jar /app/cf-adahandle-resolver.jar
ENTRYPOINT ["java", "-jar", "cf-adahandle-resolver.jar"]