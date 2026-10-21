#!/usr/bin/env bash
# Сборка и запуск игры "Звёздные войны" (Linux/macOS)
set -e
cd "$(dirname "$0")"
mkdir -p bin
javac -encoding UTF-8 -d bin src/starwars/StarWarsGame.java
java -cp bin starwars.StarWarsGame
