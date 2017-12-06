## Welcome to Nannoq Tools

This repo is a collection of the most current version of all Nannoq Tools.

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

```
<dependency>
    <groupId>com.nannoq</groupId>
    <artifactId>tools</artifactId>
    <version>1.0.0</version>
</dependency>
```

or Gradle:

```
dependencies {
    compile group: 'nannoq.com:tools:1.0.0'
}
```

## Contributing

Please read [CONTRIBUTING.md](https://github.com/mikand13/nannoq-tools/blob/master/CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.

## Versioning

We use [SemVer](http://semver.org/) for versioning. For the versions available, see the [tags on this repository](https://github.com/mikand13/nannoq-tools/tags)

## Authors

* **Anders Mikkelsen** - *Initial work* - [Norigin Media](http://noriginmedia.com/)

See also the list of [contributors](https://github.com/mikand13/nannoq-tools/contributors) who participated in this project.

## License

This project is licensed under the MIT License - see the [LICENSE.md](https://github.com/mikand13/nannoq-tools/blob/master/LICENSE) file for details
