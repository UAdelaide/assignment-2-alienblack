#!/usr/bin/env bash
set -euo pipefail

JUNIT="${JUNIT:-$HOME/.m2/repository/junit/junit/4.13.2/junit-4.13.2.jar}"
HAM="${HAM:-$HOME/.m2/repository/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar}"
[ -f "$JUNIT" ] || mvn -q dependency:get -Dartifact=junit:junit:4.13.2
[ -f "$HAM" ]   || mvn -q dependency:get -Dartifact=org.hamcrest:hamcrest-core:1.3

# Clean stale classes from IntelliJ/out
rm -rf out
mkdir -p out

# Compile all sources
find src -name '*.java' > sources.txt
javac -source 11 -target 11 -cp "$JUNIT:$HAM" -d out @sources.txt

# Discover default-package tests only at top level of out
TESTS=$(find out -maxdepth 1 -type f -name '*_Test.class' -exec basename {} .class \;)
if [ -z "$TESTS" ]; then
  echo "No tests found"
  exit 0
fi

java -cp "out:$JUNIT:$HAM" org.junit.runner.JUnitCore $TESTS
