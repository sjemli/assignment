#!/usr/bin/env sh
echo "Running TNT assignment"
./mvnw clean install
docker build -t tnt-assignment .
docker run -p 8080:8080 -d tnt-assignment
docker run -p 9090:8080 -d xyzassessment/backend-services
