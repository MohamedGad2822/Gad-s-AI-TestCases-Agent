@echo off
cd /d "%~dp0"
if exist build rmdir /s /q build
mkdir build
dir /s /b src\*.java > build\sources.txt
javac -encoding UTF-8 -d build @build\sources.txt || exit /b 1
echo Main-Class: com.ignteq.tcagent.Main> build\manifest.txt
jar cfm tc-agent.jar build\manifest.txt -C build com
echo built: %cd%\tc-agent.jar
