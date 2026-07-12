#!/bin/bash
# RacingManager portable launcher
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAVA_HOME="${SCRIPT_DIR}/jre"
JAR="${SCRIPT_DIR}/lib/racingmanager-backend-*-fat.jar"

# Use bundled JRE if available, otherwise system java
if [ -d "$JAVA_HOME" ]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA="java"
fi

exec "$JAVA" \
    -Dracingmanager.profile=prod \
    -jar "$JAR" \
    "$@"
