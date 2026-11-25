@echo off
cd /d "%~dp0"
echo Compiling EWasteApp.java ...
javac -encoding UTF-8 -cp ".;lib\*" EWasteApp.java
if errorlevel 1 (
  echo Compile failed.
  pause
  exit /b 1
)
java -cp ".;lib\*" EWasteApp
