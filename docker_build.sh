#!/usr/bin/env bash

echo "Build runtime container:"
docker build --rm -t vrapio/vrap .
echo "Done."
