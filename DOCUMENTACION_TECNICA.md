# 📚 Documentación Técnica - MultiMinecraft Launcher

## 📋 Tabla de Contenidos

1. [Arquitectura General](#arquitectura-general)
2. [Descarga e Instalación de Instancias Vanilla](#descarga-e-instalación-de-instancias-vanilla)
3. [Descarga e Instalación de Instancias Forge](#descarga-e-instalación-de-instancias-forge)
4. [Ejecución de Instancias](#ejecución-de-instancias)
5. [Componentes Principales](#componentes-principales)
6. [Flujos de Datos](#flujos-de-datos)

---

## 🏗️ Arquitectura General

### Estructura del Proyecto

El launcher sigue una arquitectura **MVC (Modelo-Vista-Controlador)** con servicios especializados:

```
src/main/java/com/multiminecraft/launcher/
├── Main.java                    # Punto de entrada de la aplicación
├── App.java                     # Aplicación JavaFX principal
├── model/                       # Modelos de datos (POJOs)
│   ├── Instance.java            # Representa una instancia de Minecraft
│   ├── LauncherConfig.java     # Configuración global del launcher
│   ├── MinecraftVersion.java   # Información de versión de Minecraft
│   └── LoaderType.java         # Enum: VANILLA, FORGE, FABRIC, etc.
├── service/                     # Lógica de negocio (Servicios)
│   ├── ConfigService.java      # Gestión de configuración (JSON)
│   ├── DownloadService.java    # Descarga de archivos HTTP
│   ├── MojangService.java      # Interacción con API de Mojang
│   ├── InstanceService.java    # Gestión de instancias (CRUD)
│   ├── LaunchService.java      # Lanzamiento de Minecraft
│   ├── ForgeService.java       # Instalación de Forge
│   └── FabricService.java      # Instalación de Fabric
├── controller/                  # Controladores JavaFX (UI)
│   ├── MainController.java
│   ├── CreateInstanceController.java
│   └── InstancesController.java
└── util/                        # Utilidades
    ├── PlatformUtil.java        # Detección de OS y rutas
    ├── FileUtil.java           # Operaciones de archivos
    └── JsonUtil.java           # Utilidades JSON
```

### Flujo General de Operación

```
Usuario → Controller → Service → API/Archivos → Respuesta → UI
```

1. **Usuario** interactúa con la interfaz JavaFX
2. **Controller** recibe eventos y delega a servicios
3. **Service** ejecuta lógica de negocio (descargas, instalación, etc.)
4. **API/Archivos** se comunican con servicios externos o sistema de archivos
5. **Respuesta** se procesa y actualiza la UI

---

## 📥 Descarga e Instalación de Instancias Vanilla

### Flujo Completo

```
1. Usuario crea instancia Vanilla
   ↓
2. InstanceService.createInstance()
   ↓
3. Crear estructura de directorios
   ↓
4. MojangService.downloadVersion()
   ↓
5. Descargar JSON de versión
   ↓
6. Descargar Client JAR
   ↓
7. Descargar librerías (paralelo)
   ↓
8. Descargar índice de assets
   ↓
9. Guardar configuración (instance.json)
   ↓
10. Instancia lista
```

### Detalles Paso a Paso

#### 1. Creación de Instancia (`InstanceService.createInstance()`)

**Ubicación**: `src/main/java/com/multiminecraft/launcher/service/InstanceService.java:36`

```java
public void createInstance(Instance instance, Consumer<String> statusCallback) throws Exception {
    // 1. Validar nombre
    if (instance.getName() == null || instance.getName().trim().isEmpty()) {
        throw new IllegalArgumentException("El nombre de la instancia no puede estar vacío");
    }
    
    // 2. Verificar que no exista
    if (instanceExists(instance.getName())) {
        throw new IllegalArgumentException("Ya existe una instancia con ese nombre");
    }
    
    // 3. Obtener rutas
    Path instanceDir = configService.getInstanceDirectory(instance.getName());
    Path minecraftDir = configService.getInstanceMinecraftDirectory(instance.getName());
    
    // 4. Crear estructura de directorios
    createInstanceDirectories(minecraftDir);
    
    // 5. Descargar Minecraft Vanilla
    mojangService.downloadVersion(instance.getVersion(), minecraftDir, statusCallback);
    
    // 6. Guardar configuración
    configService.saveInstanceConfig(instance);
}
```

**Estructura de directorios creada**:
```
Instancias/{nombre}/
└── .minecraft/
    ├── versions/          # Versiones de Minecraft instaladas
    ├── libraries/         # Librerías (puede estar vacío si usa compartidas)
    ├── assets/           # Assets del juego (texturas, sonidos)
    │   ├── indexes/      # Índices de assets
    │   └── objects/      # Archivos de assets (hash)
    ├── mods/             # Mods instalados
    ├── resourcepacks/    # Packs de recursos
    ├── shaderpacks/      # Shaders
    ├── saves/           # Mundos guardados
    ├── screenshots/     # Capturas
    └── logs/            # Logs del juego
```

#### 2. Descarga de Versión (`MojangService.downloadVersion()`)

**Ubicación**: `src/main/java/com/multiminecraft/launcher/service/MojangService.java:96`

**Proceso**:

1. **Obtener información de versión**:
   - Consulta el manifest de versiones: `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`
   - Busca la versión específica (ej: "1.20.1")
   - Obtiene la URL del JSON de versión

2. **Descargar JSON de versión**:
   - URL ejemplo: `https://piston-meta.mojang.com/v1/packages/{hash}/{version}.json`
   - Guarda en: `versions/{version}/{version}.json`
   - Este JSON contiene:
     - Información de descarga del cliente
     - Lista de librerías necesarias
     - Índice de assets
     - Argumentos de lanzamiento
     - Main class

3. **Descargar Client JAR**:
   - URL del JAR desde el JSON: `downloads.client.url`
   - Guarda en: `versions/{version}/{version}.jar`
   - Verifica hash SHA1 antes de descargar (evita re-descargas)

4. **Descargar librerías** (`downloadLibraries()`):
   - **Ubicación compartida**: `.MultiMinecraft_Java/libraries/` (prioridad)
   - **Ubicación instancia**: `Instancias/{nombre}/.minecraft/libraries/` (fallback)
   - Descarga en **paralelo** (8 conexiones simultáneas)
   - Verifica reglas de compatibilidad de OS
   - Formato de ruta Maven: `com/example/library/version/library-version.jar`

5. **Descargar índice de assets**:
   - Solo descarga el índice JSON (no los assets en sí)
   - Los assets se descargan cuando se lanza el juego
   - Guarda en: `assets/indexes/{assetIndexId}.json`

### Ejemplo de JSON de Versión (1.20.1)

```json
{
  "id": "1.20.1",
  "type": "release",
  "mainClass": "net.minecraft.client.main.Main",
  "inheritsFrom": null,
  "downloads": {
    "client": {
      "sha1": "0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838",
      "size": 12345678,
      "url": "https://piston-data.mojang.com/v1/objects/..."
    }
  },
  "libraries": [
    {
      "name": "com.mojang:patchy:1.3.9",
      "downloads": {
        "artifact": {
          "path": "com/mojang/patchy/1.3.9/patchy-1.3.9.jar",
          "sha1": "...",
          "url": "https://libraries.minecraft.net/..."
        }
      },
      "rules": [
        {
          "action": "allow"
        }
      ]
    }
  ],
  "assetIndex": {
    "id": "5",
    "sha1": "...",
    "url": "https://piston-meta.mojang.com/v1/packages/..."
  },
  "arguments": {
    "game": [...],
    "jvm": [...]
  }
}
```

---

## 🔧 Descarga e Instalación de Instancias Forge

### Flujo Completo

```
1. Usuario crea instancia Forge
   ↓
2. InstanceService.createInstance()
   ↓
3. Crear estructura de directorios
   ↓
4. MojangService.downloadVersion() (Vanilla base)
   ↓
5. ForgeService.installForge()
   ↓
6. Obtener versión recomendada de Forge
   ↓
7. Descargar instalador de Forge
   ↓
8. Ejecutar instalador (--installClient)
   ↓
9. Verificar instalación
   ↓
10. Descargar librerías de Forge
   ↓
11. Extraer natives
   ↓
12. Guardar configuración
   ↓
13. Instancia lista
```

### Detalles Paso a Paso

#### 1. Obtención de Versión de Forge (`ForgeService.getRecommendedForgeVersion()`)

**Ubicación**: `src/main/java/com/multiminecraft/launcher/service/ForgeService.java:234`

**Proceso**:

1. **Consultar API de Forge**:
   - URL: `https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json`
   - Este JSON contiene las versiones recomendadas y latest

2. **Buscar versión recomendada**:
   - Clave: `{minecraftVersion}-recommended` (ej: "1.20.1-recommended")
   - Si no existe, busca: `{minecraftVersion}-latest`
   - Retorna: `{minecraftVersion}-{forgeVersion}` (ej: "1.20.1-47.4.10")

**Ejemplo de JSON de promociones**:
```json
{
  "promos": {
    "1.20.1-recommended": "47.4.10",
    "1.20.1-latest": "47.4.11"
  }
}
```

#### 2. Descarga del Instalador (`ForgeService.installForge()`)

**Ubicación**: `src/main/java/com/multiminecraft/launcher/service/ForgeService.java:48`

**URLs de descarga** (en orden de intento):

1. **URL principal** (Maven):
   ```
   https://maven.minecraftforge.net/net/minecraftforge/forge/{version}/forge-{version}-installer.jar
   ```
   Ejemplo: `https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-installer.jar`

2. **URL alternativa** (legacy):
   ```
   https://files.minecraftforge.net/maven/net/minecraftforge/forge/{version}/forge-{version}-installer.jar
   ```

**Guardado**: `{minecraftDir}/temp/forge-installer.jar`

#### 3. Ejecución del Instalador

**Ubicación**: `src/main/java/com/multiminecraft/launcher/service/ForgeService.java:148`

**Comando ejecutado**:
```bash
java -jar forge-installer.jar --installClient
```

**Configuración previa**:
- Directorio de trabajo: `.minecraft` de la instancia
- Crea `launcher_profiles.json` si no existe (requerido por el instalador)
- Crea directorio `versions/` si no existe

**Proceso del instalador**:
1. El instalador detecta el directorio `.minecraft` desde el working directory
2. Descarga el JSON de versión Forge
3. Crea la carpeta `versions/{forgeVersion}/`
4. Guarda `{forgeVersion}.json` con toda la información de Forge
5. Extrae natives a `versions/{forgeVersion}/natives/`

**Estructura después de la instalación**:
```
.minecraft/
├── versions/
│   ├── 1.20.1/                    # Versión base Vanilla
│   │   ├── 1.20.1.json
│   │   └── 1.20.1.jar
│   └── 1.20.1-forge-47.4.10/      # Versión Forge
│       ├── 1.20.1-forge-47.4.10.json
│       └── natives/                # DLLs extraídos
│           ├── glfw.dll
│           ├── lwjgl.dll
│           └── ...
└── launcher_profiles.json          # Creado por el instalador
```

#### 4. Verificación y Completado (`verifyAndCompleteForgeInstallation()`)

**Ubicación**: `src/main/java/com/multiminecraft/launcher/service/ForgeService.java:290`

**Proceso**:

1. **Buscar versión Forge instalada**:
   - Busca directorios en `versions/` que empiecen con `{minecraftVersion}-forge-`
   - Ejemplo: `1.20.1-forge-47.4.10`

2. **Verificar estructura**:
   - Existe directorio de versión
   - Existe JSON de versión
   - JSON es válido

3. **Descargar librerías de Forge**:
   - Lee el JSON de Forge
   - Obtiene lista de librerías (incluyendo herencia)
   - Descarga al directorio **compartido** (prioridad)
   - Fallback a directorio de instancia si falla

4. **Extraer natives**:
   - Busca librerías con classifier `natives-windows` (o según OS)
   - Extrae `.dll`, `.so`, `.dylib` de los JARs
   - Guarda en `versions/{forgeVersion}/natives/`

### Ejemplo de JSON de Versión Forge (1.20.1-forge-47.4.10)

```json
{
  "id": "1.20.1-forge-47.4.10",
  "inheritsFrom": "1.20.1",
  "type": "release",
  "mainClass": "cpw.mods.bootstraplauncher.BootstrapLauncher",
  "arguments": {
    "game": [
      "--username", "${auth_player_name}",
      "--version", "${version_name}",
      ...
    ],
    "jvm": [
      "-Dforge.logging.markers=REGISTRIES",
      "-Dforge.logging.console.level=debug",
      ...
    ]
  },
  "libraries": [
    {
      "name": "net.minecraftforge:forge:1.20.1-47.4.10",
      "downloads": {
        "artifact": {
          "path": "net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10.jar",
          "url": "https://maven.minecraftforge.net/..."
        }
      }
    },
    {
      "name": "cpw.mods:modlauncher:10.0.9",
      ...
    }
  ]
}
```

**Características importantes**:
- `inheritsFrom: "1.20.1"`: Hereda configuración de la versión Vanilla
- `mainClass: "cpw.mods.bootstraplauncher.BootstrapLauncher"`: Main class de Forge
- Librerías adicionales de Forge y ModLauncher

---

## 🚀 Ejecución de Instancias

### Flujo Completo

```
1. Usuario hace clic en "Jugar"
   ↓
2. LaunchService.launchInstance()
   ↓
3. Determinar versión (Vanilla o Forge)
   ↓
4. Cargar JSON de versión
   ↓
5. Verificar/descargar librerías faltantes
   ↓
6. Construir comando de lanzamiento
   ↓
7. Filtrar argumentos problemáticos (Forge)
   ↓
8. Ejecutar proceso
   ↓
9. Descargar assets en segundo plano
   ↓
10. Capturar salida del proceso
```

### Detalles Paso a Paso

#### 1. Determinación de Versión (`LaunchService.launchInstance()`)

**Ubicación**: `src/main/java/com/multiminecraft/launcher/service/LaunchService.java:39`

**Para Vanilla**:
- Usa directamente la versión: `1.20.1`
- Carga: `versions/1.20.1/1.20.1.json`

**Para Forge**:
- Busca versión Forge: `findForgeVersion()`
- Busca directorios que empiecen con `{version}-forge-`
- Usa: `1.20.1-forge-47.4.10`
- Carga: `versions/1.20.1-forge-47.4.10/1.20.1-forge-47.4.10.json`

#### 2. Construcción del Comando (`buildLaunchCommand()`)

**Ubicación**: `src/main/java/com/multiminecraft/launcher/service/LaunchService.java:146`

**Estructura del comando**:

```bash
java \
  -Xmx4G \                    # Memoria máxima
  -Xms2G \                    # Memoria mínima
  [argumentos JVM] \          # Del JSON (heredados + actuales)
  -cp <classpath> \           # Classpath (si es legacy)
  <mainClass> \               # net.minecraft.client.main.Main o cpw.mods.bootstraplauncher.BootstrapLauncher
  [argumentos del juego]      # Del JSON (heredados + actuales)
```

**Proceso de construcción**:

1. **Java executable**:
   - De configuración de instancia o global
   - Fallback: `System.getProperty("java.home")`

2. **Argumentos JVM**:
   - Primero del padre (si `inheritsFrom` existe)
   - Luego del actual
   - Reemplaza variables: `${natives_directory}`, `${library_directory}`, etc.

3. **Classpath** (`buildClasspath()`):
   - Agrega todas las librerías (compartidas primero, luego instancia)
   - Maneja herencia (`inheritsFrom`)
   - Agrega Client JAR
   - Separador según OS: `;` (Windows) o `:` (Linux/Mac)

4. **Main class**:
   - Vanilla: `net.minecraft.client.main.Main`
   - Forge: `cpw.mods.bootstraplauncher.BootstrapLauncher`

5. **Argumentos del juego**:
   - Primero del padre (si existe)
   - Luego del actual
   - Reemplaza variables: `${version_name}`, `${game_directory}`, etc.

#### 3. Filtrado de Argumentos de Módulos (`filterModuleArguments()`)

**Ubicación**: `src/main/java/com/multiminecraft/launcher/service/LaunchService.java:673`

**Problema**: Forge usa classpath, no módulos de Java. Los argumentos de módulos causan conflictos.

**Argumentos eliminados**:
- `--add-modules`
- `--module-path`
- `--module`
- `ALL-UNNAMED`
- `ALL-MODULE-PATH`
- `--add-opens` problemáticos (excepto `java.base`)

#### 4. Ejecución del Proceso

**Ubicación**: `src/main/java/com/multiminecraft/launcher/service/LaunchService.java:102`

```java
ProcessBuilder processBuilder = new ProcessBuilder(command);
processBuilder.directory(minecraftDir.toFile());  // Working directory
processBuilder.redirectErrorStream(true);        // Combinar stdout y stderr
Process process = processBuilder.start();
```

**Operaciones en segundo plano**:
- Captura de salida del proceso (logging)
- Actualización de `lastPlayed` en configuración
- Descarga de assets (no bloquea el lanzamiento)

#### 5. Reemplazo de Variables

**Ubicación**: `src/main/java/com/multiminecraft/launcher/service/LaunchService.java:571`

**Variables disponibles**:

| Variable | Reemplazo |
|----------|-----------|
| `${auth_player_name}` | "Player" (offline) |
| `${version_name}` | ID de versión |
| `${game_directory}` | Ruta `.minecraft` |
| `${assets_root}` | Ruta `assets/` |
| `${assets_index_name}` | ID del índice de assets |
| `${natives_directory}` | Ruta `versions/{version}/natives/` |
| `${library_directory}` | Ruta de librerías compartidas |
| `${classpath_separator}` | `;` o `:` según OS |
| `${classpath}` | Classpath completo construido |
| `${launcher_name}` | "MultiMinecraft" |
| `${launcher_version}` | "1.0" |

---

## 🧩 Componentes Principales

### DownloadService

**Responsabilidades**:
- Descarga de archivos HTTP con progreso
- Descarga paralela de múltiples archivos
- Manejo de timeouts y errores
- Verificación de hashes SHA1

**Métodos principales**:
- `downloadFile(url, destination, progressCallback)`: Descarga simple
- `downloadFilesParallel(tasks, maxConcurrent, progressCallback)`: Descarga paralela
- `downloadString(url)`: Descarga de texto (JSON)

### MojangService

**Responsabilidades**:
- Interacción con API de Mojang
- Descarga de versiones de Minecraft
- Descarga de librerías
- Descarga de assets

**Métodos principales**:
- `fetchVersions()`: Obtiene lista de versiones disponibles
- `downloadVersion(versionId, minecraftDir, callback)`: Descarga versión completa
- `downloadLibraries(versionData, librariesDir)`: Descarga librerías en paralelo
- `downloadAssets(versionData, minecraftDir, callback)`: Descarga assets

### ForgeService

**Responsabilidades**:
- Obtención de versiones de Forge
- Descarga del instalador
- Ejecución del instalador
- Verificación y completado de instalación

**Métodos principales**:
- `installForge(minecraftVersion, minecraftDir, callback)`: Instalación completa
- `getRecommendedForgeVersion(minecraftVersion)`: Obtiene versión recomendada
- `verifyAndCompleteForgeInstallation(...)`: Verifica y completa instalación
- `extractNativesIfNeeded(...)`: Extrae archivos nativos

### LaunchService

**Responsabilidades**:
- Construcción de comandos de lanzamiento
- Ejecución de procesos de Minecraft
- Manejo de classpath y argumentos
- Filtrado de argumentos problemáticos

**Métodos principales**:
- `launchInstance(instance)`: Lanza una instancia
- `buildLaunchCommand(...)`: Construye comando completo
- `buildClasspath(...)`: Construye classpath
- `filterModuleArguments(...)`: Filtra argumentos de módulos

### ConfigService

**Responsabilidades**:
- Gestión de configuración global (`launcher.json`)
- Gestión de configuración de instancias (`instance.json`)
- Rutas de directorios

**Métodos principales**:
- `getInstanceDirectory(name)`: Ruta de instancia
- `getInstanceMinecraftDirectory(name)`: Ruta `.minecraft`
- `getSharedLibrariesDirectory()`: Ruta de librerías compartidas
- `saveInstanceConfig(instance)`: Guarda configuración
- `loadInstanceConfig(name)`: Carga configuración

---

## 🔄 Flujos de Datos

### Flujo de Creación de Instancia Vanilla

```
CreateInstanceController.onCreate()
    ↓
InstanceService.createInstance()
    ↓
├─→ createInstanceDirectories()
│   └─→ Crea estructura de carpetas
│
├─→ MojangService.downloadVersion()
│   ├─→ getVersion() → Consulta manifest
│   ├─→ downloadString() → Descarga JSON de versión
│   ├─→ downloadFile() → Descarga Client JAR
│   ├─→ downloadLibraries() → Descarga librerías (paralelo)
│   └─→ downloadAssetIndex() → Descarga índice de assets
│
└─→ ConfigService.saveInstanceConfig()
    └─→ Guarda instance.json
```

### Flujo de Creación de Instancia Forge

```
CreateInstanceController.onCreate()
    ↓
InstanceService.createInstance()
    ↓
├─→ createInstanceDirectories()
│
├─→ MojangService.downloadVersion()  # Vanilla base
│
└─→ ForgeService.installForge()
    ├─→ getRecommendedForgeVersion()
    │   └─→ Consulta API de Forge
    │
    ├─→ downloadFile() → Descarga instalador
    │
    ├─→ ProcessBuilder → Ejecuta instalador
    │   └─→ Crea versión Forge en versions/
    │
    └─→ verifyAndCompleteForgeInstallation()
        ├─→ findInstalledForgeVersion()
        ├─→ verifyForgeInstallation()
        ├─→ verifyAndDownloadForgeLibraries()
        └─→ extractNativesIfNeeded()
```

### Flujo de Ejecución

```
MainController.onPlayClicked()
    ↓
LaunchService.launchInstance()
    ↓
├─→ findForgeVersion()  # Si es Forge
│
├─→ FileUtil.readFileAsString() → Carga JSON de versión
│
├─→ verifyAndDownloadLibraries() → Auto-repair
│
├─→ buildLaunchCommand()
│   ├─→ buildClasspath()
│   │   ├─→ addLibraries() → Agrega librerías al classpath
│   │   └─→ addClientJar() → Agrega JAR del cliente
│   │
│   ├─→ addArguments() → Agrega argumentos JVM y game
│   │   └─→ replaceVariables() → Reemplaza ${variables}
│   │
│   └─→ filterModuleArguments() → Filtra argumentos problemáticos
│
├─→ ProcessBuilder.start() → Ejecuta proceso
│
└─→ Threads en segundo plano:
    ├─→ captureOutput() → Captura salida
    ├─→ Actualiza lastPlayed
    └─→ downloadAssets() → Descarga assets
```

---

## 📝 Notas Técnicas Importantes

### Directorios Compartidos vs Instancia

**Librerías**:
- **Prioridad**: `.MultiMinecraft_Java/libraries/` (compartido)
- **Fallback**: `Instancias/{nombre}/.minecraft/libraries/`
- **Razón**: Evita duplicados, ahorra espacio

**Assets**:
- **Ubicación**: `Instancias/{nombre}/.minecraft/assets/`
- **Razón**: Pueden variar entre versiones

**Versiones**:
- **Ubicación**: `Instancias/{nombre}/.minecraft/versions/`
- **Razón**: Cada instancia puede tener versiones diferentes

### Herencia de Versiones

Forge usa `inheritsFrom` para heredar de Vanilla:
- Librerías del padre se incluyen
- Argumentos del padre se combinan
- Main class del hijo tiene prioridad

### Manejo de Errores

- **Descarga**: Reintentos automáticos con URLs alternativas
- **Instalación**: Limpieza automática si falla
- **Ejecución**: Auto-repair de librerías faltantes

### Compatibilidad de OS

- **Reglas de librerías**: Filtrado por OS (`rules` en JSON)
- **Separadores de ruta**: Detectados automáticamente
- **Natives**: Extraídos según OS (`.dll`, `.so`, `.dylib`)

---

## 🔍 Debugging

### Logs

Los logs se guardan en:
- **Launcher**: `.MultiMinecraft_Java/logs/launcher.log`
- **Minecraft**: `Instancias/{nombre}/.minecraft/logs/`

### Niveles de Log

- **INFO**: Operaciones principales (descarga, instalación, lanzamiento)
- **DEBUG**: Detalles técnicos (URLs, rutas, argumentos)
- **WARN**: Advertencias (archivos faltantes, fallbacks)
- **ERROR**: Errores críticos

### Variables de Entorno

- `JAVA_HOME`: Usado como fallback para ruta de Java
- `APPDATA` (Windows): Directorio de datos del usuario

---

**Documentación generada el**: 2025-12-09
**Versión del launcher**: 1.0.0

