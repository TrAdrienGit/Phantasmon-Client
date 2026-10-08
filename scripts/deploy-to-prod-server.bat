@echo off
REM Build the Phantasmon client (JDK 25 for Gradle/Loom) and deploy it:
REM local instance + server over SSH/Tailscale (fallback handled by the .sh script).
setlocal

set "JAVA_HOME=C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "GIT_BASH=C:\Program Files\Git\bin\bash.exe"
if not exist "%GIT_BASH%" (
    echo Git Bash introuvable: %GIT_BASH%
    exit /b 1
)

cd /d "%~dp0.."
"%GIT_BASH%" scripts/deploy-to-prod-server.sh
set RC=%ERRORLEVEL%

if %RC% neq 0 (
    echo.
    echo ECHEC du build/deploiement ^(code %RC%^).
) else (
    echo.
    echo OK. Relancer les jeux ouverts pour charger le nouveau jar.
)
pause
exit /b %RC%
