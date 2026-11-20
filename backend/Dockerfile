FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 MAVEN_OPTS=-Xmx256m mvn -B -DskipTests package
FROM eclipse-temurin:21-jre-jammy@sha256:a8bff88f760e87e0c8844f8461f807c203fd5e8f78c951c843024647e965eaf5
ENV JAVA_TOOL_OPTIONS="-Xmx384m"
WORKDIR /app
COPY --from=build /app/target/claims-0.1.0.jar app.jar
USER 10001
ENTRYPOINT ["java", "-jar", "app.jar"]
