#!/usr/bin/env bash

echo "Building artifact:"
docker run -v ${PWD}:/ramble -w /ramble openjdk:8-alpine ./gradlew shadowJar
echo "Done."

echo "Build runtime container:"
docker build -t ramble .
echo "Done."
