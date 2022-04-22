[![codecov](https://codecov.io/gh/projectronin/interop-proxy-server/branch/master/graph/badge.svg?token=6066BAwJYk)](https://app.codecov.io/gh/projectronin/interop-proxy-server/branch/master)
[![Tests](https://github.com/projectronin/interop-proxy-server/actions/workflows/test.yml/badge.svg)](https://github.com/projectronin/interop-proxy-server/actions/workflows/test.yml)
[![Integration Tests](https://github.com/projectronin/interop-proxy-server/actions/workflows/integration_test.yml/badge.svg)](https://github.com/projectronin/interop-proxy-server/actions/workflows/integration_test.yml)
[![Lint](https://github.com/projectronin/interop-proxy-server/actions/workflows/lint.yml/badge.svg)](https://github.com/projectronin/interop-proxy-server/actions/workflows/lint.yml)

# interop-proxy-server

A GraphQL API server for retrieving data. This server uses [Spring Boot](https://spring.io/projects/spring-boot)
and [GraphQL Kotlin](https://opensource.expediagroup.com/graphql-kotlin/docs/) to dynamically generate a GraphQL API
based off the defined Queries, Mutations and model classes.

## Consuming Proxy Server

Our graphql schema describing the resources and data can be access here [interopSchema.graphql](interopSchema.graphql).

#### Authentication options

Proxy Server supports two authentication methods currently.

##### User (Seki) authentication

The User Bearer token must be supplied in the request's Authentication header. Any request's tenant mnemonic must match
the tenant authorized for the User.

##### Machine to Machine (Auth0) authentication

For select [calls](interopSchema.graphql), where user authentication is not required, machine to machine authentication
is accepted. A signed Auth0 Bearer token must be supplied in the request's 'Authorization' header. The token must have
been issued, issuer on the JWT, by the auth endpoint corresponding to the environment, the audience must be "proxy", and
the token must not be expired at time of processing.

## Development

### Project Ronin's package repo

Follow the steps [here](https://projectronin.atlassian.net/wiki/spaces/ENG/pages/1645740033/GitHub) to ensure your local
machine can download the Project Ronin's artifacts.

### Generating GraphQL schema

Graphql schema is generated directly from the corresponding Kotlin data classes and persisted
at [interopSchema.graphql](interopSchema.graphql). This is currently done automatically during compile.

### Generating container

This project uses [Jib](https://github.com/GoogleContainerTools/jib) to build containers. This can be used with or
without Docker installed on your system. Currently, we have not assigned a specific name to the created image, so it
will need to be supplied on the command line.

To build with Docker installed, run the following, which will build the server into a Docker repository and make it
available on your local Docker daemon.

```shell
./gradlew jibDockerBuild --image=[IMAGE_NAME]
```

To build without Docker installed, run the following command. This will
require [authentication](https://github.com/GoogleContainerTools/jib/tree/master/jib-gradle-plugin#authentication-methods)
to deploy to the chosen image location. This is not currently enabled for the project.

```shell
./gradlew jib --image=[IMAGE_NAME]
```

## Dependent Configuration

### Authentication Support

#### Sandbox App Orchard (Non-production)

Running integration test and running locally both require the sandbox key has to be set as an environment variable named
AO_SANDBOX_KEY. On Github, this is already taken care of, but it'll need to be set on your local system before you can
successfully run the tests. Assuming you're running on a Mac, go to your home directory and look for a hidden file
called .zshenv, and if it doesn't exist, create it. Then, add the following line to the file, replacing KEY_VALUE with
whatever the key is.

```shell
export AO_SANDBOX_KEY=KEY_VALUE
```

The AO_SANDBOX_KEY can be found in the 1Password Interoperability vault under "Epic AO Sandbox Private Key (pkcs8)".
Make sure to remove any newlines or comments from the key and save the file.

Next, restart IntelliJ to let it pick up the change. To make sure it has, open the Terminal tab at the bottom and enter
the command "echo $AO_SANDBOX_KEY" and it should print the value of the key.

#### User Auth (Seki)

You will also need to add the public JWT secret used by Seki for authentication. The secret is also in 1Password. Search
for 'All Keys', find 'Seki JWT Secret', and copy it.

```shell
export SERVICE_CALL_JWT_SECRET=SECRET_VALUE
```

#### Machine to Machine Auth (Auth0)

Additional configuration is required for supporting Machine to Machine calls, prefixed by "ronin.server.auth.m2m". These
can either be configured via the applications.yaml ([example](resources/application-test.properties)) in the environment
or via the following env variables, both are defaulted to dev values should not need manual configuration while running
locally.

```shell
export RONIN_SERVER_AUTH_M2M_ISSUER=AUTH0_INSTANCE
export RONIN_SERVER_AUTH_M2M_AUDIENCE=PROXY_ENDPOINT
```

### Datasources

This service is also dependent on two independent databases that can be configured by the corresponding spring boot
[data source configuration](https://docs.spring.io/spring-boot/docs/2.1.x/reference/html/howto-data-access.html). These
databases are for the interop-ehr (tenant) and interop-queue services and require the ```spring.ehr.datasource```
and ```spring.ehr.datasource``` prefixes, respectively.

### Aidbox

Proxy server also has a dependency on Aidbox and therefore requires Aidbox credentials. For the moment aidbox is
maintained by the Data Platform team and new clients and permissions are managed manually.

```shell
export AIDBOX_CLIENT_ID=CLIENT_ID
export AIDBOX_CLIENT_SECRET=SECRET_VALUE
```

### DataDog
If you want to send logs to DataDog while running locally for testing, you need to set an API key environment variable.
```shell
export DD_API_KEY=API_KEY
```
Once you have a DataDog account, API keys can be found [here](https://app.datadoghq.com/organization-settings/api-keys).

## Running the Service

### Running integration tests

Finally, you can start running the integration tests. From the command line, run:

```shell
./gradlew it
```

From within IntelliJ, integration tests are stored in src/it. Open one of them up and click the green arrow to run it,
similar to a unit test.

### Running locally via Docker

The proxy server can also be run via docker-compose by building the [container image](#generating-container) from jib in
combination with the [docker compose configuration](docker-compose.yml). Additionally, you will need to have the
interop-queue-liquibase and interop-ehr-liquibase container images for database schema deployment. Follow the
instructions [here](https://github.com/projectronin/interop-queue/tree/master/interop-queue-liquibase/README.md#building-the-docker-container-image)
and [here](https://github.com/projectronin/interop-ehr/tree/master/interop-ehr-liquibase/README.md#building-the-docker-container-image)
to build those images locally. Then use this command to start that container and the other dependent containers:

```shell
./gradlew jibDockerBuild && docker-compose up
```

## Code Review, Codeowners, and the PR process

The current policy is that at least two approving reviews are required from the
[codeowners](CODEOWNERS) for a PR to pass, in addition to status checks for codecov.

In addition, we have set the merge policy to squash commits on a merge to master, and to automatically delete the PR
branch on a successful merge to master.
