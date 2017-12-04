## Welcome to Nannoq Tools

This repo is a collection of the most current version of all Nannoq Tools.

### Prerequisites

Vert.x >= 3.5.0

Java >= 1.8

Maven

### Installing

mvn clean package -Dgpg.skip=true

## Running the tests

mvn clean test -Dgpg.skip=true

## Running the integration tests

mvn clean verify -Dgpg.skip=true
