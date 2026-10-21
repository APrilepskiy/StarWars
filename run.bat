@echo off
rem Сборка и запуск игры "Звёздные войны" (Windows)
cd /d "%~dp0"
if not exist bin mkdir bin
javac -encoding UTF-8 -d bin src\starwars\StarWarsGame.java
java -cp bin starwars.StarWarsGame
