#!/usr/bin/env sh
echo "Launching Aggregation Service..."
./mvnw clean install
docker build -t aggregation-service .
docker-compose up
