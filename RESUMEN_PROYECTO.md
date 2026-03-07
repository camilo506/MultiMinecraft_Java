# MultiMinecraft Launcher — Resumen del Proyecto 🎮

Es un **launcher (lanzador) multiplataforma de Minecraft** que permite gestionar **instancias independientes** del juego. Cada instancia tiene su propio directorio `.minecraft` completamente aislado, lo que significa que puedes tener distintas versiones, mods, mundos y configuraciones sin que interfieran entre sí.

---

## 🛠️ Tecnologías utilizadas

| Categoría | Tecnología | Versión | Propósito |
|---|---|---|---|
| **Lenguaje** | **Java** | 17 | Lenguaje principal del proyecto |
| **UI/Interfaz** | **JavaFX** | 21.0.1 | Interfaz gráfica (controles, FXML, web) |
| **Build System** | **Maven** | 3.6+ | Gestión de dependencias y compilación |
| **JSON** | **Gson** (Google) | 2.10.1 | Serialización/deserialización de JSON |
| **HTTP Client** | **OkHttp** | 4.12.0 | Descargas HTTP (versiones, librerías, assets) |
| **Logging** | **SLF4J** + **Logback** | 2.0.9 / 1.4.14 | Sistema de logs |
| **File I/O** | **Commons IO** | 2.15.1 | Operaciones de archivos |
| **Testing** | **JUnit Jupiter** | 5.10.1 | Tests unitarios |
| **Packaging** | **Maven Shade Plugin** | 3.5.1 | Fat JAR (JAR ejecutable con todas las dependencias) |
| **Installer** | **JPackage Maven Plugin** | 1.6.0 | Generación de instaladores nativos (EXE, DMG, etc.) |

---

## 🏗️ Arquitectura

El proyecto sigue el patrón **MVC (Modelo-Vista-Controlador)** con servicios especializados:

- **`model/`** — Modelos de datos: `Instance`, `LauncherConfig`, `MinecraftVersion`, `LoaderType`
- **`service/`** — Lógica de negocio:
  - `MojangService` → API de Mojang (descargar versiones)
  - `DownloadService` → Descargas HTTP con progreso
  - `InstanceService` → CRUD de instancias
  - `LaunchService` → Lanzamiento de Minecraft
  - `ForgeService` / `FabricService` → Soporte para mod loaders
  - `ConfigService` → Configuración (JSON)
- **`controller/`** — Controladores JavaFX: `MainController`, `InstancesController`, `CreateInstanceController`
- **`util/`** — Utilidades: `PlatformUtil`, `JsonUtil`, `FileUtil`

---

## ✅ Funcionalidades principales

- **Instancias independientes** con su propio `.minecraft`
- **Soporte multiplataforma** (Windows, macOS, Linux)
- **Descarga automática** de Minecraft Vanilla desde la API de Mojang
- **Soporte para Forge** (descarga instalador, ejecuta, verifica, extrae natives)
- **Interfaz moderna** con temas claro y oscuro
- **Configuración flexible** (RAM, Java personalizable por instancia)
- **Librerías compartidas** para ahorrar espacio en disco
- **Auto-reparación** de librerías faltantes al lanzar

---

## 🚧 En desarrollo / planificado

- Soporte para **Fabric**
- **Autenticación** de Microsoft/Mojang
- Importar/exportar instancias
- Empaquetado nativo (EXE, DMG, AppImage)
