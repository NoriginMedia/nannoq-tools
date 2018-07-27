## Welcome to Nannoq Tools

[![Build Status](https://www.tomrom.net/buildStatus/icon?job=nannoq-tools/master)](https://www.tomrom.net/job/nannoq-tools/job/master/)

This repo is a collection of the most current version of all Nannoq Tools.

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
    <artifactId>tools</artifactId>
    <version>1.0.7</version>
</dependency>
```

or Gradle:

```groovy
dependencies {
    compile group: 'nannoq.com:tools:1.0.7’
}
```

### Implementation and Use

Please consult the individual modules on implementations and use, this is just a parent project.

## Contributing

Please read [CONTRIBUTING.md](https://github.com/NoriginMedia/nannoq-tools/blob/master/CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.

## Versioning

We use [SemVer](http://semver.org/) for versioning. For the versions available, see the [tags on this repository](https://github.com/NoriginMedia/nannoq-tools/tags)

## Authors

* **Anders Mikkelsen** - *Initial work* - [Norigin Media](http://noriginmedia.com/)

See also the list of [contributors](https://github.com/NoriginMedia/nannoq-tools/contributors) who participated in this project.

## License

This project is licensed under the MIT License - see the [LICENSE.md](https://github.com/NoriginMedia/nannoq-tools/blob/master/LICENSE) file for details
