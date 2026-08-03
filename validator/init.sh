#!/usr/bin/env sh
set -eu

mvn --batch-mode org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file \
  -Dfile=./lib/kodkod.jar \
  -DgroupId=kodkod \
  -DartifactId=kodkod \
  -Dversion=1.0 \
  -Dpackaging=jar
