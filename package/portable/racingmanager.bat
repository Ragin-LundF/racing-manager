@echo off
REM RacingManager portable launcher
set SCRIPT_DIR=%~dp0
set JRE_DIR=%SCRIPT_DIR%jre
set JAR=%SCRIPT_DIR%lib\racingmanager-backend-*-fat.jar

if exist "%JRE_DIR%\bin\java.exe" (
    set JAVA="%JRE_DIR%\bin\java.exe"
) else (
    set JAVA=java
)

%JAVA% -Dracingmanager.profile=prod -jar "%JAR%" %*
