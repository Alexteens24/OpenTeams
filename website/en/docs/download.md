# Download and build

OpenTeams has no stable binary release yet. During indev, build the exact source commit you want to evaluate.

## Build requirements

- Git
- JDK 25 for the Gradle toolchain
- Access to Maven Central and the PaperMC repository

Source is compiled with `--release 21`, so the deployable JAR runs on Java 21 or newer.

## Clone and build

```bash
git clone https://github.com/Alexteens24/OpenTeams.git
cd OpenTeams
git checkout <commit-sha>
./gradlew clean build
```

The deployable shaded artifact is:

```text
openteams-core/build/libs/openteams-core-1.0.0-SNAPSHOT.jar
```

JARs from `openteams-api`, `openteams-dialog-ui`, `openteams-test-kit`, and the example addon do not replace the Core JAR on a server.

## Verify the artifact

```bash
jar tf openteams-core/build/libs/openteams-core-*.jar | grep plugin.yml
sha256sum openteams-core/build/libs/openteams-core-*.jar
```

It must include `plugin.yml`, `config.yml`, `db/migration/common/V1__core_schema.sql`, Core classes, relocated runtime dependencies, and the Dialog UI classes/message bundles.

Run all tests with `./gradlew test`. Addon developers can build `:openteams-api` and `:openteams-example-addon` separately; the example addon is the current lifecycle/public-type compatibility fixture.

## Safe updates

Read [Release status](./release-status), stop the server, back up the database and plugin directory, replace the Core JAR, restart, wait for `WRITABLE`, and run `/teamadmin doctor`. Never use PlugMan; see the [Production runbook](./operations).
