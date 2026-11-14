FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build
COPY gradlew build.gradle settings.gradle ./
COPY gradle/ gradle/
COPY src/main/ src/main/
RUN bash gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre-noble
RUN apt-get update && apt-get install -y --no-install-recommends python3-venv tini \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY backend-b/requirements.txt ./
RUN python3 -m venv /opt/venv \
    && /opt/venv/bin/pip install --no-cache-dir -r requirements.txt
COPY backend-b/som_engine/ som_engine/
COPY --from=build /build/build/libs/*.jar som.jar
COPY deploy/render-start.sh .
ENV SOM_ENGINE_MODE=real
ENTRYPOINT ["/usr/bin/tini", "-g", "--", "bash", "/app/render-start.sh"]
