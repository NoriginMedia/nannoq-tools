# Nannoq Cluster

[![Build Status](https://www.tomrom.net/buildStatus/icon?job=nannoq-cluster/develop)](https://www.tomrom.net/job/nannoq-cluster/job/develop/)

nannoq-cluster is a collection of classes for managing services and API's in clustered Vert.x environments as well as helpers for clustering.

Main features:
 - ServiceManager (Manage services and API's easily across a vertx cluster)
 - ApiManager (Creates Http Records for the ServiceManager)

### Prerequisites

Vert.x >= 3.5.0

Java >= 1.8

Maven

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
    <artifactId>cluster</artifactId>
    <version>1.0.5-SNAPSHOT</version>
</dependency>
```

or Gradle:

```groovy
repositories {
    maven { url "http://oss.sonatype.org/content/repositories/snapshots/" }
}

dependencies {
    compile group: 'nannoq.com:cluster:1.0.4-SNAPSHOT'
}
```

### Implementation and Use

Please consult the [Wiki](https://github.com/NoriginMedia/nannoq-cluster/wiki) for guides on implementations and use.

## Contributing

Please read [CONTRIBUTING.md](https://github.com/NoriginMedia/nannoq-cluster/blob/master/CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.

## Versioning

We use [SemVer](http://semver.org/) for versioning. For the versions available, see the [tags on this repository](https://github.com/NoriginMedia/nannoq-cluster/tags)

## Authors

* **Anders Mikkelsen** - *Initial work* - [Norigin Media](http://noriginmedia.com/)

See also the list of [contributors](https://github.com/NoriginMedia/nannoq-cluster/contributors) who participated in this project.

## License

This project is licensed under the MIT License - see the [LICENSE.md](https://github.com/NoriginMedia/nannoq-cluster/blob/master/LICENSE) file for details
