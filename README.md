# VRAP

Vrap is a validating REST API proxy. In order to be able to validate rest api calls the proxy consumes RAML 1.0 definition files.

## Features

- Supports [RAML 1.0](http://www.raml.org) REST API definition files, which are validated before vrap starts 
- Includes the mulesoft [api-console](https://github.com/mulesoft/api-console) to ease testing
- Provides access to the raml definition files via http [http://localhost:5050/api-raml/{filePath}](http://localhost:5050/api-raml/)
    - Raml definition files are available as text (*Accept: text/plain*) and as syntax highlighted html (web browswer, *Accept: text/html*)
    - Raml definition files with all *!include* tags inlined are available by adding the *include* query parameter [http://localhost:5050/api-raml/{filePath}?include](http://localhost:5050/api-raml/1?include) (this is especially useful if you want to use the api-console and your raml definition files contain a lot of includes)
- Sends examples from the raml definition for a matching request 
- Validates incoming requests against the raml definition
    - all uri parameters are defined and conform to their specified type 
    - headers that are defined conform to their specified type
    - all query parameters are defined and conform to their specified type
    - the request body conforms to the specified type
- Proxies requests to the base uri of the raml definition and validates the received response
    - status code must be defined
    - body type conforms to the specified type
- Validation can be disabled for a request
- Easy to integrate into your continous integration pipeline with the [vrapio/vrap](https://hub.docker.com/r/vrapio/vrap/) docker image

# How to use it

The easisest way is to start vrap with docker. This requires that you have [docker](https://www.docker.com/) docker installed on your system. When docker is started, you can run vrap with the following command:

```
docker run -v<RAML-definition-directory>:/api -p5050:5050 vrapio/vrap /api/api.raml 
```

## Vrap mode

Vrap provides two modes of operation, which can be set globally on the command line (via the *-m mode* cli option) or set for a request with the *X-Vrap-Mode* http header.
 
- **example** mode sends the examples for a request as given in the RAML definition
- **proxy** mode proxies requests to the base uri as given in the RAML definition
    
## Http headers

The behaviour of vrap can be controlled by adding the following headers to the request:

- *X-Vrap-Mode* sets the vrap mode for the request

	Value    | Description
	---------|--------
	*example*| sends the raml definition example that matches the request
	*proxy*  | proxy the request to the base uri given in the raml definition

- *X-Vrap-Disable-Validation* disables the validation. You can set multiple values for this header:

	Value           | Description
	----------------|--------
	*request*       | disable request body validation
	*queryParameter*| disable query parameter validation
	*header*        | disable request header validation
	*response*      | disable received response body validation


## Command line options

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

# Source code

The source code is available from our github repository [vrapio/vrap](https://github.com/vrapio/vrap/), which contains a *Readme.md* file with build instructions.
	
