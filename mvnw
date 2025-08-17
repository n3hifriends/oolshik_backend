#!/bin/sh
JAR_PATH=".mvn/wrapper/maven-wrapper.jar"
WRAPPER_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"
if [ ! -f "$JAR_PATH" ]; then
  mkdir -p .mvn/wrapper
  echo "Downloading Maven Wrapper..."
  curl -fsSL "$WRAPPER_URL" -o "$JAR_PATH" || wget -q "$WRAPPER_URL" -O "$JAR_PATH"
fi
JAVA_EXEC="java"
exec "$JAVA_EXEC" -classpath "$JAR_PATH" -Dmaven.multiModuleProjectDirectory="$PWD"   org.apache.maven.wrapper.MavenWrapperMain "$@"
