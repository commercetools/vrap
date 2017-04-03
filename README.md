# RAML proxy and mock server

## Running ramble locally

### Run from shadow/fat jar

Build shadow jar with gradle:

```
./gradlew shadowJar
```

Then run built shadow with java:

```
java -jar build/libs/ramble-all.jar <path-to-raml-file>
```

### Directly via gradle

```
./gradlew run --PcliArgs=<path-to-raml-file>
```

### Usage with Docker

#### Build the image

```bash
./docker_build.sh
```

or

```bash
docker run -v ${PWD}:/ramble -w /ramble openjdk:8-alpine ./gradlew shadowJar
docker build -t ramble .
```

#### Run the docker image

```bash
docker run -v<RAML-source-directory>:/api -p5050:5050 ramble /api/api.raml 
```

## End points

- API browser [http://localhost:5050/api-raml/]()
    - with resolved includes [http://localhost:5050/api-raml/?include]()
- API console [http://localhost:5050/api-console/]()
    - with resolved includes [http://localhost:5050/api-console/?include]()
