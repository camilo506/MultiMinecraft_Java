# MultiMinecraft Launcher

## Guía de uso y documentación del proyecto

Esta guía explica qué hace MultiMinecraft, cómo se organiza, cómo se crea una instancia, qué opciones ofrece la interfaz y qué ocurre internamente al iniciar Minecraft.

> Esta documentación describe el código actual del proyecto. Algunas funciones dependen de configuración remota y pueden no estar disponibles si el servidor de configuración no las ha publicado.

## 1. Qué es MultiMinecraft

MultiMinecraft es un launcher multiplataforma para gestionar varias instalaciones independientes de Minecraft. Cada instancia tiene su propia configuración, versión, mods, mundos, packs de recursos, shaders, logs e icono.

El aislamiento permite, por ejemplo, mantener una instancia Vanilla para jugar sin mods y otra con Forge para un modpack, sin mezclar archivos entre ambas.

### Características principales

- Instancias independientes de Minecraft.
- Descarga automática de versiones release desde los servicios de Mojang.
- Soporte para Vanilla, Forge y Fabric.
- Descarga y verificación de cliente, librerías e índices de assets.
- Reparación automática de librerías que falten al iniciar.
- Detección de la versión de Java compatible con Minecraft.
- Memoria RAM global y memoria específica por instancia.
- Iconos personalizados y skin local por instancia.
- Gestión de mundos, mods, resource packs y shader packs mediante las carpetas de cada instancia.
- Actualización del launcher y herramientas para instalar modpacks o actualizar mods desde enlaces remotos.
- Interfaz JavaFX con tema oscuro.

## 2. Requisitos

### Para ejecutar el proyecto

- Java 21, porque Maven compila el proyecto con `source` y `target` 21.
- Maven 3.6 o posterior.
- Conexión a Internet para consultar versiones, descargar archivos, comprobar actualizaciones y cargar la configuración remota.
- Un sistema operativo Windows, macOS o Linux. Las rutas se adaptan automáticamente.

### Para jugar

La versión de Java se resuelve según la versión de Minecraft cuando es posible:

| Minecraft | Java mínima aproximada |
|---|---:|
| Versiones antiguas | 8 |
| 1.17 a 1.19 | 17 |
| 1.20.0 a 1.20.4 | 17 |
| 1.20.5 o posterior y 1.21.x | 21 |
| Versiones anuales 25.x | 21 |
| Versiones anuales 26.x o posteriores | 25 |

Se puede usar una ruta de Java específica por instancia si esa propiedad se establece en su configuración. Si no, el launcher intenta encontrar o preparar un runtime compatible y finalmente usa el Java configurado globalmente o el Java del sistema.

## 3. Inicio del launcher

El punto de entrada es `com.multiminecraft.launcher.Main`. Esta clase arranca JavaFX mediante `App`.

Al iniciar:

1. `App` carga la configuración global desde `launcher.json`.
2. Si no existe nombre de jugador, muestra la pantalla de bienvenida.
3. Si ya existe nombre, abre directamente la ventana principal.
4. La ventana se crea con un tamaño inicial de 1080 x 660 píxeles.
5. Se aplica el tema oscuro y se cargan las vistas FXML y sus hojas CSS.

### Primera ejecución

En la pantalla de bienvenida se introduce el nombre del jugador y se pulsa **Continuar**. El nombre se guarda en la configuración global y las siguientes ejecuciones abren la biblioteca principal directamente.

## 4. Ventana principal

La ventana principal está formada por:

- **Barra lateral:** instancia seleccionada, botón para jugar, navegación y perfil del jugador.
- **Banner central:** muestra información de la biblioteca o de la última instancia jugada.
- **Biblioteca de instancias:** tarjetas con las instancias disponibles.
- **Pie de ventana:** progreso de operaciones y enlaces de comunidad/sitio web cuando están configurados.

### Seleccionar y jugar

1. Pulsa una tarjeta de instancia para seleccionarla.
2. Comprueba en la barra lateral el nombre, la versión y el loader.
3. Pulsa **Jugar**.
4. El launcher prepara Java, librerías, assets y natives.
5. Construye el comando de Java usando la configuración de la instancia.
6. Inicia Minecraft con el directorio de esa instancia como directorio de trabajo.

La hora de última ejecución se guarda después de iniciar el proceso. La salida del juego se captura en segundo plano para facilitar el diagnóstico mediante logs.

## 5. Crear una instancia

Pulsa **+ Instancia** en la barra lateral. El formulario permite configurar los siguientes campos.

### Nombre de instancia

Es obligatorio y debe ser único. El nombre también identifica la carpeta de la instancia, por lo que en modo edición no se permite cambiarlo.

### Nombre del jugador

Se precarga el nombre global, pero puede cambiarse para esa instancia. Si se deja vacío al crear, se conserva el valor por defecto del modelo cuando corresponda.

### Versión

El launcher consulta el manifest de Mojang y muestra las versiones de tipo **release** disponibles. La versión seleccionada determina el cliente, las librerías, los assets y la versión mínima de Java.

