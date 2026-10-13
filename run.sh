#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
mkdir -p "$DIR/build"

if [ "$1" == "--test" ]; then
    echo "🔨 Compilando e executando testes..."
    kotlinc "$DIR"/core/src/main/kotlin/org/opendriver/core/model/*.kt \
            "$DIR"/core/src/main/kotlin/org/opendriver/core/calculator/*.kt \
            "$DIR"/core/src/test/kotlin/org/opendriver/core/ViabilityEngineTest.kt \
            -include-runtime -d "$DIR/build/test-runner.jar"
    java -jar "$DIR/build/test-runner.jar"
    exit 0
fi

if [ ! -f "$DIR/build/opendriver-cli.jar" ] || [ "$1" == "--build" ]; then
    echo "🔨 Compilando OpenDriver CLI..."
    kotlinc "$DIR"/core/src/main/kotlin/org/opendriver/core/model/*.kt \
            "$DIR"/core/src/main/kotlin/org/opendriver/core/calculator/*.kt \
            "$DIR"/core/src/main/kotlin/org/opendriver/core/cli/*.kt \
            -include-runtime -d "$DIR/build/opendriver-cli.jar"
    if [ "$1" == "--build" ]; then
        echo "✅ Compilado com sucesso em build/opendriver-cli.jar"
        exit 0
    fi
fi

java -jar "$DIR/build/opendriver-cli.jar" "$@"
