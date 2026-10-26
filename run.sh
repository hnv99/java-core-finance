#!/usr/bin/env sh
# Compiles and starts FinCore without Maven (only a JDK 21 is required).
# Usage: ./run.sh [dataDirectory]
set -e
cd "$(dirname "$0")"
rm -rf out
javac -encoding UTF-8 --release 21 -Xlint:all,-serial -d out $(find src/main/java -name '*.java')
java -cp out com.fincore.App "$@"
