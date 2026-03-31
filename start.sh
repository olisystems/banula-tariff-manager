#!/usr/bin/env bash

source "$(dirname "$0")/../transit-local-services/scripts/customTerminal.sh"

PORT=$(cat local/port.md | head -n 1 | tr -d '[:space:]')
API_NAME="banula-tariff-manager:$PORT"
LANG="java"   # only for color (optional)

custom_terminal_setup "$API_NAME" "$LANG"

# Set Java Home
eval export JAVA_HOME=$(cat local/javaHome.md | head -n 1 | tr -d '[:space:]')

# Set timezone and Spring profiles
export MAVEN_OPTS="-Duser.timezone=UTC"

# Start the application with Maven
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local,local-custom"