Si la lista no carga, es necesario comprobar la conexión a Internet y volver a abrir o refrescar el formulario.

### Tipo de instalación

Las opciones actuales son:

- **Vanilla:** instala únicamente el cliente oficial de Minecraft.
- **Forge:** instala primero la base Vanilla y después Forge, incluyendo sus librerías y archivos de versión.
- **Fabric:** instala primero la base Vanilla y después Fabric.

### Memoria RAM

El deslizador permite seleccionar de 1 GB a 16 GB. El valor se guarda como, por ejemplo, `2G` y se utiliza para construir los argumentos `-Xmx` y `-Xms` del proceso de Java.

Como regla práctica, no se debe asignar toda la RAM del equipo a Minecraft. La memoria disponible debe dejar espacio para el sistema operativo y el launcher.

### Icono

**Seleccionar** abre la galería de iconos incluida en los recursos del proyecto. Si no se selecciona otro icono, se copia un icono predeterminado en la carpeta de la instancia.

### Skin

La skin es opcional. Puede almacenarse para una instancia y se prepara al lanzar el juego. El launcher evita sobrescribir la configuración de `CustomSkinLoader` cuando la instancia no tiene una skin local configurada.

### Qué sucede al pulsar Crear Instancia

El flujo interno es:

1. Validar nombre, versión y que no exista otra instancia con el mismo nombre.
2. Crear la carpeta de la instancia y sus directorios de Minecraft.
3. Descargar el JSON de la versión desde Mojang.
4. Descargar y verificar el client JAR mediante SHA-1.
5. Descargar las librerías compatibles con el sistema operativo en paralelo.
6. Descargar el índice de assets.
7. Instalar Forge o Fabric si se seleccionó un loader.
8. Guardar `instance.json`.
9. Copiar el icono predeterminado o guardar el icono elegido.
10. Mostrar el resultado en la biblioteca.

Si la creación falla, el servicio intenta borrar la carpeta incompleta para no dejar una instancia parcialmente instalada.

## 6. Editar y eliminar instancias

### Editar

La opción **Editar** permite modificar:

- Nombre del jugador.
- Versión seleccionada.
- Tipo de loader.
- Memoria RAM.
- Icono.
- Skin.

El nombre de la instancia permanece bloqueado porque coincide con el nombre de su directorio. Si se cambia la versión o el loader, se limpia la versión específica del loader para que pueda resolverse de nuevo.

Editar guarda la configuración, pero no convierte automáticamente todos los archivos de una instalación existente. Para cambios grandes de loader o versión puede ser preferible crear una instancia nueva.

### Eliminar

**Eliminar** solicita confirmación y borra la carpeta completa de la instancia, incluidos mundos, mods, configuraciones, logs e iconos almacenados allí. Esta acción no tiene papelera dentro del launcher; se recomienda hacer copia de los mundos antes de confirmarla.

## 7. Crear el entorno al lanzar

`LaunchService` realiza estas tareas antes de ejecutar Minecraft:

1. Selecciona el nombre de jugador de la instancia.
2. Prepara la skin local si existe.
3. Resuelve una ruta de Java compatible.
4. Selecciona el JSON de versión de Forge o Fabric cuando la instancia usa loader.
5. Verifica y descarga librerías faltantes.
6. Descarga los assets antes de iniciar para evitar fallos por recursos ausentes.
7. Extrae natives necesarias.
8. Construye los argumentos JVM y de juego definidos por Mojang o el loader.
9. Añade la memoria configurada.
10. Aplica ajustes especiales para Forge, incluidos classpath, módulos y lista de exclusión.
11. Ejecuta el proceso con el directorio `.minecraft` de la instancia.

El launcher no espera a que termine Minecraft: inicia el proceso y continúa capturando su salida en un hilo separado.

## 8. Vista de servidor y modpacks

La opción **Servidor** abre una vista con recursos asociados al servidor. Las URLs se cargan desde una configuración remota en GitHub y existen valores de respaldo para algunos recursos.

### Instalar Exiliados ModPack

Descarga e instala el modpack desde la URL remota configurada. Al finalizar, la lista de instancias se refresca y la nueva instancia aparece en la biblioteca.

Si la URL remota no está configurada, la acción se detiene y muestra un aviso.

### Actualizar Mods

1. Selecciona una instancia existente.
2. Confirma la actualización.
3. El servicio elimina los mods marcados para eliminar y copia los nuevos mods desde la descarga configurada.
4. Se muestra el progreso y el resultado.

La opción requiere que exista al menos una instancia y que la URL de mods sea válida.

## 9. Configuración global

La vista **Configuración** permite cambiar:

- **Nombre del jugador:** valor usado como predeterminado.
- **Memoria por defecto:** opciones `1G`, `2G`, `3G`, `4G`, `6G`, `8G`, `10G`, `12G` y `16G`.
- **Actualización del launcher:** consulta la versión remota y, si hay una nueva, descarga y ejecuta el instalador.
- **Acerca de:** versión instalada, desarrollador y sitio web configurado.

