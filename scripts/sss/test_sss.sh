#!/bin/bash




TESTSITE=/tmp/ranflood_testsite
ATTACKSITE="$TESTSITE/attackedFolder"
REPORT="$TESTSITE/report_$(date +%Y%m%d%H%M%S).txt"
SETTINGS_PATH="src/tests/java/playground/settings.ini"


# Build
./gradlew build
./gradlew testCompareJar


# Create the test site and copy the settings files
mkdir "$TESTSITE" "$ATTACKSITE"
cp src/tests/java/playground/sss/compare/settings*.ini "$TESTSITE"


# Daemon:
# START in the background
java -jar build/libs/ranfloodd.jar src/tests/java/playground/settings.ini &
DAEMON_PID=$!
sleep 2

for SETTINGS in $@
do
	echo "Running with settings file: $SETTINGS"
	java -jar build/libs/ranflodd.jar $SETTINGS
done

# Daemon:
# STOP
kill $DAEMON_PID