# Nannoq Web

[![Build Status](https://www.tomrom.net/buildStatus/icon?job=nannoq-web/develop)](https://www.tomrom.net/job/nannoq-web/job/develop/)

nannoq-web is a REST (Level 3) controller implementation that is based on vertx-web and leverages [nannoq-repository](https://github.com/NoriginMedia/nannoq-repository) for data store access.

It incorporates:
 - ETag
 - Clustered Caching through nannoq-repository

It supports:
 - Filtering
 - Ordering
 - Projections
 - Grouping
 - Aggregations
 - Cross-Model Aggregations

## Prerequisites

Vert.x >= 3.5.0

Java >= 1.8

Maven

[nannoq-repository](https://github.com/NoriginMedia/nannoq-repository)

## Installing

mvn clean package -Dgpg.skip=true

### Running the tests

mvn clean test -Dgpg.skip=true

### Running the integration tests

mvn clean verify -Dgpg.skip=true

## Usage

First install with either Maven:

```xml
<repositories>
    <repository>
        <id>ossrh</id>
        <name>OSSRH Snapshots</name>
        <url>https://oss.sonatype.org/content/repositories/snapshots</url>

        <releases>
            <enabled>false</enabled>
        </releases>

        <snapshots>
            <enabled>true</enabled>
            <updatePolicy>always</updatePolicy>
            <checksumPolicy>fail</checksumPolicy>
        </snapshots>
    </repository>
</repositories>

<dependency>
    <groupId>com.nannoq</groupId>
    <artifactId>web</artifactId>
    <version>1.0.5-SNAPSHOT</version>
</dependency>
```

or Gradle:

```groovy
repositories {
    maven { url "http://oss.sonatype.org/content/repositories/snapshots/" }
}

dependencies {
    compile group: 'nannoq.com:web:1.0.4-SNAPSHOT'
}
```

### Implementation and Querying

Please consult the [Wiki](https://github.com/NoriginMedia/nannoq-web/wiki) for guides on implementations and queries on the controller.

## Contributing

Please read [CONTRIBUTING.md](https://github.com/NoriginMedia/nannoq-web/blob/master/CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.

## Versioning

We use [SemVer](http://semver.org/) for versioning. For the versions available, see the [tags on this repository](https://github.com/NoriginMedia/nannoq-web/tags)

## Authors

* **Anders Mikkelsen** - *Initial work* - [Norigin Media](http://noriginmedia.com/)

See also the list of [contributors](https://github.com/NoriginMedia/nannoq-web/contributors) who participated in this project.

## License

This project is licensed under the MIT License - see the [LICENSE.md](https://github.com/NoriginMedia/nannoq-web/blob/master/LICENSE) file for details