Actualmente el código fuerza el tema oscuro al guardar; aunque el modelo conserva una propiedad `theme`, el cambio de tema claro no está habilitado desde la interfaz actual.

El botón de restablecer coloca los valores visuales en sus valores iniciales, pero el texto indica que quedan sin guardar hasta que se pulse guardar.

## 10. Archivos y rutas de datos

La carpeta base depende del sistema operativo:

| Sistema | Carpeta base habitual |
|---|---|
| Windows | `%APPDATA%\\.MultiMinecraft` |
| macOS | `~/Library/Application Support/MultiMinecraft` |
| Linux | `$XDG_DATA_HOME/MultiMinecraft` o `~/.MultiMinecraft` |

Dentro de esa carpeta se utilizan:

```text
.MultiMinecraft/
├── launcher.json              # Configuración global
├── Instancias/
│   └── NombreDeInstancia/
│       ├── instance.json      # Configuración de la instancia
│       ├── versions/
│       ├── libraries/
│       ├── assets/
│       ├── mods/
│       ├── resourcepacks/
│       ├── shaderpacks/
│       ├── saves/
│       ├── screenshots/
│       ├── logs/
│       └── icons/
├── libraries/                 # Librerías compartidas cuando aplica
├── assets/                    # Assets compartidos disponibles
├── skins/                     # Skins almacenadas por el launcher
├── updates/                   # Instaladores descargados para actualizar
└── logs/                      # Logs del launcher
```

El servicio de configuración serializa estos datos como JSON usando Gson. Entre las propiedades importantes de una instancia están `name`, `version`, `loader`, `memory`, `javaPath`, `icon`, `playerName`, `skinPath`, `lastPlayed` y `totalPlaytime`.

## 11. Arquitectura para desarrolladores

El proyecto usa una separación tipo MVC:

```text
JavaFX/FXML
    -> Controllers
        -> Services
            -> Mojang, Forge, Fabric, archivos y procesos del sistema
        -> Models persistidos como JSON
```

### Paquetes principales

- `Main` y `App`: arranque, escena JavaFX, navegación y tema.
- `controller`: controladores de bienvenida, biblioteca, creación/edición, configuración, progreso y servidor.
- `model`: `Instance`, `LauncherConfig`, `MinecraftVersion` y `LoaderType`.
- `service`: configuración, descargas, versiones de Mojang, instancias, lanzamiento, Java, Forge, Fabric, modpacks y actualizaciones.
- `util`: rutas del sistema, operaciones de archivos, JSON y alertas.
- `resources/fxml`: vistas de JavaFX.
- `resources/css`: estilos de las vistas y temas.

### Descargas

Las descargas HTTP se realizan con el servicio de descargas. Las librerías se pueden descargar en paralelo, se escriben mediante archivos temporales y se validan con SHA-1 cuando Mojang proporciona el hash. Esto evita conservar archivos corruptos y permite omitir descargas que ya son válidas.

### Compilar y ejecutar

Desde la raíz del proyecto:

```text
mvn clean package
mvn javafx:run
```

El artefacto empaquetado usa `Main` como clase principal. La configuración Maven incluye JavaFX, Gson, OkHttp, SLF4J/Logback, Commons IO, JUnit y el plugin de Shade para generar un JAR con dependencias.

## 12. Solución de problemas

### La pantalla de bienvenida aparece siempre

Comprueba que `launcher.json` se pueda escribir y que contenga un `playerName` no vacío.

### No aparecen versiones

La lista se obtiene desde Mojang. Comprueba Internet, firewall, proxy y que el servicio de manifest esté disponible.

### Minecraft no inicia

Revisa, en este orden:

1. Que la versión de Java cumpla el requisito de Minecraft.
2. Que existan `versions/<version>/<version>.json` y el client JAR.
3. Que las librerías y natives se hayan descargado correctamente.
4. El log del launcher y la salida del proceso de Minecraft.
5. Que la RAM asignada no sea superior a la memoria disponible.

### Forge o Fabric no inicia

Comprueba que el loader esté instalado dentro de la instancia y que el JSON del loader exista en `versions`. Si se cambió la versión o el loader, vuelve a crear la instancia cuando la instalación anterior haya quedado incompatible.

### Una instalación se interrumpió

La creación intenta limpiar la carpeta fallida. Si quedara una carpeta incompleta, elimina esa instancia desde el launcher o haz una copia de los mundos y borra manualmente su directorio antes de repetir el proceso.

## 13. Limitaciones conocidas

- La autenticación oficial de Microsoft/Mojang no está implementada; el lanzamiento usa el nombre configurado localmente.
- La interfaz aplica tema oscuro aunque el modelo contempla otros valores.
- Las funciones de servidor, modpack, actualizaciones y enlaces dependen de archivos remotos.
- Fabric está integrado en el modelo y en el flujo de instalación, pero su disponibilidad práctica depende de que las URLs y versiones requeridas estén accesibles.
- No existe importación/exportación de instancias desde la interfaz actual.
