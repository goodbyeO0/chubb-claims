FROM node:24.18.0-bookworm-slim@sha256:6f7b03f7c2c8e2e784dcf9295400527b9b1270fd37b7e9a7285cf83b6951452d AS frontend
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY frontend/ ./
COPY api/ /app/api/
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS backend
WORKDIR /app
COPY backend/ ./
COPY --from=frontend /app/frontend/dist/claims/browser/ src/main/resources/static/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy@sha256:a8bff88f760e87e0c8844f8461f807c203fd5e8f78c951c843024647e965eaf5
ENV JAVA_TOOL_OPTIONS="-Xmx384m"
WORKDIR /app
COPY --from=backend /app/target/claims-0.1.0.jar app.jar
USER 10001
ENTRYPOINT ["java", "-jar", "app.jar"]
