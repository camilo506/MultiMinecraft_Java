; ============================================================================
; MultiMinecraft Launcher - Inno Setup Installer Script
; Genera un instalador .exe profesional para Windows
; ============================================================================

#define MyAppName "MultiMinecraft"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "MultiMinecraft"
#define MyAppExeName "MultiMinecraft.exe"
#define MyAppURL "https://github.com/MultiMinecraft"

[Setup]
; Identificador único de la app (NO cambiar después de la primera distribución)
AppId={{E8F2A1B3-7C4D-4E5F-9A0B-1C2D3E4F5A6B}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
; Permisos: no requiere admin
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
; Archivo de salida
OutputDir=..\target\installer
OutputBaseFilename=MultiMinecraft-Setup-{#MyAppVersion}
; Ícono del instalador
SetupIconFile=..\src\main\resources\icons\app.ico
; Compresión
Compression=lzma2/ultra64
SolidCompression=yes
; Visual
WizardStyle=modern
WizardSizePercent=110
; Desinstalador
UninstallDisplayIcon={app}\{#MyAppExeName}
UninstallDisplayName={#MyAppName} Launcher

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Crear acceso directo en el &Escritorio"; GroupDescription: "Accesos directos:"

[Files]
; Copiar TODO el contenido del app-image generado por jpackage
Source: "..\target\dist\MultiMinecraft\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
; Acceso directo en el menú inicio
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\Desinstalar {#MyAppName}"; Filename: "{uninstallexe}"
; Acceso directo en el escritorio (si el usuario lo eligió)
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
; Opción para ejecutar el launcher después de instalar
Filename: "{app}\{#MyAppExeName}"; Description: "Ejecutar {#MyAppName} ahora"; Flags: nowait postinstall skipifsilent
