# WildRovers Backend

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## Build requirements

Use JDK 25 and the checked-in Maven wrapper (`./mvnw` or `mvnw.cmd` on Windows).
The wrapper pins Maven 3.9.16, whose bundled libraries avoid the deprecated
`sun.misc.Unsafe` calls made by Maven 3.9.9 on this JDK. `.mvn/jvm.config`
enables native access for Maven's Jansi console library, including when Quarkus
launches the wrapper as a subprocess in dev mode.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./mvnw compile quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged using:
```shell script
./mvnw package
```
It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

If you want to build an _über-jar_, execute the following command:
```shell script
./mvnw package -Dquarkus.package.type=uber-jar
```

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

## Creating a native executable

You can create a native executable using:
```shell script
./mvnw package -Pnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:
```shell script
./mvnw package -Pnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/getting-started-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/maven-tooling.html.

## Building the Docker image

The backend ships only as a native executable. From the backend root, build the
executable and package it, which is what the release CI does:

```shell
./mvnw package -Pnative -DskipTests -Dquarkus.native.container-build=true
docker build -f src/main/docker/Dockerfile.native -t wildrovers-backend .
```

Only Docker and a JDK 25 for Maven are required locally. The container build
uses the UBI 9 Mandrel builder configured in the `native` profile, so the
executable links against the same glibc as the UBI 9 runtime image. That image
runs the executable as user 185 and includes the AWT/ImageIO libraries generated
next to it in `target/`, which image uploads need. Supply the same runtime
environment and mounts as `docker-compose.yml`, including `/work/uploads`,
`/work/logs` and keys.

Native compilation needs several GB of memory; set the native-image heap with
`-Dquarkus.native.native-image-xmx=6g` if the default is too small.

To build without a local JDK, `src/main/docker/Dockerfile.native-multistage`
compiles and packages in one Docker build, using its own source allowlist in
`Dockerfile.native-multistage.dockerignore` so local credentials stay out of the
build context:

```shell
docker build -f src/main/docker/Dockerfile.native-multistage -t wildrovers-backend .
```

The GitHub Actions workflow `.github/workflows/build-native.yml` builds the
native executable and publishes the Linux AMD64 image when a tag points to a
commit on `master`, as `ghcr.io/voxel07/wildrovers-backend:<tag>` and
`ghcr.io/voxel07/wildrovers-backend:latest`.

# About this Project
This is the backend for the www.wild-rovers.de homepage. It provides bunch of Rest endpoinst witch are secured by a role based system.

## Functinality
### User Management
### Forum

## Model Structure
The models serve multiple purposes thats why they are structured the following way
```
    @NotBlank(message = "The Password must not be blank or empty") //Hybernate validator
    @Length(min = 3, max=5)

    @Password //Quarkus Security

    @Column(name = "password") //SQL ORM Mapping
    private String password;
```

# Upload storage

See [upload limits, backups and recovery](docs/uploads-backup.md). Apply
`migrations/2026-10-22_review_low_fixes.sql` after the medium-fixes migration and
before deploying this backend: answer `editDate` is now epoch milliseconds and
the unused `FORUM_PICTURE` table is archived and retired. Set
`IMMICH_ALLOWED_HOST` to restrict album links to the exact Immich hostname.

