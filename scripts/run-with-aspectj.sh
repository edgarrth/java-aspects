#!/usr/bin/env bash
set -euo pipefail
VERSION="1.9.25.1"
JAR="$HOME/.m2/repository/org/aspectj/aspectjweaver/$VERSION/aspectjweaver-$VERSION.jar"
APP="target/payment-aspectj-poc-1.0.0-SNAPSHOT.jar"
if [[ ! -f "$JAR" || ! -f "$APP" ]]; then
  echo "Primero ejecuta: mvn clean verify"
  exit 1
fi
exec java -javaagent:"$JAR" -jar "$APP"
