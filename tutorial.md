# Tutorial

We start vrap with the raml definition file for [https://restcountries.eu/rest/v2](https://restcountries.eu/rest/v2) which is available from our github repository [https://github.com/vrapio/vrap/blob/master/examples/REST-Countries.raml](https://github.com/vrapio/vrap/blob/master/examples/REST-Countries.raml) 

## Download the raml definition

- Use your browser to download the file: [https://raw.githubusercontent.com/vrapio/vrap/master/examples/REST-Countries.raml](https://raw.githubusercontent.com/vrapio/vrap/master/examples/REST-Countries.raml) 
- Or use curl to download the raml file into the current directory
```
curl https://raw.githubusercontent.com/vrapio/vrap/master/examples/REST-Countries.raml > REST-countries.raml
```

## Start vrap with docker

- Start vrap with the following docker command:
```
docker run -v$(pwd):/api -p5050:5050 vrapio/vrap /api/REST-countries.raml
```

## Browsing and testing the raml definition

- open a browser to access the vrap ui [http://localhost:5050](http://localhost:5050)  