#!/bin/bash
# Runs JUnit tests (JUnit 4, tests live next to the sources in src/).
# Usage: dev/guitest/run-tests.sh [java executable] <test class> [<test class> ...]
# Example: dev/guitest/run-tests.sh java net.sourceforge.docfetcher.model.ocr.ImagePagesTest
set -e
cd "$(dirname "$0")/../.."
JAVA="${1:-java}"; shift
CP="src:out/compile.dest/classes"
for jar in $(find lib -name "*.jar" | grep -v -e "-sources" -e "-src" -e win32 -e cocoa -e "Windows-" -e "Mac-" -e "/ant"); do
	CP="$CP:$jar"
done
# jnotify is needed by tests that load an index registry (folder watching)
"$JAVA" -Djava.library.path=lib/jnotify:lib/jxgrabkey -cp "$CP" org.junit.runner.JUnitCore "$@"
