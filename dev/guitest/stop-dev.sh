#!/bin/bash
# Stops the development build and the nested X display started by run-dev.sh.
cd "$(dirname "$0")/../.."
for f in dev/guitest/app.pid dev/guitest/xephyr.pid; do
	[ -f "$f" ] && kill "$(cat "$f")" 2>/dev/null; rm -f "$f"
done
echo "stopped"
