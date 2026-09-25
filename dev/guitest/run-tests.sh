#!/bin/bash
# Runs JUnit tests (JUnit 4, tests live next to the sources in src/).
# Usage: dev/guitest/run-tests.sh [java executable] <test class> [<test class> ...]
# Example: dev/guitest/run-tests.sh java net.sourceforge.docfetcher.model.ocr.ImagePagesTest
# Works on Linux and in Git Bash on Windows.
set -e
cd "$(dirname "$0")/../.."
JAVA="${1:-java}"; shift
case "$(uname -s)" in
	MINGW*|MSYS*|CYGWIN*) SEP=";"; OTHER="-e gtk -e cocoa -e Linux- -e Mac-" ;;
	*) SEP=":"; OTHER="-e win32 -e cocoa -e Windows- -e Mac-" ;;
esac
CP="src${SEP}out/compile.dest/classes"
for jar in $(find lib -name "*.jar" | grep -v -e "-sources" -e "-src" $OTHER -e "/ant"); do
	CP="$CP$SEP$jar"
done
# jnotify is needed by tests that load an index registry (folder watching)
"$JAVA" -Djava.library.path="lib/jnotify${SEP}lib/jxgrabkey" -cp "$CP" org.junit.runner.JUnitCore "$@"
