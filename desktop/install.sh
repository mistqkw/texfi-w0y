#!/usr/bin/env bash
# Собирает w0y для Linux из исходников и ставит для текущего пользователя.
# Нужны JDK 21 (JAVA_HOME) и mpv (звук играет он).
set -euo pipefail
cd "$(dirname "$0")"
./gradlew createDistributable
exec ./install-prebuilt.sh build/compose/binaries/main/app/w0y src/main/resources/icons
