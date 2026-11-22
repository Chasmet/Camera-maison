#!/usr/bin/env sh
# Launcher de secours: les workflows génèrent le wrapper Gradle officiel.
set -eu
cd "$(dirname "$0")"
if [ -f gradle/wrapper/gradle-wrapper.jar ]; then
    exec java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain "$@"
fi
if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
fi
printf '%s\n' 'Gradle non installé. Installe Gradle 8.7, puis exécute gradle wrapper --gradle-version 8.7.' >&2
exit 1
