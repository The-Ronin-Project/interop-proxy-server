[![codecov](https://codecov.io/gh/projectronin/interop-proxy-server/branch/master/graph/badge.svg?token=6066BAwJYk)](https://app.codecov.io/gh/projectronin/interop-proxy-server/branch/master)
[![Tests](https://github.com/projectronin/interop-proxy-server/actions/workflows/test.yml/badge.svg)](https://github.com/projectronin/interop-proxy-server/actions/workflows/test.yml)
[![Lint](https://github.com/projectronin/interop-proxy-server/actions/workflows/lint.yml/badge.svg)](https://github.com/projectronin/interop-proxy-server/actions/workflows/lint.yml)

# interop-proxy-server

A GraphQL API server for retrieving data. This server uses [Spring Boot](https://spring.io/projects/spring-boot)
and [GraphQL Kotlin](https://opensource.expediagroup.com/graphql-kotlin/docs/) to dynamically generate a GraphQL API
based off the defined Queries, Mutations and model classes.

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

### Running integration tests

First, the sandbox key has to be set as an environment variable named AO_SANDBOX_KEY. On Github, this is already taken
care of, but it'll need to be set on your local system before you can successfully run the tests. Assuming you're
running on a Mac, go to your home directory and look for a hidden file called .zshenv, and if it doesn't exist, create
it. Then, add the following line to the file, replacing KEY_VALUE with whatever the key is.

```shell
export AO_SANDBOX_KEY=KEY_VALUE
```

The key can be found in the 1Password Interoperability vault under "Epic AO Sandbox Private Key (pkcs8)". Make sure to
remove any newlines or comments from the key and save the file.

Next, restart IntelliJ to let it pick up the change. To make sure it has, open the Terminal tab at the bottom and enter
the command "echo $AO_SANDBOX_KEY" and it should print the value of the key.

Finally, you can start running the integration tests. From the command line, run:

```shell
./gradlew it
```

From within IntelliJ, integration tests are stored in src/it. Open one of them up and click the green arrow to run it,
similar to a unit test.

### Running locally

Running locally builds on the configuration used for running integration tests, so ensure that the `AO_SANDBOX_KEY` is
properly configured in your environment.

From the command line, running the following command will start the server at http://localhost:8080/graphql:

```shell
./gradlew bootRun
````

Note that the process will stay alive in your terminal until you kill it. It is recommended to run it directly from your
Mac terminal, where you can use `Command + .` to kill the process.

If port 8080 is being occupied on your machine, you can also start the server using this command, supplying your
preferred port number:

```shell
./gradlew bootRun --args='--spring.profiles.active=it --server.port=[PORT]'
```

Due to the way the arguments work, note that we do need to tell this command to use our "it" profile that is usually
auto-defaulted by bootRun.
