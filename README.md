# VRAP

Vrap is a validating REST API proxy. In order to be able to validate rest api calls the proxy consumes RAML 1.0 definition files.

## Features

- Supports [RAML 1.0](http://www.raml.org) REST API definition files 
- Provides two modes of operation, which can be set globally on the command line (via the *-m mode* cli option) or set for a request with the `X-Vrap-Mode` http header
    - **example** mode sends the examples for a request as given in the RAML definition
    - **proxy** mode proxies requests to the base uri as given in the RAML definition
 - Validates incoming requests
    - uri parameters
    - headers
    - query parameters
    - request body
 - Validates received response
    - status code
    - body
- Validation can be disabled for a request by sending the `X-Vrap-Disable-Validation` http header
    
    `X-Vrap-Disable-Validation` |
    ----------------------------|--------
    `request`                   | disable request body validation
    `queryParameter` 	        | disable query parameter validation
    `header`                    | disable request header validation
    `response`                  | disable received response body validation

- Easy to integrate into your continous integration pipeline with the [vrapio/vrap](https://hub.docker.com/r/vrapio/vrap/) docker image
- Includes the mulesoft [api-console](https://github.com/mulesoft/api-console) which allows to send requests to VRAP. This makes it easy to send test requests.
- Provides access to the raml definition files via http [http://localhost:5050/api-raml/{filePath}](http://localhost:5050/api-raml/). They are available as text and as syntax highlighted html (`Accepts: text/html`).
    - Additonally a raml definition file with all `!include` tags resolved is available by adding the `include` query parameter [http://localhost:5050/api-raml/{filePath}?include](http://localhost:5050/api-raml/1?include). This is especially useful if you want to use the api-console when and raml definition files contain a lot of includes.

## How to use it

The easisest way is to start VRAP with docker. This requires that you have [docker](https://www.docker.com/) docker installed on your system. When docker is started, you can run VRAP with the following command:

```
docker run -v<RAML-definition-directory>:/api -p5050:5050 vrapio/vrap /api/api.raml 
```
### Comand line options

```
usage: vrap [OPTIONS] <file.raml>
 -a,--api <api>                    URI to proxy to
 -d,--duplicate-detection <bool>   Enable duplicate key detection
 -dr,--dry-run                     Report errors only
 -h,--help                         display help
 -m,--mode <mode>                  vrap mode: [example, proxy]
 -p,--port <port>                  port to listen for requests
 -s,--pool-size <pool-size>        Size of the http client connection pool
 -ssl <mode>                       SSL verification mode: [normal, insecure]
```

## Source code

The source code is available from our github repository [vrapio/vrap](https://github.com/vrapio/vrap/), which contains a `Readme.md` file with build instructions.
	
