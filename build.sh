#!/usr/bin/env bash
# Build without Maven. Needs a JDK 17+ on PATH.
set -e
cd "$(dirname "$0")"
rm -rf build && mkdir -p build
find src -name '*.java' > build/sources.txt
javac -encoding UTF-8 -d build @build/sources.txt
printf 'Main-Class: com.ignteq.tcagent.Main\n' > build/manifest.txt
jar cfm tc-agent.jar build/manifest.txt -C build com
echo "built: $(pwd)/tc-agent.jar"
