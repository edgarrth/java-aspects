#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
export JAVA_HOME

VERSION="1.9.25.1"
JAR="$HOME/.m2/repository/org/aspectj/aspectjweaver/$VERSION/aspectjweaver-$VERSION.jar"
APP="target/payment-aspectj-poc-1.0.0-SNAPSHOT.jar"

mvn package

if [[ ! -f "$JAR" ]]; then
  echo "No se encontró el agente AspectJ en $JAR" >&2
  exit 1
fi
exec java -javaagent:"$JAR" -jar "$APP"
