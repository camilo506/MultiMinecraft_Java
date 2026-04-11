@echo off
REM ============================================================================
REM  MultiMinecraft Launcher - Script de Build del Instalador
REM  Genera el instalador .exe para distribuir a tus amigos
REM ============================================================================
echo.
echo ============================================
echo   MultiMinecraft Launcher - Build Installer
echo ============================================
echo.

REM --- Configuración ---
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot
set JPACKAGE="%JAVA_HOME%\bin\jpackage.exe"
set ISCC="C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
set PROJECT_DIR=%~dp0
set ICO_FILE=%PROJECT_DIR%src\main\resources\icons\app.ico
set APP_VERSION=1.0.0

REM --- Paso 1: Compilar el proyecto con Maven ---
echo [1/5] Compilando el proyecto con Maven...
call mvn clean package -DskipTests -q
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: La compilacion de Maven fallo.
    pause
    exit /b 1
)
echo       OK - Fat JAR generado.

REM --- Paso 2: Preparar directorio de entrada para jpackage ---
echo [2/5] Preparando archivos para jpackage...
if exist "%PROJECT_DIR%target\input" rmdir /s /q "%PROJECT_DIR%target\input"
mkdir "%PROJECT_DIR%target\input"
copy "%PROJECT_DIR%target\launcher-%APP_VERSION%.jar" "%PROJECT_DIR%target\input\" > nul
echo       OK - Archivos preparados.

REM --- Paso 3: Generar app-image nativo con jpackage ---
echo [3/5] Generando app-image nativo con jpackage...
if exist "%PROJECT_DIR%target\dist" rmdir /s /q "%PROJECT_DIR%target\dist"
%JPACKAGE% ^
    --type app-image ^
    --input "%PROJECT_DIR%target\input" ^
    --main-jar "launcher-%APP_VERSION%.jar" ^
    --main-class com.multiminecraft.launcher.Main ^
    --name MultiMinecraft ^
    --icon "%ICO_FILE%" ^
    --app-version %APP_VERSION% ^
    --vendor "MultiMinecraft" ^
    --dest "%PROJECT_DIR%target\dist" ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --java-options "-Dprism.dirtyopts=false"
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: jpackage fallo al generar el app-image.
    pause
    exit /b 1
)
echo       OK - App-image generado.

REM --- Paso 4: INCLUIR JAVA.EXE (Fix para PCs externas) ---
echo [4/5] Incluyendo java.exe y javaw.exe en el runtime...
copy "%JAVA_HOME%\bin\java.exe" "%PROJECT_DIR%target\dist\MultiMinecraft\runtime\bin\" > nul
copy "%JAVA_HOME%\bin\javaw.exe" "%PROJECT_DIR%target\dist\MultiMinecraft\runtime\bin\" > nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: No se pudo copiar java.exe al runtime.
    pause
    exit /b 1
)
echo       OK - Binarios de Java incluidos.

REM --- Paso 5: Compilar instalador .exe con Inno Setup ---
echo [5/5] Compilando instalador .exe con Inno Setup...
if not exist %ISCC% (
    echo ERROR: Inno Setup no encontrado en %ISCC%
    echo        Instala Inno Setup 6 desde: https://jrsoftware.org/isinfo.php
    pause
    exit /b 1
)
if not exist "%PROJECT_DIR%target\installer" mkdir "%PROJECT_DIR%target\installer"
%ISCC% "%PROJECT_DIR%installer\setup.iss"
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Inno Setup fallo al compilar el instalador.
    pause
    exit /b 1
)

echo.
echo ============================================
echo   INSTALADOR CREADO EXITOSAMENTE!
echo ============================================
echo.
echo   Archivo: target\installer\MultiMinecraft-Setup-%APP_VERSION%.exe
echo.
echo   Comparte este archivo con tus amigos.
echo   Ellos solo necesitan ejecutarlo para instalar el launcher.
echo   NO necesitan tener Java instalado!
echo.
pause
