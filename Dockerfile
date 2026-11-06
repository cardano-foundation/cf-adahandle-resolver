# Eclipse Temurin, pinned to an exact release so an update is a reviewed change. 25.0.5+8 (October 2026) is the
# first JDK 25 release whose trust store (cacerts) drops the roots that signed the compromised eMudhra
# intermediate CAs: https://adoptium.net/news/2026/10/temurin-october-2026-emudhra/
FROM eclipse-temurin:25.0.5_8-jdk-noble AS build

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
FROM eclipse-temurin:25.0.5_8-jre-noble AS runtime
# Fail the build if the trust store still trusts the roots removed for eMudhra.
RUN ! keytool -list -v -cacerts -storepass changeit | grep -E "^Owner: .*CN=(emSign ECC Root CA - G3|AAA Certificate Services)\b"
WORKDIR /app
COPY --from=build /app/target/*.jar /app/cf-adahandle-resolver.jar
ENTRYPOINT ["java", "-jar", "cf-adahandle-resolver.jar"]
