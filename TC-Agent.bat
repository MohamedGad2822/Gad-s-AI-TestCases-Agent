@echo off
rem Double-click launcher: opens the GUI with no console window.
cd /d "%~dp0"
start "" javaw -jar tc-agent.jar
