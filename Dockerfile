# syntax=docker/dockerfile:1
# One image: Quarkus backend (API + reader) with the admin Wasm bundle under /admin/.
# Stages 1 and 2 produce platform-independent output (Wasm, fast-jar) and run on the build
# platform; only the runtime stage is built per target architecture.

# 1) Admin app: Compose Multiplatform Wasm distribution
FROM --platform=$BUILDPLATFORM gradle:9.8.0-jdk21 AS admin
# The Node.js binary the Kotlin/Wasm toolchain downloads needs libatomic; brotli pre-compresses
# the bundle
RUN apt-get update && apt-get install -y --no-install-recommends libatomic1 brotli && rm -rf /var/lib/apt/lists/*
WORKDIR /src/admin
COPY admin/ ./
RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    gradle --no-daemon --console=plain wasmJsBrowserDistribution
# Brotli variants next to the originals, sent to browsers that accept br (PrecompressedAdminBundle).
# Done here rather than in the backend stage so a backend-only change keeps this step cached.
RUN find composeApp/build/dist/wasmJs/productionExecutable/ -type f \( -name '*.wasm' -o -name '*.js' \) -print0 \
        | xargs -0 -P "$(nproc)" -n 1 brotli --best --keep

# 2) Backend with the admin bundle as static resources
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS backend
# Without unzip the Maven wrapper fetches the .tar.gz and checks it against the .zip checksum
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /src/backend
COPY backend/ ./
COPY --from=admin /src/admin/composeApp/build/dist/wasmJs/productionExecutable/ src/main/resources/META-INF/resources/admin/
RUN rm -f src/main/resources/META-INF/resources/admin/*.map
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q package -DskipTests

# 3) Runtime
FROM eclipse-temurin:21-jre
WORKDIR /deployments
COPY --from=backend --chown=185:0 /src/backend/target/quarkus-app/ ./
USER 185
EXPOSE 8080
# Image decoding and encoding (media uploads) use AWT without a display
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /deployments/quarkus-run.jar"]
