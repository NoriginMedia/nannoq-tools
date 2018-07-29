# Welcome to Nannoq Tools

[![Build Status](https://www.tomrom.net/buildStatus/icon?job=nannoq-tools/master)](https://www.tomrom.net/job/nannoq-tools/job/master/)

Table of Contents
=================

   * [Welcome to Nannoq Tools](#welcome-to-nannoq-tools)
      * [Modules](#modules)
         * [Prerequisites](#prerequisites)
      * [Installing](#installing)
         * [Running the tests](#running-the-tests)
         * [Running the integration tests](#running-the-integration-tests)
      * [Usage](#usage)
         * [Implementation and Use](#implementation-and-use)
      * [Contributing](#contributing)
      * [Versioning](#versioning)
      * [Authors](#authors)
      * [License](#license)
      
## Modules

[Auth](https://noriginmedia.github.io/nannoq-tools/auth)

```
auth is a collection of classes for managing JWT based authentication and authorization on a vertx environment.

It supports:
 - JWT Creation
 - JWT Verification
 - JWT Revocation
 - OAuth Login
 - Direct Token Conversion
 
Current providers:
 
 - Facebook
 - Google
 - Instagram
```

[Cluster](https://noriginmedia.github.io/nannoq-tools/cluster)

```
cluster is a collection of classes for managing services and API's in clustered Vert.x environments as well as helpers for clustering.

Main features:
 - ServiceManager (Manage services and API's easily across a vertx cluster)
 - ApiManager (Creates Http Records for the ServiceManager)

```

[Firebase Cloud Messaging (FCM)](https://noriginmedia.github.io/nannoq-tools/fcm)

```
fcm is a XMPP server implementation for use with Firebase Cloud Messaging with all features, for a Vert.x environment.

It supports:
 - Topics
 - Direct Messages (Down and Upstream)
 - Device Groups
 - Device Registrations
```

[Repository](https://noriginmedia.github.io/nannoq-tools/repository)

```
repository is a collection of repository implementations for Vert.x. All repositories operate with a unified querying interface that abstracts away the underlying data store. Individual implementations will be extended for any data store specific functionality that is not reasonable to abstract away.

It supports:
 - Caching, extends to clustered caching with JCache if vertx is clustered.
 - Will refresh ETags if supplied an EtagManager implementation.
 
Operations:
 - Batch create/read/update/delete
 - Queries
   * Filtering
   * Ordering
   * Projections
   * Aggregations
```

[Web](https://noriginmedia.github.io/nannoq-tools/web)

```
web is a REST (Level 3) controller implementation that is based on vertx-web and leverages [nannoq-repository](https://github.com/NoriginMedia/nannoq-repository) for data store access.

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
```

### Prerequisites

Vert.x >= 3.5.3

Java >= 1.8

Kotlin

## Installing

./gradlew install

### Running the tests

./gradlew test

### Running the integration tests

./gradlew verify

## Usage

First install with either Maven:

```xml
<dependency>
    <groupId>com.nannoq</groupId>
    <artifactId>moduleToImport</artifactId>
    <version>1.0.8</version>
</dependency>
```

or Gradle:

```groovy
dependencies {
    compile group: 'nannoq.com:moduleToImport:1.0.8’
}
```

### Implementation and Use

Please consult the [GitHub Pages](https://noriginmedia.github.io/nannoq-tools/) on implementations and use.

## Contributing

Please read [CONTRIBUTING.md](https://github.com/NoriginMedia/nannoq-tools/blob/master/CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.

## Versioning

We use [SemVer](http://semver.org/) for versioning. For the versions available, see the [tags on this repository](https://github.com/NoriginMedia/nannoq-tools/tags)

## Authors

* **Anders Mikkelsen** - *Initial work* - [Norigin Media](http://noriginmedia.com/)

See also the list of [contributors](https://github.com/NoriginMedia/nannoq-tools/contributors) who participated in this project.

## License

This project is licensed under the MIT License - see the [LICENSE.md](https://github.com/NoriginMedia/nannoq-tools/blob/master/LICENSE) file for details
