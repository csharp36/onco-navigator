#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script, version 3.3.2
#
# Required ENV vars:
# ------------------
#   JAVA_HOME - location of a JDK home dir
#
# Optional ENV vars
# -----------------
#   MAVEN_OPTS - parameters passed to the Java VM when running Maven
#     e.g. to debug Maven itself, use
#       set MAVEN_OPTS=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000
#   MAVEN_SKIP_RC - flag to disable loading of mavenrc files
# ----------------------------------------------------------------------------

if [ -z "$MAVEN_SKIP_RC" ]; then
  if [ -f /usr/local/etc/mavenrc ]; then
    . /usr/local/etc/mavenrc
  fi
  if [ -f /etc/mavenrc ]; then
    . /etc/mavenrc
  fi
  if [ -f "$HOME/.mavenrc" ]; then
    . "$HOME/.mavenrc"
  fi
fi

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false
darwin=false
mingw=false
case "$(uname)" in
  CYGWIN*) cygwin=true ;;
  MINGW*) mingw=true ;;
  Darwin*) darwin=true ;;
esac

# Attempt to find $JAVA_HOME
if [ -z "$JAVA_HOME" ]; then
  if $darwin; then
    if [ -x '/usr/libexec/java_home' ]; then
      export JAVA_HOME="$(/usr/libexec/java_home)"
    elif [ -d "/System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home" ]; then
      export JAVA_HOME="/System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home"
    fi
  fi
fi

if [ -z "$JAVA_HOME" ]; then
  if [ -r /etc/gentoo-release ]; then
    JAVA_HOME=$(java-config --jre-home)
  fi
fi

if [ -z "$JAVA_HOME" ]; then
  # resolve links - $0 may be a symbolic link
  PRG="$0"
  while [ -h "$PRG" ]; do
    ls=$(ls -ld "$PRG")
    link=$(expr "$ls" : '.*-> \(.*\)$')
    if expr "$link" : '/.*' > /dev/null; then
      PRG="$link"
    else
      PRG="$(dirname "$PRG")/$link"
    fi
  done
  saveddir="$(pwd)"
  M2_HOME=$(dirname "$(dirname "$(readlink -f "$PRG")")")
  cd "$saveddir"
fi

# For cygwin, we need to set paths to the right format.
if $cygwin; then
  M2_HOME=$(cygpath --unix "$M2_HOME")
fi

if [ -z "$M2_HOME" ]; then
  M2_HOME="$HOME/.m2"
fi

MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$(pwd)}"
MAVEN_OPTS="${MAVEN_OPTS:-}"

find_maven_basedir() {
  basedir="$(find_maven_basedir_base "$1")"
  echo "$basedir"
}

find_maven_basedir_base() {
  wdir="$1"
  while [ "$wdir" != '/' ]; do
    if [ -d "$wdir"/.mvn ]; then
      echo "$wdir"
      return
    fi
    wdir=$(cd "$wdir/.." && pwd)
  done
  echo "$1"
}

MAVEN_PROJECTBASEDIR="$(find_maven_basedir "$(pwd)")"

# Find the mvn wrapper jar
WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"

# Prefer to use Maven's own downloaded copy of itself
WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"
if [ -r "$WRAPPER_PROPERTIES" ]; then
  distributionUrl=$(grep "^distributionUrl" "$WRAPPER_PROPERTIES" | cut -d'=' -f2-)
else
  distributionUrl="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip"
fi

MAVEN_WRAPPER_DOWNLOADER_OPTS="--download-if-necessary"
if [ "$distributionUrl" ]; then
  WRAPPER_DOWNLOAD_URL="$distributionUrl"
fi

MAVEN_DISTRIBUTION="$(basename "${distributionUrl%%-bin.zip}")"
MAVEN_HOME="${M2_HOME}/wrapper/dists/${MAVEN_DISTRIBUTION}-bin/${MAVEN_DISTRIBUTION}"

if [ -f "$MAVEN_HOME/bin/mvn" ]; then
  MVNW_VERBOSE="${MVNW_VERBOSE:-false}"
  if [ "$MVNW_VERBOSE" = "true" ]; then
    echo "Found $MVNW_VERBOSE in $MAVEN_HOME"
  fi
else
  echo "Downloading Apache Maven $MAVEN_DISTRIBUTION..."
  # Download Maven
  MAVEN_DOWNLOAD_URL="${distributionUrl}"
  MAVEN_ZIP="${MAVEN_HOME}.zip"
  mkdir -p "$(dirname "${MAVEN_HOME}")"
  if command -v curl > /dev/null 2>&1; then
    curl -fsSL -o "${MAVEN_ZIP}" "${MAVEN_DOWNLOAD_URL}" || {
      echo "Failed to download Maven from ${MAVEN_DOWNLOAD_URL}" >&2
      exit 1
    }
  elif command -v wget > /dev/null 2>&1; then
    wget -q -O "${MAVEN_ZIP}" "${MAVEN_DOWNLOAD_URL}" || {
      echo "Failed to download Maven from ${MAVEN_DOWNLOAD_URL}" >&2
      exit 1
    }
  else
    echo "Neither curl nor wget found. Cannot download Maven." >&2
    exit 1
  fi
  unzip -q "${MAVEN_ZIP}" -d "$(dirname "${MAVEN_HOME}")"
  rm -f "${MAVEN_ZIP}"
  chmod +x "${MAVEN_HOME}/bin/mvn"
fi

exec "${MAVEN_HOME}/bin/mvn" ${MAVEN_OPTS} "$@"
