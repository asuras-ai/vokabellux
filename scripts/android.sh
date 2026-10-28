#!/bin/sh
# Use the project-local toolchain when available, or the caller's installed tools.
set -eu
TASK_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$TASK_ROOT"
if [ -d "$TASK_ROOT/.tools/jdk/Contents/Home" ]; then
  export JAVA_HOME="$TASK_ROOT/.tools/jdk/Contents/Home"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if [ -d "$TASK_ROOT/.tools/android-sdk" ]; then export ANDROID_HOME="$TASK_ROOT/.tools/android-sdk"; fi
exec ./gradlew "$@"
