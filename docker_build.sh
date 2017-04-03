#!/usr/bin/env bash

echo "Building artifact:"
docker run -v ${PWD}:/src -w /src openjdk:8-alpine ./gradlew shadowJar
echo "Done."

echo "Build runtime container:"
docker build -t ramble .
echo "Done."
