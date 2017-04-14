# Tutorial

We start vrap with the raml definition file for https://restcountries.eu/rest/v2 which is available from our github repository https://github.com/vrapio/vrap/blob/master/examples/REST-Countries.raml

We use curl to download the raml file to the current directory

```
curl https://raw.githubusercontent.com/vrapio/vrap/master/examples/REST-Countries.raml > REST-countries.raml
```

Then we start vrap with the following docker command:

```
docker run -v$(pwd):/api -p5050:5050 vrapio/vrap /api/REST-countries.raml
```

Then open a browser for the vrap ui http://localhost:5050 