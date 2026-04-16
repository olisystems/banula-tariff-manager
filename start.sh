#!/usr/bin/env bash

CUSTOM_TERMINAL_SCRIPT="$(dirname "$0")/../transit-local-services/scripts/customTerminal.sh"
if [ -f "$CUSTOM_TERMINAL_SCRIPT" ]; then
  source "$CUSTOM_TERMINAL_SCRIPT"
fi

if [ ! -f "$(dirname "$0")/local/port.md" ]; then
  echo "WARNING: local/port.md not found. Please create it with the service port number."
  exit 1
fi

PORT=$(cat local/port.md | head -n 1 | tr -d '[:space:]')
API_NAME="banula-tariff-manager:$PORT"
LANG="java"   # only for color (optional)

if command -v custom_terminal_setup &> /dev/null; then
  custom_terminal_setup "$API_NAME" "$LANG"
fi

# Set Java Home
if [ ! -f "$(dirname "$0")/local/javaHome.md" ]; then
  echo "WARNING: local/javaHome.md not found. Please create it with the path to your Java installation."
  exit 1
fi
eval export JAVA_HOME=$(cat local/javaHome.md | head -n 1 | tr -d '[:space:]')

# Set timezone and Spring profiles
export MAVEN_OPTS="-Duser.timezone=UTC"

# Start the application with Maven
if [ -x "./mvnw" ]; then
  ./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local,local-custom"
else
  mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local,local-custom"
fi
