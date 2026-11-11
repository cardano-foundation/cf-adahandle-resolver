# Eclipse Temurin on floating JDK 25 tags, so every build gets the latest JDK 25 and Ubuntu security updates.
FROM eclipse-temurin:25-jdk-resolute AS build

WORKDIR /app
COPY pom.xml /app/pom.xml
COPY mvnw /app/mvnw
COPY .mvn /app/.mvn

RUN ./mvnw verify clean --fail-never
COPY . /app
# In a git worktree, .git is a file pointing to the main checkout, which isn't copied: drop it so the build runs
# as without git (no commit on /actuator/info) instead of failing. A normal checkout's .git directory stays.
RUN if [ -f .git ]; then rm .git; fi
RUN ./mvnw clean package -DskipTests

# Ubuntu 26.04 LTS (resolute) JRE; like 24.04, it runs on x86-64-v2 CPUs. Not ubi10-minimal: UBI 10 requires an
# x86-64-v3 CPU (AVX2) and fails at startup on older hosts and generic VM CPU models with
# "Fatal glibc error: CPU does not support x86-64-v3". Temurin 25 has no ubi9-minimal image.
FROM eclipse-temurin:25-jre-resolute AS runtime
# Fail the build on a JDK older than 25.0.5+8 (e.g. a stale cached base image): its trust store still has the
# roots that signed the compromised eMudhra intermediate CAs.
# https://adoptium.net/news/2026/10/temurin-october-2026-emudhra/
RUN ! keytool -list -v -cacerts -storepass changeit | grep -E "^Owner: .*CN=(emSign ECC Root CA - G3|AAA Certificate Services)\b"
WORKDIR /app
COPY --from=build /app/target/*.jar /app/cf-adahandle-resolver.jar
# The API port (server.port, PORT); change it here too if PORT is changed.
EXPOSE 9095
ENTRYPOINT ["java", "-jar", "cf-adahandle-resolver.jar"]
