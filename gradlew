#!/usr/bin/env sh
##############################################################################
#  Gradle start up script (POSIX).
#  Memakai gradle-wrapper.jar bila ada. Bila tidak ada (jar tidak ikut di zip),
#  memakai perintah `gradle` yang terpasang di sistem (Termux: pkg install gradle).
##############################################################################
APP_HOME=$( cd "$( dirname "$0" )" && pwd )
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$CLASSPATH" ]; then
    if command -v gradle >/dev/null 2>&1; then
        exec gradle "$@"
    fi
    echo "gradle-wrapper.jar tidak ada dan perintah 'gradle' tidak ditemukan." >&2
    echo "Di Termux: pkg install gradle" >&2
    exit 1
fi

if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi
exec "$JAVACMD" -Xmx64m -Xms64m $JAVA_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
