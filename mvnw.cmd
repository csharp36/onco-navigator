@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM
@REM Required ENV vars:
@REM JAVA_HOME - location of a JDK home dir
@REM
@REM Optional ENV vars
@REM MAVEN_BATCH_ECHO - if set to 'on' then the mvnw batch will echo commands
@REM MAVEN_BATCH_PAUSE - if set to 'on' then the mvnw batch will pause at end
@REM MAVEN_OPTS - parameters passed to the Java VM when running Maven
@REM   e.g. to debug Maven itself, use
@REM      set MAVEN_OPTS=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000
@REM MAVEN_SKIP_RC - flag to disable loading of mavenrc files
@REM ----------------------------------------------------------------------------

@IF "%MAVEN_BATCH_ECHO%" == "on"  ECHO ON

@REM Set the local variable WRAPPER_JAR
SET WRAPPER_JAR="%~dp0.mvn\wrapper\maven-wrapper.jar"

@REM Determine the Maven distribution
@IF NOT EXIST "%~dp0.mvn\wrapper\maven-wrapper.properties" GOTO error
@FOR /F "tokens=2 delims==" %%G IN ('FINDSTR /i "distributionUrl" "%~dp0.mvn\wrapper\maven-wrapper.properties"') DO (
    SET distributionUrl=%%G
)

@SET MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.9-bin\apache-maven-3.9.9

@IF EXIST "%MAVEN_HOME%\bin\mvn.cmd" GOTO run

@ECHO Downloading Apache Maven 3.9.9...
@MKDIR "%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.9-bin" 2>NUL
@powershell -Command "(New-Object System.Net.WebClient).DownloadFile('https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip', '%TEMP%\maven.zip')"
@powershell -Command "Expand-Archive '%TEMP%\maven.zip' '%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.9-bin'"
@DEL /Q "%TEMP%\maven.zip"

:run
@"%MAVEN_HOME%\bin\mvn.cmd" %MAVEN_OPTS% %*
@GOTO end

:error
@ECHO maven-wrapper.properties not found
@EXIT /B 1

:end
