#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/core
java -m jdk.compiler/com.sun.tools.javac.Main -d build/core app/src/main/java/com/doormate/app/core/*.java tests/SimulationTest.java
java -cp build/core SimulationTest
