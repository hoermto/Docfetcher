#!/bin/bash
# Starts the development build of DocFetcher on a nested X display for GUI tests.
# Usage: dev/guitest/run-dev.sh [java executable] [extra JVM options]
# Requires: ./mill compile, Xephyr (started by this script on :2 if not running).
# Writes the PIDs to dev/guitest/app.pid and dev/guitest/xephyr.pid.
set -e
cd "$(dirname "$0")/../.."
JAVA="${1:-java}"; shift || true
DISPLAY_NR=:2
if ! pgrep -x Xephyr > /dev/null; then
	(setsid Xephyr $DISPLAY_NR -screen 1600x1000 -ac > dev/guitest/xephyr.log 2>&1 & echo $! > dev/guitest/xephyr.pid)
	sleep 3
fi
CP="src:out/compile.dest/classes"
for jar in $(find lib -name "*.jar" | grep -v -e "-sources" -e "-src" -e win32 -e cocoa -e "Windows-" -e "Mac-" -e "/ant" -e junit-4); do
	CP="$CP:$jar"
done
(DISPLAY=$DISPLAY_NR setsid "$JAVA" -Djava.library.path=lib/jnotify:lib/jxgrabkey "$@" -cp "$CP" \
	net.sourceforge.docfetcher.gui.Application > dev/guitest/app.log 2>&1 & echo $! > dev/guitest/app.pid)
echo "DocFetcher started on display $DISPLAY_NR (pid $(cat dev/guitest/app.pid))"
