#!/bin/bash
# Starts the development build of DocFetcher for GUI tests.
# Usage: dev/guitest/run-dev.sh [java executable] [extra JVM options]
# Linux: requires ./mill compile and Xephyr (started by this script on :2 if not running).
# Windows (Git Bash): the program runs on the desktop; drive it with dev/guitest/win.py.
# Writes the PIDs to dev/guitest/app.pid and dev/guitest/xephyr.pid.
set -e
cd "$(dirname "$0")/../.."
JAVA="${1:-java}"; shift || true
case "$(uname -s)" in
	MINGW*|MSYS*|CYGWIN*) WINDOWS=1; SEP=";"; OTHER="-e gtk -e cocoa -e Linux- -e Mac-" ;;
	*) WINDOWS=0; SEP=":"; OTHER="-e win32 -e cocoa -e Windows- -e Mac-" ;;
esac
DISPLAY_NR=:2
if [ $WINDOWS = 0 ] && ! pgrep -x Xephyr > /dev/null; then
	(setsid Xephyr $DISPLAY_NR -screen 1600x1000 -ac > dev/guitest/xephyr.log 2>&1 & echo $! > dev/guitest/xephyr.pid)
	sleep 3
fi
CP="src${SEP}out/compile.dest/classes"
for jar in $(find lib -name "*.jar" | grep -v -e "-sources" -e "-src" $OTHER -e "/ant" -e junit-4); do
	CP="$CP$SEP$jar"
done
LIBPATH="lib/jnotify${SEP}lib/jxgrabkey"
if [ $WINDOWS = 1 ]; then
	("$JAVA" -Djava.library.path="$LIBPATH" "$@" -cp "$CP" \
		net.sourceforge.docfetcher.gui.Application > dev/guitest/app.log 2>&1 & echo $! > dev/guitest/app.pid)
	echo "DocFetcher started (pid $(cat dev/guitest/app.pid))"
else
	(DISPLAY=$DISPLAY_NR setsid "$JAVA" -Djava.library.path="$LIBPATH" "$@" -cp "$CP" \
		net.sourceforge.docfetcher.gui.Application > dev/guitest/app.log 2>&1 & echo $! > dev/guitest/app.pid)
	echo "DocFetcher started on display $DISPLAY_NR (pid $(cat dev/guitest/app.pid))"
fi
