#!/bin/sh

#
# Gradle wrapper script
#

APP_BASE_NAME=$(basename "$0")
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

JAVA_HOME="${JAVA_HOME:-}"
if [ -z "$JAVA_HOME" ]; then
    JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(which java 2>/dev/null || echo /usr/bin/java)")")")
fi

JAVA_OPTS="${JAVA_OPTS:-} -Xmx64m -Xms64m"

exec "$JAVA_HOME/bin/java" $JAVA_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
