# Deploy with Docker

## Prerequisites

Docker provides a way to run applications securely isolated in a container, packaged with all its dependencies and libraries.
To learn more on Docker, please see [Docker overview](https://docs.docker.com/engine/docker-overview/).

Make sure that you have the latest version of Docker installed on your machine. [Get latest version](https://www.docker.com/products/overview#/install_the_platform)

> **WSL**: Make sure to use [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/). Installing Docker in Ubuntu will not work.

[Notes for non-Linux systems](notes-for-non-linux.md)

## Usage instructions
In this example we use [Docker Compose](https://docs.docker.com/compose/) to spin up all the different required containers/services for cBioPortal.

### Quick Start
Important when using Windows:<br/>
Please use the WSL input window (WSL2 Terminal) directly and not "Windows PowerShell" or "Git Bash".
```
git clone https://github.com/cBioPortal/cbioportal-docker-compose.git
cd cbioportal-docker-compose
./init.sh
docker compose up
```

You should now be able to see the cBioPortal website at http://localhost:8080

Import studies with:<br/>(Hint: Don't close the current terminal but open a second terminal and change the path to "/cbioportal-docker-compose")

```
docker compose run cbioportal metaImport.py -u http://cbioportal:8080 -s study/lgg_ucsf_2014/ -o
```

Clear persistent data volumes with:

```
docker compose down -v
```

### Comprehensive Start

#### Step 1 - Run Docker Compose

Download the git repo that has the Docker compose file and go to the root of that folder:

```
git clone https://github.com/cBioPortal/cbioportal-docker-compose.git
cd cbioportal-docker-compose
```

Then download all necessary files (seed data, example config and example study from datahub) with the init script:
```
./init.sh
```

Then run:

```
docker compose up
```

This will start all four containers (services) defined [here](https://github.com/cBioPortal/cbioportal-docker-compose/blob/master/docker-compose.yml). That is:

- the mysql database, which holds most of the cBioPortal data
- the cBioPortal Java web app, this serves the React frontend as well as the REST API
- the session service Java web app. This service has a REST API and stores session information (e.g. what genes are being queried) and user specific data (e.g. saved cohorts) in a separate mongo database
- the mongo database that persists the data for the session service

It will take a few minutes the first time to import the seed database and perform migrations if necessary. Each container outputs logs to the terminal. For each log you'll see the name of the container that outputs it (e.g. `cbioportal_container` or `cbioportal_session_database_container`). If all is well you won't see any significant errors (maybe some warnings, that's fine to ignore). If all went well you should be able to visit the cBioPortal homepage on http://localhost:8080. You'll notice that no studies are shown on the homepage yet:

<img width="1414" alt="Screen Shot 2022-01-24 at 2 10 10 PM" src="https://user-images.githubusercontent.com/1334004/150848276-dec9551f-6b90-470f-bb59-e7754829fc83.png">


Go to the next step to see how to import studies.

##### Notes on detached mode

If you prefer to run the services in detached mode (i.e. not logging everything to your terminal), you can run

```
docker compose up -d
```

In this mode, you'll have to check the logs of each container manually using e.g.:

```
docker logs -f cbioportal_container
```

You can list all containers running on your system with

```
docker ps -a
```

To shut down the containers:

```
docker compose down
```

> **Tip:** If you are using [Docker Desktop](https://www.docker.com/products/docker-desktop/), detached mode is
the preferred way to run as it provides a UI for listing the containers and interfacing with them.

#### Step 2 - Import Studies
To import studies you can run:

```
docker compose run cbioportal metaImport.py -u http://cbioportal:8080 -s study/lgg_ucsf_2014/ -o
```
This will import the [lgg_ucsf_2014 study](https://www.cbioportal.org/patient?studyId=lgg_ucsf_2014) into your local database. It will take a few minutes to import. After importing, restart the cbioportal web container:

```
docker compose restart cbioportal
```

or 

All public studies can be downloaded from https://www.cbioportal.org/datasets, or https://github.com/cBioPortal/datahub/. You can add any of them to the `./study` folder and import them. There's also a script (`./study/init.sh`) to download multiple studies. You can set `DATAHUB_STUDIES` to any public study id (e.g. `lgg_ucsf_2014`) and run `./init.sh`.

##### Notes on restarting
To avoid having to restart one can alternatively hit an API endpoint. To do so, call the `/api/cache` endpoint with a `DELETE` http-request (see [here](/deployment/customization/application.properties-Reference.md#evict-caches-with-the-apicache-endpoint) for more information):

```
curl -x DELETE -H "X-API-KEY: my-secret-api-key-value" http://localhost:8080/api/cache
```

The value of the API key is configured in the _application.properties_ file. You can visit http://localhost:8080 again and you should be able to see the new study.

#### Step 3 - Customize your application.properties file ###

The properties file can be found in `./config/application.properties`. Which was set up when running `init.sh`.

This properties file allows you to customize your instance of cBioPortal with e.g. custom logos, or point the cBioPortal container to e.g. use an external mysql database. See the [properties](/deployment/customization/Customizing-your-instance-of-cBioPortal.md) documentation for a comprehensive overview.

If you would like to enable OncoKB see [OncoKB data access](/deployment/integration-with-other-webservices/OncoKB-Data-Access.md) for 
how to obtain a data access token. After obtaining a valid token use:

#### Step 4 - Customize cBioPortal setup
To read more about the various ways to use authentication and parameters for running the cBioPortal web app see the relevant [backend deployment documentation](/deployment/customization/Customizing-your-instance-of-cBioPortal.md).

On server systems that can easily spare 4 GiB or more of memory, set the `-Xms`
and `-Xmx` options to the same number. This should increase performance of
certain memory-intensive web services such as computing the data for the
co-expression tab. If you are using MacOS or Windows, make sure to take a look
at [these notes](notes-for-non-linux.md) to allocate more memory for the
virtual machine in which all Docker processes are running.

## More commands ##
For documentation on how to import a study, see [this tutorial](import_data.md)
For more uses of the cBioPortal image, see [this file](example_commands.md)

To Dockerize a Keycloak authentication service alongside cBioPortal,
see [this file](using-keycloak.md).

## Building cBioPortal 

If you need to build the cBioPortal backend to a docker image, you can use the following command from the cbioportal repo. 

```
docker build -t cbioportal/cbioportal:my-dev-cbioportal-image -f docker/web-and-data/Dockerfile .
```

Change the [env file](https://github.com/cBioPortal/cbioportal-docker-compose/blob/master/.env) in the cbioportal-docker-compose repo to reference your image.

```
DOCKER_IMAGE_CBIOPORTAL=cbioportal/cbioportal:my-dev-cbioportal-image
```

Note that the above command will deploy the repo as loose files. If you would like to build to a single executable app.jar, you can use the **web** configuration:

```
docker build -t cbioportal/cbioportal:my-dev-cbioportal-image -f docker/web/Dockerfile .
```

In this case, the default [docker-compose.yml](https://github.com/cBioPortal/cbioportal-docker-compose/blob/master/docker-compose.yml) may not work. If you get an error about missing _PortalApplication_, it must be configured to launch the app.jar instead. 

```
java -Xms2g -Xmx4g -jar /cbioportal-webapp/app.jar -spring...
```

There may be a [docker-compose.web.yml](https://github.com/cBioPortal/cbioportal-docker-compose/blob/master/docker-compose.web.yml) you can use instead of modifying the above.

```
docker compose -f docker-compose.web.yml up -d
```


## Uninstalling cBioPortal ##
```
docker compose down -v --rmi all
```
