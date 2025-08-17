@echo off
set JAR_PATH=.mvn\wrapper\maven-wrapper.jar
set WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar
if not exist %JAR_PATH% (
  mkdir .mvn\wrapper
  powershell -Command "Invoke-WebRequest -Uri %WRAPPER_URL% -OutFile %JAR_PATH%"
)
set JAVA_EXEC=java
%JAVA_EXEC% -classpath %JAR_PATH% -Dmaven.multiModuleProjectDirectory=%CD% org.apache.maven.wrapper.MavenWrapperMain %*
