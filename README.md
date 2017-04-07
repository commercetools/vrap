[![Build Status](https://travis-ci.org/vrapio/vrap.svg?branch=master)](https://travis-ci.org/vrapio/vrap)

# VRAP
 
Vrap is a validating REST API proxy. In order to be able to validate rest api calls the proxy consumes [RAML](http://raml.org/) 1.0 definition files

## Usage

### Docker

To start vrap using docker use the following command

```
docker run -v<RAML-definition-directory>:/api -p5050:5050 vrapio/vrap /api/api.raml 
```

## Running vrap locally

### Run from shadow/fat jar

Build shadow jar with gradle:

```
./gradlew shadowJar
```

Then run built shadow with java:

```
java -jar build/libs/vrap-all.jar <path-to-raml-file>
```

### Directly via gradle

```
./gradlew run -PcliArgs=<path-to-raml-file>
```

## Validation

In order to validate incoming requests and responses your application has to be configured to use the vrap api url: [http://localhost:5050/api]()

## Web Interface

Vrap includes a small web interface for browsing the API definition.

- API browser [http://localhost:5050/api-raml/]()
    - with resolved includes [http://localhost:5050/api-raml/?include]()

And an API console which uses Vrap as proxy

- API console [http://localhost:5050/api-console/]()
    - with resolved includes [http://localhost:5050/api-console/?include]()
