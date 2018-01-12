#!/usr/bin/env bash

echo "Building artifact:"
docker run --rm -v ${PWD}:/vrap -v${HOME}/.gradle:/root/.gradle -w /vrap openjdk:8-alpine ./gradlew shadowJar
echo "Done."

echo "Build runtime container:"
docker build -t vrapio/vrap .
echo "Done."
