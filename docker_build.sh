#!/usr/bin/env bash

echo "Building artifact:"
docker run -v ${PWD}:/vrap -w /vrap openjdk:8-alpine ./gradlew shadowJar
echo "Done."

echo "Build runtime container:"
docker build -t vrap .
echo "Done."
