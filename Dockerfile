FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/pedido-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
# Use a small entrypoint script that waits for Postgres to be reachable before starting the app.
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh
# Install pg_isready (postgresql-client) so the entrypoint can wait for a healthy Postgres
RUN apt-get update \
	&& apt-get install -y --no-install-recommends postgresql-client \
	&& rm -rf /var/lib/apt/lists/*

ENTRYPOINT ["/app/docker-entrypoint.sh"]

