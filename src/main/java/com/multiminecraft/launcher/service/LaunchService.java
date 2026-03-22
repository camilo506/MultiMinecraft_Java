package com.multiminecraft.launcher.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.multiminecraft.launcher.model.Instance;
import com.multiminecraft.launcher.util.FileUtil;
import com.multiminecraft.launcher.util.PlatformUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;

/**
 * Servicio para lanzar instancias de Minecraft
 */
public class LaunchService {

    private static final Logger logger = LoggerFactory.getLogger(LaunchService.class);

    private final ConfigService configService;
    private final MojangService mojangService;
    private String currentPlayerName = "Player";

    public LaunchService() {
        this.configService = ConfigService.getInstance();
        this.mojangService = new MojangService();
    }

    /**
     * Lanza una instancia de Minecraft
     */
    public Process launchInstance(Instance instance) throws Exception {
        // Establecer nombre del jugador desde la instancia
        String pName = instance.getPlayerName();
        this.currentPlayerName = (pName != null && !pName.trim().isEmpty()) ? pName.trim() : "Player";
        logger.info("Lanzando instancia: {}", instance.getName());

        Path minecraftDir = configService.getInstanceMinecraftDirectory(instance.getName());
        String baseVersion = instance.getVersion();

        // Si es una instancia Forge o Fabric, buscar la versión del loader instalada
        // IMPORTANTE: Para loaders, NO usamos el JSON de vanilla, sino el JSON del
        // loader
        // El JSON tiene inheritsFrom y mainClass propios
        String version = baseVersion;
        if (instance.getLoader() == com.multiminecraft.launcher.model.LoaderType.FORGE) {
            String forgeVersion = findForgeVersion(minecraftDir, baseVersion);
            if (forgeVersion != null) {
                logger.info("Versión Forge encontrada: {}", forgeVersion);
                version = forgeVersion;
            } else {
                logger.warn("No se encontró versión Forge para {}, usando versión Vanilla", baseVersion);
            }
        } else if (instance.getLoader() == com.multiminecraft.launcher.model.LoaderType.FABRIC) {
            String fabricVersion = findFabricVersion(minecraftDir, baseVersion);
            if (fabricVersion != null) {
                logger.info("Versión Fabric encontrada: {}", fabricVersion);
                version = fabricVersion;
            } else {
                logger.warn("No se encontró versión Fabric para {}, usando versión Vanilla", baseVersion);
            }
        }

        // Leer el JSON de la versión (operación rápida)
        // Para Forge, esto carga el JSON de Forge (1.20.1-forge-47.4.10.json)
        // NO el JSON de vanilla (1.20.1.json)
        final String finalVersion = version;
        Path versionJsonPath = minecraftDir.resolve("versions").resolve(finalVersion).resolve(finalVersion + ".json");
        if (!Files.exists(versionJsonPath)) {
            throw new IOException("No se encontró el archivo de versión: " + versionJsonPath);
        }

        String versionJson = FileUtil.readFileAsString(versionJsonPath);
        JsonObject versionData = JsonParser.parseString(versionJson).getAsJsonObject();

        // El versionData ahora contiene todos los datos del JSON de Forge:
        // - versionData.get("mainClass") -> MainClass de Forge (ej:
        // cpw.mods.bootstraplauncher.BootstrapLauncher)
        // - versionData.get("arguments") -> Argumentos JVM y game de Forge
        // - versionData.get("libraries") -> Librerías Forge + ModLauncher
        // - versionData.get("inheritsFrom") -> Versión base (ej: "1.20.1")
        logger.debug("JSON de versión cargado: {} (Forge: {})", finalVersion,
                instance.getLoader() == com.multiminecraft.launcher.model.LoaderType.FORGE);

        // Verificar y descargar librerías faltantes (Auto-repair)
        verifyAndDownloadLibraries(versionData, minecraftDir);

        // Descargar assets ANTES de lanzar (CRÍTICO: Sin assets, Minecraft crashea
        // inmediatamente)
        // Para Forge, el assetIndex está en el JSON padre (vanilla), no en el hijo
        JsonObject assetVersionData = versionData;
        if (!versionData.has("assetIndex") && versionData.has("inheritsFrom")) {
            String parentVersionId = versionData.get("inheritsFrom").getAsString();
            try {
                Path parentJsonPath = minecraftDir.resolve("versions").resolve(parentVersionId)
                        .resolve(parentVersionId + ".json");
                if (Files.exists(parentJsonPath)) {
                    String parentJson = FileUtil.readFileAsString(parentJsonPath);
                    assetVersionData = JsonParser.parseString(parentJson).getAsJsonObject();
                    logger.info("Usando assetIndex del padre {} para descarga de assets", parentVersionId);
                }
            } catch (Exception e) {
                logger.warn("No se pudo cargar versión padre para assets: {}", parentVersionId, e);
            }
        }
        try {
            logger.info("Descargando assets antes de lanzar para versión {}", finalVersion);
            mojangService.downloadAssets(assetVersionData, minecraftDir, status -> {
                logger.info("Assets: {}", status);
            });
        } catch (Exception e) {
            logger.warn("Error descargando assets (se intentará continuar): {}", e.getMessage());
        }

        // Extraer natives (CRÍTICO: Sin esto falla con UnsatisfiedLinkError)
        extractNatives(versionData, minecraftDir, finalVersion);

        // Construir comando de lanzamiento (operación rápida)
        List<String> command = buildLaunchCommand(instance, minecraftDir, versionData, finalVersion);

        // IMPORTANTE: Filtrar argumentos de módulos que causan problemas con Forge
        // PERO preservar --add-opens y --add-exports de java.base que son necesarios
        command = filterModuleArguments(command, versionData);

        // Verificar y asegurar que todas las librerías ASM necesarias estén en el
        // module path
        boolean hasModulePath = command.stream().anyMatch(arg -> arg.equals("-p") || arg.equals("--module-path"));
        if (hasModulePath) {
            ensureForgeModulesInModulePath(command, versionData, minecraftDir);
        }

        // Filtrar argumentos que activan modo demo (seguridad adicional)
        command = filterDemoArguments(command);

        // Loggear la línea de ejecución completa para debugging
        String fullCommand = String.join(" ", command);
        logger.info("Línea de ejecución completa: {}", fullCommand);
        logger.debug("Comando desglosado ({} argumentos):", command.size());
        for (int i = 0; i < command.size(); i++) {
            logger.debug("  [{}] {}", i, command.get(i));
        }

        // Crear y lanzar proceso inmediatamente
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(minecraftDir.toFile());
        processBuilder.redirectErrorStream(true);

        // Lanzar proceso inmediatamente (esta es la operación crítica)
        Process process = processBuilder.start();

        logger.info("Proceso de Minecraft iniciado para instancia: {}", instance.getName());

        // Operaciones en segundo plano (no bloquean el lanzamiento)

        // Capturar salida en un thread separado
        new Thread(() -> captureOutput(process, instance.getName())).start();

        // Actualizar última vez jugado en segundo plano
        new Thread(() -> {
            try {
                instance.setLastPlayed(LocalDateTime.now());
                configService.saveInstanceConfig(instance);
            } catch (Exception e) {
                logger.warn("No se pudo guardar la última vez jugado", e);
            }
        }).start();

        // Descargar assets ya no es necesario aquí (se hace antes del lanzamiento)

        return process;
    }

    /**
     * Construye el comando de lanzamiento
     */
    private List<String> buildLaunchCommand(Instance instance, Path minecraftDir, JsonObject versionData,
            String versionId) {
        List<String> command = new ArrayList<>();

        // Java executable
        String javaPath = configService.getJavaPath(instance);
        command.add(javaPath);

        // JVM Arguments
        String memory = configService.getMemory(instance);
        command.add("-Xmx" + memory);
        command.add("-Xms" + parseMinMemory(memory));

        // IMPORTANTE: Agregar flags necesarios para Forge ANTES de otros argumentos JVM
        // Estos flags deben estar al inicio para que Forge pueda acceder a módulos
        // internos
        if (isForgeInstance(versionId, versionData)) {
            addForgeRequiredJvmArgs(command);
            logger.info("Agregados flags JVM requeridos para Forge al inicio del comando");
        }

        // Cargar versión padre si existe (para herencia de argumentos)
        JsonObject parentData = null;
        if (versionData.has("inheritsFrom")) {
            String parentVersionId = versionData.get("inheritsFrom").getAsString();
            try {
                Path parentJsonPath = minecraftDir.resolve("versions").resolve(parentVersionId)
                        .resolve(parentVersionId + ".json");
                if (Files.exists(parentJsonPath)) {
                    String parentJson = FileUtil.readFileAsString(parentJsonPath);
                    parentData = JsonParser.parseString(parentJson).getAsJsonObject();
                }
            } catch (Exception e) {
                logger.warn("Error al cargar versión padre para argumentos", e);
            }
        }

        // 1. Argumentos JVM
        // Primero padre
        if (parentData != null) {
            addArguments(command, parentData, "jvm", minecraftDir, versionData, false); // versionData (hijo) provee el
                                                                                        // contexto
        }
        // Luego hijo (actual)
        addArguments(command, versionData, "jvm", minecraftDir, versionData, false);

        // IMPORTANTE: Para Forge, asegurar que usamos -cp (classpath) en lugar de
        // módulos
        // El problema es que la JVM está recibiendo flags relacionados con el sistema
        // de módulos
        // (--add-modules, --module-path, --module) que causan conflictos.
        // La solución es NO pasar opciones de módulo a Forge y construir correctamente
        // el classpath.

        // Verificar si ya hay -cp en el comando
        boolean hasClasspath = command.stream().anyMatch(arg -> arg.equals("-cp") || arg.equals("-classpath"));

        // Verificar si hay -p (module path) - NO podemos usar -cp y -p al mismo tiempo
        boolean hasModulePath = command.stream().anyMatch(arg -> arg.equals("-p") || arg.equals("--module-path"));

        // Si no hay classpath y no hay argumentos modernos, usar legacy (que incluye
        // -cp)
        if (!hasClasspath && !hasModulePath && command.size() <= 3) { // Solo tiene Java y memoria
            addLegacyJvmArguments(command, minecraftDir, versionData);
        } else if (!hasClasspath /* && !hasModulePath eliminado para Forge 1.20+ */
                && instance.getLoader() == com.multiminecraft.launcher.model.LoaderType.FORGE) {
            // Para Forge, si no hay -cp, agregarlo AUNQUE haya -p (module path)
            // Forge 1.20+ necesita AMBOS: -p para librerías y -cp para el BootstrapLauncher
            int mainClassIndex = -1;
            for (int i = 0; i < command.size(); i++) {
                String arg = command.get(i);
                if (arg.contains(".") && !arg.startsWith("-") && !arg.startsWith("${") &&
                        !arg.contains("=") && !arg.contains("/")) {
                    mainClassIndex = i;
                    break;
                }
            }

            if (mainClassIndex > 0) {
                String classpath = buildClasspath(minecraftDir, versionData);
                command.add(mainClassIndex, classpath);
                command.add(mainClassIndex, "-cp");
                logger.info("Agregado -cp (classpath) para Forge antes de main class (incluso con module path)");
            }
        } else if (hasModulePath) {
            logger.debug("Detectado -p (module path) - verificado si es necesario agregar -cp");
        }

        // Main class (Prioridad al hijo)
        String mainClass;
        if (versionData.has("mainClass")) {
            mainClass = versionData.get("mainClass").getAsString();
        } else if (parentData != null && parentData.has("mainClass")) {
            mainClass = parentData.get("mainClass").getAsString();
        } else {
            mainClass = "net.minecraft.client.main.Main"; // Fallback
        }
        command.add(mainClass);

        // 2. Game arguments
        // Primero padre
        if (parentData != null) {
            // Si el hijo tiene minecraftArguments (legacy), NO usar los del padre
            // (comportamiento de reemplazo)
            boolean childHasLegacy = versionData.has("minecraftArguments");
            addArguments(command, parentData, "game", minecraftDir, versionData, childHasLegacy);
        }
        // Luego hijo
        addArguments(command, versionData, "game", minecraftDir, versionData, false);

        return command;
    }

    private void addArguments(List<String> command, JsonObject sourceData, String type, Path minecraftDir,
            JsonObject contextData, boolean skipLegacy) {
        if (sourceData.has("arguments") && sourceData.getAsJsonObject("arguments").has(type)) {
            JsonArray args = sourceData.getAsJsonObject("arguments").getAsJsonArray(type);
            addArgumentsFromJson(command, args, minecraftDir, contextData);
        } else if (!skipLegacy && type.equals("game") && sourceData.has("minecraftArguments")) {
            // Legacy string arguments
            String minecraftArguments = sourceData.get("minecraftArguments").getAsString();
            String[] args = minecraftArguments.split(" ");
            for (String arg : args) {
                command.add(replaceVariables(arg, minecraftDir, contextData));
            }
        }
    }

    /**
     * Agrega argumentos desde el JSON
     */
    private void addArgumentsFromJson(List<String> command, JsonArray argsArray, Path minecraftDir,
            JsonObject versionData) {
        for (int i = 0; i < argsArray.size(); i++) {
            var element = argsArray.get(i);

            if (element.isJsonPrimitive()) {
                String arg = element.getAsString();
                String processedArg = replaceVariables(arg, minecraftDir, versionData);

                // Filtrar argumentos que activan modo demo
                if (processedArg.equals("--demo") || processedArg.equals("--demo-mode")) {
                    logger.debug("Eliminado argumento que activa modo demo: {}", processedArg);
                    continue;
                }

                // Filtrar variables no resueltas ${...} que causan crashes
                if (processedArg.contains("${")) {
                    logger.debug("Eliminado argumento con variable no resuelta: {}", processedArg);
                    continue;
                }

                // Filtrar argumentos vacíos resultantes de variables reemplazadas por ""
                if (processedArg.isEmpty()) {
                    // Si el argumento anterior era un flag (--xxx), removerlo también
                    if (!command.isEmpty() && command.get(command.size() - 1).startsWith("--")) {
                        String removedFlag = command.remove(command.size() - 1);
                        logger.debug("Eliminado flag sin valor: {} (valor era vacío)", removedFlag);
                    }
                    continue;
                }

                // Si es --add-opens o --add-exports sin valor (sin =), combinar con el
                // siguiente elemento
                if ((processedArg.equals("--add-opens") || processedArg.equals("--add-exports")) &&
                        !processedArg.contains("=") && i + 1 < argsArray.size()) {
                    var nextElement = argsArray.get(i + 1);
                    if (nextElement.isJsonPrimitive()) {
                        String nextArg = replaceVariables(nextElement.getAsString(), minecraftDir, versionData);
                        // Combinar: --add-opens + " " + valor
                        command.add(processedArg + "=" + nextArg);
                        i++; // Saltar el siguiente elemento ya que lo combinamos
                        logger.debug("Combinado argumento de módulo: {}={}", processedArg, nextArg);
                        continue;
                    }
                }

                command.add(processedArg);
            } else if (element.isJsonObject()) {
                // Argumentos condicionales (rules)
                JsonObject argObj = element.getAsJsonObject();

                // Reutilizamos la lógica de filtrado de librerías ya que la estructura "rules"
                // es idéntica
                if (shouldIncludeLibrary(argObj)) {
                    if (argObj.has("value")) {
                        var value = argObj.get("value");
                        if (value.isJsonPrimitive()) {
                            String val = replaceVariables(value.getAsString(), minecraftDir, versionData);
                            command.add(val);
                        } else if (value.isJsonArray()) {
                            JsonArray valueArray = value.getAsJsonArray();
                            // Procesar array con lógica de combinación para --add-opens/--add-exports
                            for (int j = 0; j < valueArray.size(); j++) {
                                var valElement = valueArray.get(j);
                                if (valElement.isJsonPrimitive()) {
                                    String val = replaceVariables(valElement.getAsString(), minecraftDir, versionData);

                                    // Filtrar argumentos que activan modo demo
                                    if (val.equals("--demo") || val.equals("--demo-mode")) {
                                        logger.debug("Eliminado argumento que activa modo demo desde array: {}", val);
                                        continue;
                                    }

                                    // Si es --add-opens o --add-exports sin valor, combinar con el siguiente
                                    if ((val.equals("--add-opens") || val.equals("--add-exports")) &&
                                            !val.contains("=") && j + 1 < valueArray.size()) {
                                        var nextValElement = valueArray.get(j + 1);
                                        if (nextValElement.isJsonPrimitive()) {
                                            String nextVal = replaceVariables(nextValElement.getAsString(),
                                                    minecraftDir, versionData);
                                            command.add(val + "=" + nextVal);
                                            j++; // Saltar el siguiente elemento
                                            logger.debug("Combinado argumento de módulo desde array: {}={}", val,
                                                    nextVal);
                                            continue;
                                        }
                                    }

                                    command.add(val);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Agrega argumentos JVM para versiones antiguas
     */
    private void addLegacyJvmArguments(List<String> command, Path minecraftDir, JsonObject versionData) {
        String version = versionData.get("id").getAsString();
        Path nativesDir = minecraftDir.resolve("versions").resolve(version).resolve("natives");

        command.add("-Djava.library.path=" + nativesDir.toString());
        command.add("-cp");
        command.add(buildClasspath(minecraftDir, versionData));
    }

    /**
     * Construye el classpath
     */
    private String buildClasspath(Path minecraftDir, JsonObject versionData) {
        List<String> classpathEntries = new ArrayList<>();
        String version = versionData.get("id").getAsString();

        // 1. Agregar librerías de la versión actual
        addLibraries(versionData, classpathEntries, minecraftDir);

        // 2. Manejar herencia (inheritsFrom) para librerías
        if (versionData.has("inheritsFrom")) {
            String parentVersionId = versionData.get("inheritsFrom").getAsString();
            try {
                Path parentJsonPath = minecraftDir.resolve("versions").resolve(parentVersionId)
                        .resolve(parentVersionId + ".json");
                if (Files.exists(parentJsonPath)) {
                    String parentJson = FileUtil.readFileAsString(parentJsonPath);
                    JsonObject parentData = JsonParser.parseString(parentJson).getAsJsonObject();
                    addLibraries(parentData, classpathEntries, minecraftDir);
                    logger.debug("Librerías de versión heredada {} agregadas", parentVersionId);
                } else {
                    logger.warn("No se encontró el archivo JSON de la versión heredada: {}", parentJsonPath);
                }
            } catch (Exception e) {
                logger.warn("Error al procesar herencia de librerías para: " + parentVersionId, e);
            }
        }

        // 3. Agregar Client JAR(s)
        addClientJar(minecraftDir, versionData, classpathEntries);

        String separator = PlatformUtil.getOS() == PlatformUtil.OS.WINDOWS ? ";" : ":";
        String classpath = String.join(separator, classpathEntries);

        logger.info("Classpath construido ({} entradas) para versión {}", classpathEntries.size(), version);
        if (logger.isDebugEnabled()) {
            logger.debug("Classpath completo: {}", classpath);
        }
        return classpath;
    }

    private void addLibraries(JsonObject versionData, List<String> classpathEntries, Path minecraftDir) {
        if (!versionData.has("libraries"))
            return;

        JsonArray libraries = versionData.getAsJsonArray("libraries");
        Path sharedLibrariesDir = PlatformUtil.getSharedLibrariesDirectory();

        for (int i = 0; i < libraries.size(); i++) {
            JsonObject library = libraries.get(i).getAsJsonObject();

            if (library.has("rules")) {
                if (!shouldIncludeLibrary(library)) {
                    continue;
                }
            }

            String libraryPathStr = null;
            if (library.has("downloads") && library.getAsJsonObject("downloads").has("artifact")) {
                JsonObject artifact = library.getAsJsonObject("downloads").getAsJsonObject("artifact");
                if (artifact.has("path")) {
                    libraryPathStr = artifact.get("path").getAsString();
                }
            }

            // Fallback: usar coordenadas Maven si no hay path explícito
            if (libraryPathStr == null && library.has("name")) {
                libraryPathStr = getPathFromName(library.get("name").getAsString());
            }

            if (libraryPathStr != null) {
                Path libraryPath = sharedLibrariesDir.resolve(libraryPathStr);

                // Buscar en instancia si no está en compartidos
                if (!Files.exists(libraryPath)) {
                    Path instanceLibraryPath = minecraftDir.resolve("libraries").resolve(libraryPathStr);
                    if (Files.exists(instanceLibraryPath)) {
                        libraryPath = instanceLibraryPath;
                    }
                }

                String entry = libraryPath.toString();
                if (!classpathEntries.contains(entry)) {
                    classpathEntries.add(entry);
                }
            }
        }
    }

    private String getPathFromName(String name) {
        String[] parts = name.split(":");
        if (parts.length < 3)
            return null;
        String domain = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        return domain + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".jar";
    }

    private void addClientJar(Path minecraftDir, JsonObject versionData, List<String> classpathEntries) {
        String version = versionData.get("id").getAsString();
        Path clientJar = minecraftDir.resolve("versions").resolve(version).resolve(version + ".jar");

        if (Files.exists(clientJar)) {
            classpathEntries.add(clientJar.toString());
        } else {
            // Lógica de herencia de JAR
            boolean jarFound = false;

            if (versionData.has("jar")) {
                String jarVersion = versionData.get("jar").getAsString();
                Path inheritedJar = minecraftDir.resolve("versions").resolve(jarVersion).resolve(jarVersion + ".jar");
                if (Files.exists(inheritedJar)) {
                    classpathEntries.add(inheritedJar.toString());
                    jarFound = true;
                    logger.debug("Usando JAR heredado (campo 'jar'): {}", inheritedJar);
                }
            }

            if (!jarFound && versionData.has("inheritsFrom")) {
                String inheritedVersion = versionData.get("inheritsFrom").getAsString();
                Path inheritedJar = minecraftDir.resolve("versions").resolve(inheritedVersion)
                        .resolve(inheritedVersion + ".jar");
                if (Files.exists(inheritedJar)) {
                    classpathEntries.add(inheritedJar.toString());
                    jarFound = true;
                    logger.debug("Usando JAR heredado (inheritsFrom): {}", inheritedJar);
                }
            }

            if (!jarFound) {
                logger.warn("Client JAR no encontrado para {} ni versiones heredadas", version);
            }
        }
    }

    /**
     * Verifica si una librería debe ser incluida según las reglas del sistema
     * operativo
     */
    private boolean shouldIncludeLibrary(JsonObject library) {
        JsonArray rules = library.getAsJsonArray("rules");
        boolean shouldInclude = false;

        for (int i = 0; i < rules.size(); i++) {
            JsonObject rule = rules.get(i).getAsJsonObject();
            String action = rule.has("action") ? rule.get("action").getAsString() : "allow";

            // Reglas con "features" (has_custom_resolution, is_demo_user, quick_play, etc.)
            // No soportamos ninguna de estas features, así que las excluimos
            if (rule.has("features")) {
                // Si la acción es "allow" y requiere una feature, NO incluir
                // porque no tenemos esas features activas
                if ("allow".equals(action)) {
                    return false;
                }
                // Si la acción es "disallow" y requiere una feature, SÍ incluir
                shouldInclude = true;
                continue;
            }

            if (rule.has("os")) {
                JsonObject os = rule.getAsJsonObject("os");
                String osName = os.has("name") ? os.get("name").getAsString() : null;
                PlatformUtil.OS currentOS = PlatformUtil.getOS();

                boolean matchesOS = false;
                if (osName != null) {
                    switch (osName) {
                        case "windows":
                            matchesOS = (currentOS == PlatformUtil.OS.WINDOWS);
                            break;
                        case "osx":
                        case "macos":
                            matchesOS = (currentOS == PlatformUtil.OS.MACOS);
                            break;
                        case "linux":
                            matchesOS = (currentOS == PlatformUtil.OS.LINUX);
                            break;
                    }
                } else {
                    matchesOS = true; // Sin restricción de OS
                }

                if (matchesOS) {
                    shouldInclude = "allow".equals(action);
                }
            } else {
                // Regla sin restricción de OS ni features
                shouldInclude = "allow".equals(action);
            }
        }

        return shouldInclude;
    }

    /**
     * Verifica y descarga librerías faltantes (Auto-repair)
     * Busca primero en el directorio compartido, luego en el de la instancia
     */
    private void verifyAndDownloadLibraries(JsonObject versionData, Path minecraftDir) {
        try {
            // Verificar si hay librerías faltantes
            if (!versionData.has("libraries")) {
                return;
            }

            JsonArray libraries = versionData.getAsJsonArray("libraries");
            Path sharedLibrariesDir = PlatformUtil.getSharedLibrariesDirectory();
            Path instanceLibrariesDir = minecraftDir.resolve("libraries");
            boolean needsDownload = false;

            // Verificar cada librería
            for (int i = 0; i < libraries.size(); i++) {
                JsonObject library = libraries.get(i).getAsJsonObject();

                // Verificar reglas
                if (library.has("rules")) {
                    if (!shouldIncludeLibrary(library)) {
                        continue;
                    }
                }

                String libraryPathStr = null;
                if (library.has("downloads") && library.getAsJsonObject("downloads").has("artifact")) {
                    JsonObject artifact = library.getAsJsonObject("downloads").getAsJsonObject("artifact");
                    if (artifact.has("path")) {
                        libraryPathStr = artifact.get("path").getAsString();
                    }
                }

                // Fallback: usar coordenadas Maven
                if (libraryPathStr == null && library.has("name")) {
                    libraryPathStr = getPathFromName(library.get("name").getAsString());
                }

                if (libraryPathStr != null) {
                    Path sharedLibraryPath = sharedLibrariesDir.resolve(libraryPathStr);
                    Path instanceLibraryPath = instanceLibrariesDir.resolve(libraryPathStr);

                    // Verificar si existe en alguno de los dos directorios
                    if (!Files.exists(sharedLibraryPath) && !Files.exists(instanceLibraryPath)) {
                        needsDownload = true;
                        break; // Solo necesitamos saber si falta alguna
                    }
                }
            }

            // Si faltan librerías, descargarlas
            // PRIORIDAD: Descargar primero al directorio COMPARTIDO para evitar duplicados
            // Solo usar el directorio de instancia si hay problemas con el compartido
            if (needsDownload) {
                logger.info("Se detectaron librerías faltantes, iniciando descarga...");
                try {
                    logger.info("Descargando librerías al directorio compartido: {}", sharedLibrariesDir);
                    mojangService.downloadLibraries(versionData, sharedLibrariesDir);
                    logger.info("Librerías descargadas al directorio compartido");
                } catch (Exception e) {
                    logger.warn("Error al descargar al directorio compartido, intentando directorio de instancia: {}",
                            e.getMessage());
                    // Fallback: intentar descargar al directorio de instancia si falla el
                    // compartido
                    try {
                        mojangService.downloadLibraries(versionData, instanceLibrariesDir);
                        logger.info("Librerías descargadas al directorio de instancia (fallback)");
                    } catch (Exception e2) {
                        logger.error(
                                "Error crítico al descargar librerías: {}. El juego puede no iniciar correctamente.",
                                e2.getMessage());
                        // No lanzamos excepción para intentar lanzar el juego de todos modos
                        // pero registramos el error para debugging
                    }
                }
            }

            // Manejar herencia (inheritsFrom) si existe
            if (versionData.has("inheritsFrom")) {
                String parentVersionId = versionData.get("inheritsFrom").getAsString();
                try {
                    Path parentJsonPath = minecraftDir.resolve("versions").resolve(parentVersionId)
                            .resolve(parentVersionId + ".json");
                    if (Files.exists(parentJsonPath)) {
                        String parentJson = FileUtil.readFileAsString(parentJsonPath);
                        JsonObject parentData = JsonParser.parseString(parentJson).getAsJsonObject();
                        verifyAndDownloadLibraries(parentData, minecraftDir);
                    }
                } catch (Exception e) {
                    logger.warn("Error al verificar/descargar librerías de versión heredada: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("Error al verificar/descargar librerías: {}", e.getMessage());
            // No lanzamos excepción para intentar lanzar el juego de todos modos
        }
    }

    /**
     * Extrae las librerías nativas a la carpeta "natives"
     */
    private void extractNatives(JsonObject versionData, Path minecraftDir, String versionId) {
        if (!versionData.has("libraries"))
            return;

        // Carpeta de destino: versions/{version}/natives
        Path nativesDir = minecraftDir.resolve("versions").resolve(versionId).resolve("natives");
        try {
            // Asegurar que existe y limpiar (opcional, por ahora solo crear)
            FileUtil.createDirectory(nativesDir);
        } catch (IOException e) {
            logger.warn("Error al crear directorio natives: {}", e.getMessage());
        }

        JsonArray libraries = versionData.getAsJsonArray("libraries");
        Path sharedLibrariesDir = PlatformUtil.getSharedLibrariesDirectory();

        for (int i = 0; i < libraries.size(); i++) {
            JsonObject library = libraries.get(i).getAsJsonObject();

            // Verificar reglas
            if (library.has("rules")) {
                if (!shouldIncludeLibrary(library)) {
                    continue;
                }
            }

            // Verificar si tiene natives para el OS actual
            if (library.has("natives")) {
                JsonObject natives = library.getAsJsonObject("natives");
                String osName = "linux";
                switch (PlatformUtil.getOS()) {
                    case WINDOWS:
                        osName = "windows";
                        break;
                    case MACOS:
                        osName = "osx";
                        break;
                    case LINUX:
                        osName = "linux";
                        break;
                    default:
                        osName = "linux";
                        break;
                }

                if (natives.has(osName)) {
                    String manualClassifier = natives.get(osName).getAsString();
                    // Reemplazar ${arch} si es necesario
                    if (manualClassifier.contains("${arch}")) {
                        String arch = System.getProperty("os.arch").contains("64") ? "64" : "32";
                        manualClassifier = manualClassifier.replace("${arch}", arch);
                    }

                    // Buscar el artifact con este classifier en "downloads"
                    String extractPath = null;

                    if (library.has("downloads") && library.getAsJsonObject("downloads").has("classifiers")) {
                        JsonObject classifiers = library.getAsJsonObject("downloads").getAsJsonObject("classifiers");
                        if (classifiers.has(manualClassifier)) {
                            extractPath = classifiers.getAsJsonObject(manualClassifier).get("path").getAsString();
                        }
                    }

                    // Fallback: construir path Maven si no hay downloads explícitos
                    if (extractPath == null && library.has("name")) {
                        String name = library.get("name").getAsString();
                        String[] parts = name.split(":");
                        if (parts.length >= 3) {
                            String domain = parts[0].replace('.', '/');
                            String artifact = parts[1];
                            String version = parts[2];
                            extractPath = domain + "/" + artifact + "/" + version + "/" + artifact + "-" + version + "-"
                                    + manualClassifier + ".jar";
                        }
                    }

                    if (extractPath != null) {
                        Path libPath = sharedLibrariesDir.resolve(extractPath);
                        if (Files.exists(libPath)) {
                            logger.debug("Extrayendo natives desde: {}", libPath);
                            extractJar(libPath, nativesDir);
                        } else {
                            logger.warn("Native JAR no encontrado: {}", libPath);
                        }
                    }
                }
            }
        }

        // Procesar herencia
        if (versionData.has("inheritsFrom")) {
            String parentVersionId = versionData.get("inheritsFrom").getAsString();
            try {
                Path parentJsonPath = minecraftDir.resolve("versions").resolve(parentVersionId)
                        .resolve(parentVersionId + ".json");
                if (Files.exists(parentJsonPath)) {
                    String parentJson = FileUtil.readFileAsString(parentJsonPath);
                    JsonObject parentData = JsonParser.parseString(parentJson).getAsJsonObject();
                    extractNatives(parentData, minecraftDir, versionId); // Extraer en la carpeta del HIJO
                }
            } catch (Exception e) {
                logger.warn("Error processing inherited natives", e);
            }
        }
    }

    private void extractJar(Path jarPath, Path preDir) {
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || entry.getName().startsWith("META-INF")) {
                    continue;
                }

                Path target = preDir.resolve(entry.getName());
                if (Files.exists(target))
                    continue; // Ya existe

                try (var is = zip.getInputStream(entry)) {
                    Files.copy(is, target);
                } catch (IOException e) {
                    // Ignorar errores de copia (probablemente archivo en uso)
                }
            }
        } catch (Exception e) {
            logger.warn("Error extrayendo jar: {}", e.getMessage());
        }
    }

    /**
     * Reemplaza variables en los argumentos
     */
    private String replaceVariables(String arg, Path minecraftDir, JsonObject versionData) {
        String version = versionData.has("id") ? versionData.get("id").getAsString() : "unknown";

        String assetIndex = "legacy";
        if (versionData.has("assetIndex") && versionData.get("assetIndex").isJsonObject()) {
            JsonObject ai = versionData.getAsJsonObject("assetIndex");
            if (ai.has("id")) {
                assetIndex = ai.get("id").getAsString();
            }
        } else if (versionData.has("inheritsFrom")) {
            // Para versiones con herencia (ej. Forge), buscar assetIndex en el padre
            String parentVersionId = versionData.get("inheritsFrom").getAsString();
            try {
                Path parentJsonPath = minecraftDir.resolve("versions").resolve(parentVersionId)
                        .resolve(parentVersionId + ".json");
                if (Files.exists(parentJsonPath)) {
                    String parentJson = FileUtil.readFileAsString(parentJsonPath);
                    JsonObject parentData = JsonParser.parseString(parentJson).getAsJsonObject();
                    if (parentData.has("assetIndex") && parentData.get("assetIndex").isJsonObject()) {
                        JsonObject ai = parentData.getAsJsonObject("assetIndex");
                        if (ai.has("id")) {
                            assetIndex = ai.get("id").getAsString();
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("No se pudo leer assetIndex del padre {}", parentVersionId, e);
            }
        }

        // Determinar separador de classpath según OS
        String classpathSeparator = PlatformUtil.getOS() == PlatformUtil.OS.WINDOWS ? ";" : ":";

        // Determinar directorio de librerías (prioridad: compartido, luego instancia)
        // Forge espera que ${library_directory} apunte al directorio donde están las
        // librerías
        Path libraryDirectory = PlatformUtil.getSharedLibrariesDirectory();

        // Generar UUID único para evitar modo demo (usar hash del nombre del jugador)
        String playerName = currentPlayerName;
        String playerUuid = generateOfflineUUID(playerName);
        // Generar token de acceso más realista para evitar modo demo
        String accessToken = generateOfflineAccessToken(playerName, playerUuid);
        // Para versiones modernas, usar "mojang" en lugar de "legacy" para evitar modo
        // demo
        String userType = determineUserType(version);

        return arg
                .replace("${auth_player_name}", playerName)
                .replace("${version_name}", version)
                .replace("${game_directory}", minecraftDir.toString())
                .replace("${assets_root}", minecraftDir.resolve("assets").toString())
                .replace("${assets_index_name}", assetIndex)
                .replace("${auth_uuid}", playerUuid)
                .replace("${auth_access_token}", accessToken)
                .replace("${user_type}", userType)
                .replace("${version_type}", versionData.has("type") ? versionData.get("type").getAsString() : "release")
                .replace("${natives_directory}",
                        minecraftDir.resolve("versions").resolve(version).resolve("natives").toString())
                .replace("${library_directory}", libraryDirectory.toString())
                .replace("${classpath_separator}", classpathSeparator)
                .replace("${launcher_name}", "MultiMinecraft")
                .replace("${launcher_version}", "1.0")
                .replace("${classpath}", buildClasspath(minecraftDir, versionData))
                .replace("${clientid}", "")
                .replace("${auth_xuid}", "")
                .replace("${resolution_width}", "854")
                .replace("${resolution_height}", "480")
                .replace("${quickPlayPath}", "")
                .replace("${quickPlaySingleplayer}", "")
                .replace("${quickPlayMultiplayer}", "")
                .replace("${quickPlayRealms}", "");
    }

    /**
     * Parsea la memoria mínima (la mitad de la máxima)
     */
    private String parseMinMemory(String maxMemory) {
        try {
            int value = Integer.parseInt(maxMemory.replaceAll("[^0-9]", ""));
            int minValue = Math.max(512, value / 2);
            return minValue + "M";
        } catch (NumberFormatException e) {
            return "512M";
        }
    }

    /**
     * Captura la salida del proceso
     */
    private void captureOutput(Process process, String instanceName) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.debug("[{}] {}", instanceName, line);
            }
        } catch (IOException e) {
            logger.error("Error al capturar salida del proceso", e);
        }
    }

    /**
     * Busca la versión de Forge instalada para una versión de Minecraft
     * Mejora: Busca también variaciones del formato (como en Python)
     */
    private String findForgeVersion(Path minecraftDir, String minecraftVersion) {
        try {
            Path versionsDir = minecraftDir.resolve("versions");
            if (!Files.exists(versionsDir)) {
                return null;
            }

            // Patrones de búsqueda (múltiples formatos posibles)
            List<String> searchPatterns = new ArrayList<>();
            searchPatterns.add(minecraftVersion + "-forge-"); // Formato estándar: 1.20.1-forge-47.4.10
            searchPatterns.add(minecraftVersion + "-"); // Formato alternativo: 1.20.1-47.4.10

            try (var stream = Files.list(versionsDir)) {
                List<String> allDirs = stream
                        .filter(Files::isDirectory)
                        .map(path -> path.getFileName().toString())
                        .collect(java.util.stream.Collectors.toList());

                // Buscar con cada patrón
                for (String pattern : searchPatterns) {
                    String found = allDirs.stream()
                            .filter(name -> name.startsWith(pattern))
                            .findFirst()
                            .orElse(null);

                    if (found != null) {
                        logger.debug("Versión Forge encontrada con patrón '{}': {}", pattern, found);
                        return found;
                    }
                }

                // Si no se encontró con patrones, buscar cualquier directorio que contenga
                // "forge"
                String found = allDirs.stream()
                        .filter(name -> name.toLowerCase().contains("forge") && name.contains(minecraftVersion))
                        .findFirst()
                        .orElse(null);

                if (found != null) {
                    logger.debug("Versión Forge encontrada (búsqueda flexible): {}", found);
                    return found;
                }

                return null;
            }
        } catch (IOException e) {
            logger.error("Error al buscar versión Forge", e);
            return null;
        }
    }

    /**
     * Busca la versión de Fabric instalada para una versión de Minecraft.
     * Busca directorios con el formato: 1.20.1-fabric-0.16.14
     */
    private String findFabricVersion(Path minecraftDir, String minecraftVersion) {
        try {
            Path versionsDir = minecraftDir.resolve("versions");
            if (!Files.exists(versionsDir)) {
                return null;
            }

            try (var stream = Files.list(versionsDir)) {
                List<String> allDirs = stream
                        .filter(Files::isDirectory)
                        .map(path -> path.getFileName().toString())
                        .collect(java.util.stream.Collectors.toList());

                // Buscar formato estándar: <mcversion>-fabric-<loaderversion>
                String found = allDirs.stream()
                        .filter(name -> name.startsWith(minecraftVersion + "-fabric-"))
                        .findFirst()
                        .orElse(null);

                if (found != null) {
                    logger.debug("Versión Fabric encontrada: {}", found);
                    return found;
                }

                // Búsqueda flexible: cualquier directorio con "fabric" y la versión MC
                found = allDirs.stream()
                        .filter(name -> name.toLowerCase().contains("fabric") && name.contains(minecraftVersion))
                        .findFirst()
                        .orElse(null);

                if (found != null) {
                    logger.debug("Versión Fabric encontrada (búsqueda flexible): {}", found);
                    return found;
                }

                return null;
            }
        } catch (IOException e) {
            logger.error("Error al buscar versión Fabric", e);
            return null;
        }
    }

    /**
     * Detecta si la instancia es Forge basándose en el versionId o mainClass
     */
    private boolean isForgeInstance(String versionId, JsonObject versionData) {
        // Detectar por versionId (contiene "-forge-")
        if (versionId != null && versionId.contains("-forge-")) {
            return true;
        }

        // Detectar por mainClass (contiene "bootstraplauncher")
        if (versionData != null && versionData.has("mainClass")) {
            String mainClass = versionData.get("mainClass").getAsString();
            if (mainClass != null && mainClass.contains("bootstraplauncher")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Agrega los flags JVM requeridos para Forge al inicio de la lista de
     * argumentos.
     * Estos flags son necesarios para que Forge pueda acceder a módulos internos de
     * java.base
     * y evitar InaccessibleObjectException.
     */
    private void addForgeRequiredJvmArgs(List<String> command) {
        // Flags críticos para Forge - deben estar al inicio
        // Estos permiten acceso a módulos internos de java.base
        List<String> forgeFlags = new ArrayList<>();
        forgeFlags.add("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED");
        forgeFlags.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
        forgeFlags.add("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED");
        forgeFlags.add("--add-opens=java.base/java.nio=ALL-UNNAMED");
        forgeFlags.add("--add-opens=java.base/java.util=ALL-UNNAMED");
        forgeFlags.add("--add-opens=java.base/java.util.jar=ALL-UNNAMED");
        forgeFlags.add("--add-opens=java.base/java.util.zip=ALL-UNNAMED");
        forgeFlags.add("--add-opens=java.base/sun.nio.ch=ALL-UNNAMED");
        forgeFlags.add("--add-opens=java.base/java.io=ALL-UNNAMED");

        // Insertar al inicio (después de Java path y memoria)
        // Buscar el índice después de -Xmx y -Xms
        int insertIndex = 3; // Después de: java, -Xmx, -Xms
        for (String flag : forgeFlags) {
            // Evitar duplicados
            if (!command.contains(flag)) {
                command.add(insertIndex, flag);
                insertIndex++;
                logger.debug("Agregado flag Forge: {}", flag);
            }
        }
    }

    /**
     * Filtra y elimina SOLO los argumentos problemáticos del sistema de módulos de
     * Java.
     * PRESERVA --add-opens, --add-exports, -p (module path) y --add-modules que son
     * necesarios para Forge.
     * 
     * IMPORTANTE: Para Forge, necesitamos preservar -p (module path) y
     * --add-modules ALL-MODULE-PATH
     * porque Mixin requiere que los módulos de ASM estén disponibles en el module
     * path.
     * 
     * Argumentos a PRESERVAR (necesarios para Forge):
     * - --add-opens de java.base (necesarios para Forge)
     * - --add-exports de java.base (necesarios para Forge)
     * - -p / --module-path (necesario para que Mixin encuentre módulos de ASM)
     * - --add-modules ALL-MODULE-PATH (necesario para cargar módulos desde module
     * path)
     * 
     * Argumentos a eliminar:
     * - --module (módulo específico problemático, solo si causa conflictos)
     */
    private List<String> filterModuleArguments(List<String> command, JsonObject versionData) {
        List<String> filtered = new ArrayList<>();
        boolean skipNext = false;
        boolean skipClasspathValue = false;

        // Primero verificar si hay -p (module path) en el comando
        // boolean hasModulePath = command.stream().anyMatch(arg -> arg.equals("-p") ||
        // arg.equals("--module-path"));

        for (int i = 0; i < command.size(); i++) {
            String arg = command.get(i);

            // Si el argumento anterior era -cp y lo eliminamos, saltar este también (es el
            // valor del classpath)
            if (skipClasspathValue) {
                logger.debug("Eliminado valor de classpath: {} (porque hay -p)", arg);
                skipClasspathValue = false;
                continue;
            }

            // Si hay -p (module path), eliminar -cp (classpath) porque son mutuamente
            // excluyentes
            // Forge moderno usa -p, no -cp
            /*
             * COMENTADO: Forge 1.20+ NECESITA tanto el classpath como el module path
             * El Main Class (BootstrapLauncher) está en el classpath, mientras que las
             * librerías de MC están en el module path
             * if (hasModulePath && (arg.equals("-cp") || arg.equals("-classpath"))) {
             * logger.
             * debug("Eliminado -cp (classpath) porque hay -p (module path) - son mutuamente excluyentes"
             * );
             * skipClasspathValue = true; // El siguiente argumento es el valor del
             * classpath, también eliminarlo
             * continue;
             * }
             */

            // Si el argumento anterior era un flag de módulo que debemos preservar, incluir
            // este también
            if (skipNext) {
                skipNext = false;
                // Preservar el valor de -p, --module-path o --add-modules
                // IMPORTANTE: Normalizar rutas del module path para Windows (convertir / a \)
                if (arg.contains("/") || arg.contains("\\")) {
                    // Es una ruta del module path - normalizar separadores
                    String normalizedPath = normalizeModulePath(arg);
                    logger.debug("Preservado y normalizado valor de módulo: {} -> {}", arg, normalizedPath);
                    filtered.add(normalizedPath);
                } else {
                    logger.debug("Preservado valor de módulo: {}", arg);
                    filtered.add(arg);
                }
                continue;
            }

            // PRESERVAR -p (module path) y --module-path - NECESARIOS para Forge/Mixin
            // Mixin necesita que los módulos de ASM estén en el module path
            if (arg.equals("--module-path") || arg.equals("-p")) {
                logger.debug("Preservado flag de module path (necesario para Forge/Mixin): {}", arg);
                filtered.add(arg);
                skipNext = true; // El siguiente argumento es el valor del module path
                continue;
            }

            // PRESERVAR --add-modules ALL-MODULE-PATH - NECESARIO para Forge/Mixin
            // Permite que Java cargue módulos desde el module path
            if (arg.equals("--add-modules")) {
                logger.debug("Preservado flag --add-modules (necesario para Forge/Mixin): {}", arg);
                filtered.add(arg);
                skipNext = true; // El siguiente argumento es el valor (probablemente ALL-MODULE-PATH)
                continue;
            }

            // PRESERVAR ALL-MODULE-PATH - NECESARIO para Forge/Mixin
            if (arg.equals("ALL-MODULE-PATH")) {
                logger.debug("Preservado ALL-MODULE-PATH (necesario para Forge/Mixin): {}", arg);
                filtered.add(arg);
                continue;
            }

            // Eliminar solo --module si causa problemas específicos (raro en Forge moderno)
            if (arg.equals("--module")) {
                logger.debug("Eliminado flag --module (puede causar conflictos): {}", arg);
                skipNext = true; // El siguiente argumento probablemente es el valor
                continue;
            }

            // PRESERVAR --add-opens y --add-exports (necesarios para Forge)
            if (arg.startsWith("--add-opens") || arg.startsWith("--add-exports")) {
                // Verificar si es de java.base (necesario para Forge)
                if (arg.contains("java.base/")) {
                    // PRESERVAR - estos son necesarios para Forge
                    logger.debug("Preservado --add-opens/--add-exports de java.base: {}", arg);
                    filtered.add(arg);
                    continue;
                }

                // PRESERVAR también los de org.objectweb.asm - NECESARIOS para Mixin
                if (arg.contains("org.objectweb.asm")) {
                    logger.debug("Preservado --add-opens/--add-exports de org.objectweb.asm (necesario para Mixin): {}",
                            arg);
                    filtered.add(arg);
                    continue;
                }

                // Si contiene ALL-UNNAMED pero es de java.base, preservarlo
                if (arg.contains("ALL-UNNAMED") && arg.contains("java.base")) {
                    logger.debug("Preservado --add-opens/--add-exports de java.base con ALL-UNNAMED: {}", arg);
                    filtered.add(arg);
                    continue;
                }

                // Para otros casos, preservar por defecto (pueden ser necesarios)
                logger.debug("Preservado --add-opens/--add-exports: {}", arg);
                filtered.add(arg);
                continue;
            }

            // Mantener el argumento
            filtered.add(arg);
        }

        // Agregar --add-opens explícitos para módulos de ASM si no están presentes
        // Esto es necesario para que Mixin pueda acceder a los módulos de ASM
        // IMPORTANTE: Deben ir ANTES del mainClass (como argumentos JVM, no game args)
        boolean hasAsmOpens = filtered.stream()
                .anyMatch(arg -> arg.contains("org.objectweb.asm") && arg.startsWith("--add-opens"));

        if (!hasAsmOpens) {
            // Encontrar el índice del mainClass para insertar antes de él
            int mainClassIdx = -1;
            for (int i = 0; i < filtered.size(); i++) {
                String arg = filtered.get(i);
                // mainClass es un nombre de clase con puntos, sin guiones ni = ni /
                if (arg.contains(".") && !arg.startsWith("-") && !arg.startsWith("${") &&
                        !arg.contains("=") && !arg.contains("/") && !arg.contains("\\") &&
                        !arg.contains(";") && !arg.contains(":")) {
                    mainClassIdx = i;
                    break;
                }
            }

            List<String> asmOpens = List.of(
                    "--add-opens=org.objectweb.asm/org.objectweb.asm=ALL-UNNAMED",
                    "--add-opens=org.objectweb.asm/org.objectweb.asm.tree=ALL-UNNAMED",
                    "--add-opens=org.objectweb.asm/org.objectweb.asm.util=ALL-UNNAMED",
                    "--add-opens=org.objectweb.asm/org.objectweb.asm.tree.analysis=ALL-UNNAMED",
                    "--add-opens=org.objectweb.asm/org.objectweb.asm.commons=ALL-UNNAMED");

            if (mainClassIdx > 0) {
                // Insertar antes del mainClass
                filtered.addAll(mainClassIdx, asmOpens);
            } else {
                // Fallback: agregar al final (mejor que no agregarlos)
                filtered.addAll(asmOpens);
            }
            logger.info("Agregados --add-opens explícitos para módulos de ASM (necesarios para Mixin)");
        }

        // Nota: ensureAsmLibrariesInModulePath se llama desde launchInstance con
        // minecraftDir

        if (filtered.size() != command.size()) {
            logger.info("Filtrados {} argumentos problemáticos de módulos (de {} a {} argumentos)",
                    command.size() - filtered.size(), command.size(), filtered.size());
        }

        return filtered;
    }

    /**
     * Normaliza las rutas del module path para usar el separador correcto del
     * sistema operativo.
     * Convierte barras `/` a `\` en Windows y maneja rutas separadas por `;` o `:`.
     */
    private String normalizeModulePath(String modulePath) {
        boolean isWindows = PlatformUtil.getOS() == PlatformUtil.OS.WINDOWS || java.io.File.separatorChar == '\\';

        if (isWindows) {
            // En Windows, convertir todas las barras `/` a `\`
            String normalized = modulePath.replace('/', '\\');
            return normalized;
        } else {
            return modulePath;
        }
    }

    /**
     * Asegura que todas las librerías ASM y Nashorn necesarias estén en el module
     * path.
     * Si faltan, las agrega automáticamente.
     */

    private void ensureForgeModulesInModulePath(List<String> command, JsonObject versionData, Path minecraftDir) {
        // Encontrar el índice del module path (-p o --module-path)
        int modulePathIndex = -1;
        String modulePathValue = null;
        String separator = PlatformUtil.getOS() == PlatformUtil.OS.WINDOWS ? ";" : ":";

        for (int i = 0; i < command.size(); i++) {
            String arg = command.get(i);
            if (arg.equals("-p") || arg.equals("--module-path")) {
                if (i + 1 < command.size()) {
                    modulePathIndex = i + 1;
                    modulePathValue = command.get(i + 1);
                    break;
                }
            }
        }

        if (modulePathIndex == -1 || modulePathValue == null) {
            logger.debug("No se encontró module path en el comando");
            return;
        }

        // Analizar qué artefactos ASM ya están presentes para evitar duplicados de
        // versión (Ej: Si ya está asm-9.8.jar, NO agregar asm-9.2.jar)
        // La clave es extraer el artifactId Maven desde la RUTA completa del JAR,
        // no desde el nombre del archivo (que incluye la versión y dificulta el
        // parsing).
        Set<String> presentAsmArtifacts = new HashSet<>();
        String[] existingParts = modulePathValue.split(Pattern.quote(separator));
        for (String part : existingParts) {
            if (part.isEmpty())
                continue;
            String artifactId = extractAsmArtifactId(part);
            if (artifactId != null) {
                presentAsmArtifacts.add(artifactId);
            }
        }

        logger.debug("Artefactos ASM ya presentes en el module path: {}", presentAsmArtifacts);

        // Obtener directorio de librerías compartidas y de instancia
        Path sharedLibrariesDir = PlatformUtil.getSharedLibrariesDirectory();
        Path instanceLibrariesDir = minecraftDir.resolve("libraries");
        List<String> missingLibs = new ArrayList<>();

        // Buscar todas las librerías ASM en ambos directorios
        List<Path> searchDirs = new ArrayList<>();
        searchDirs.add(sharedLibrariesDir);
        if (Files.exists(instanceLibrariesDir)) {
            searchDirs.add(instanceLibrariesDir);
        }

        // 1. Buscar librerías ASM (SOLO si no existe ya una versión de ese artefacto)
        for (Path baseDir : searchDirs) {
            try {
                Path asmBaseDir = baseDir.resolve("org").resolve("ow2").resolve("asm");
                if (Files.exists(asmBaseDir)) {
                    // Buscar recursivamente todas las librerías ASM
                    try (java.util.stream.Stream<Path> asmFiles = Files.walk(asmBaseDir)
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".jar"))) {

                        for (Path libPath : asmFiles.toList()) {
                            String artifactId = extractAsmArtifactId(libPath.toString());

                            // Si ya existe ese artefacto en el module path, saltarlo
                            if (artifactId != null && presentAsmArtifacts.contains(artifactId)) {
                                logger.debug("Saltando librería ASM '{}' porque el artefacto '{}' ya está presente.",
                                        libPath.getFileName(), artifactId);
                                continue;
                            }

                            // Marcar como presente para no agregar otra versión después
                            if (artifactId != null) {
                                presentAsmArtifacts.add(artifactId);
                            }

                            addIfMissing(libPath, modulePathValue, missingLibs, separator);
                        }
                    }
                }
            } catch (IOException e) {
                logger.warn("Error al buscar librerías ASM en directorio: {}", baseDir, e);
            }
        }

        // 2. Buscar librerías Nashorn (nashorn-core) - NO agregar al module path.
        // Nashorn en el module path causa FindException porque intenta resolver
        // sus dependencias (org.objectweb.asm) como módulos nombrados, lo cual
        // falla cuando asm ya está registrado desde una ruta diferente.
        // Nashorn ya está incluido en el classpath (-cp) por buildClasspath(),
        // donde el BootstrapLauncher de Forge lo carga correctamente.
        // Por tanto, simplemente NO lo agregamos al module path.

        // Si faltan librerías, agregarlas al module path
        if (!missingLibs.isEmpty()) { // Simplified condition as nashorn check is now implicit in addIfMissing
            String updatedModulePath = modulePathValue;
            if (!updatedModulePath.isEmpty() && !updatedModulePath.endsWith(separator)) {
                updatedModulePath += separator;
            }
            updatedModulePath += String.join(separator, missingLibs);

            // Normalizar todo el path
            updatedModulePath = normalizeModulePath(updatedModulePath);

            // Actualizar el valor del module path
            command.set(modulePathIndex, updatedModulePath);
            logger.info("Agregadas {} librerías faltantes (ASM/Nashorn) al module path", missingLibs.size());
            logger.debug("Module path actualizado y normalizado: {}", updatedModulePath);
        } else {
            // Aún así, normalizar el path existente por seguridad
            String normalized = normalizeModulePath(modulePathValue);
            if (!normalized.equals(modulePathValue)) {
                command.set(modulePathIndex, normalized);
                logger.debug("Module path normalizado (separadores)");
            }
            logger.debug("Todas las librerías necesarias ya están en el module path");
        }
    }

    /**
     * Extrae el artifactId Maven de un artefacto ASM a partir de su ruta completa.
     * La ruta Maven tiene el formato:
     * .../org/ow2/asm/{artifactId}/{version}/{artifactId}-{version}.jar
     * Usar la ruta en lugar del nombre del archivo evita el problema de parsear
     * "asm-util-9.8.jar" → el segmento de directorio padre de la versión es el
     * artifactId.
     *
     * @param jarPath ruta completa al JAR (puede usar / o \)
     * @return el artifactId (ej: "asm", "asm-util", "asm-tree") o null si no es ASM
     */
    private String extractAsmArtifactId(String jarPath) {
        // Normalizar a forward slashes para el análisis
        String normalized = jarPath.replace('\\', '/');
        // Buscar el segmento /org/ow2/asm/ en la ruta
        int asmIdx = normalized.indexOf("/org/ow2/asm/");
        if (asmIdx == -1)
            return null;
        // Después de "/org/ow2/asm/" viene el artifactId como directorio
        String afterAsm = normalized.substring(asmIdx + "/org/ow2/asm/".length());
        int slashIdx = afterAsm.indexOf('/');
        if (slashIdx == -1)
            return null;
        return afterAsm.substring(0, slashIdx); // ej: "asm-util", "asm", "asm-tree"
    }

    private void addIfMissing(Path libPath, String currentModulePath, List<String> missingLibs, String separator) {
        String libPathStr = libPath.toString();
        // Normalizar también el path actual para la comparación
        if (PlatformUtil.getOS() == PlatformUtil.OS.WINDOWS) {
            libPathStr = libPathStr.replace('/', '\\');
        }

        // Verificar si ya está en missingLibs (para no repetirlo nosotros mismos)
        if (missingLibs.contains(libPathStr)) {
            return;
        }

        // Verificar si ya está en el module path
        boolean alreadyInPath = false;
        String[] modulePathParts = currentModulePath.split(Pattern.quote(separator)); // Use Pattern.quote for split
        for (String part : modulePathParts) {
            String normalizedPart = part;
            if (PlatformUtil.getOS() == PlatformUtil.OS.WINDOWS) {
                normalizedPart = normalizedPart.replace('/', '\\');
            }
            // Comparación robusta: path completo o nombre de archivo
            if (normalizedPart.equalsIgnoreCase(libPathStr) ||
                    normalizedPart.endsWith(libPath.getFileName().toString())) {
                alreadyInPath = true;
                break;
            }
        }

        if (!alreadyInPath) {
            missingLibs.add(libPathStr);
            logger.debug("Librería faltante agregada al module path: {}", libPathStr);
        }
    }

    /**
     * Filtra argumentos que activan el modo demo
     */
    private List<String> filterDemoArguments(List<String> command) {
        List<String> filtered = new ArrayList<>();
        for (String arg : command) {
            // Eliminar argumentos que activan modo demo
            if (arg.equals("--demo") || arg.equals("--demo-mode") || arg.contains("demo=true")) {
                logger.info("Eliminado argumento que activa modo demo: {}", arg);
                continue;
            }
            filtered.add(arg);
        }
        return filtered;
    }

    /**
     * Determina el tipo de usuario según la versión de Minecraft
     * Para versiones modernas (1.7.2+), usar "mojang" ayuda a evitar modo demo
     */
    private String determineUserType(String version) {
        try {
            // Extraer número de versión mayor (ej: "1.20.1" -> 1.20)
            String[] parts = version.split("\\.");
            if (parts.length >= 2) {
                int major = Integer.parseInt(parts[0]);
                int minor = Integer.parseInt(parts[1]);

                // Versiones 1.7.2+ usan "mojang", anteriores usan "legacy"
                if (major > 1 || (major == 1 && minor >= 7)) {
                    return "mojang";
                }
            }
        } catch (Exception e) {
            logger.debug("Error al determinar tipo de usuario, usando mojang por defecto", e);
        }
        // Por defecto usar "mojang" para versiones modernas
        return "mojang";
    }

    /**
     * Genera un token de acceso offline más realista para evitar modo demo
     * El token debe ser un string largo que parezca un token real de autenticación
     * Los tokens reales de Minecraft suelen tener 300+ caracteres
     */
    private String generateOfflineAccessToken(String playerName, String playerUuid) {
        try {
            // Generar un token basado en el nombre y UUID del jugador
            String seed = playerName + playerUuid + "offline_token" + System.currentTimeMillis();
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            md.update(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] digest = md.digest();

            // Convertir a hexadecimal
            StringBuilder token = new StringBuilder();
            for (byte b : digest) {
                token.append(String.format("%02x", b));
            }

            // Repetir y mezclar para hacer el token más largo (tokens reales son ~300+
            // caracteres)
            // Usar múltiples iteraciones para generar un token de ~320 caracteres
            String baseToken = token.toString();
            for (int i = 0; i < 4; i++) {
                // Rotar y mezclar el token base
                String rotated = baseToken.substring(i) + baseToken.substring(0, i);
                token.append(rotated);
            }

            // Asegurar que el token tenga al menos 300 caracteres
            String finalToken = token.toString();
            if (finalToken.length() < 300) {
                // Repetir hasta alcanzar 300 caracteres
                while (finalToken.length() < 300) {
                    finalToken += baseToken;
                }
            }

            // Limitar a ~320 caracteres (tamaño típico de tokens reales)
            return finalToken.substring(0, Math.min(320, finalToken.length()));
        } catch (java.security.NoSuchAlgorithmException e) {
            logger.warn("Error al generar token de acceso, usando token por defecto", e);
            // Fallback: token basado en hash simple pero largo
            long hash1 = (playerName + playerUuid).hashCode() & 0xFFFFFFFFL;
            long hash2 = System.currentTimeMillis() & 0xFFFFFFFFL;
            String fallback = String.format("%032x%032x%032x%032x%032x", hash1, hash2, hash1 ^ hash2, hash1 << 1,
                    hash2 << 1);
            return fallback.substring(0, Math.min(320, fallback.length()));
        }
    }

    /**
     * Genera un UUID offline basado en el nombre del jugador
     * Este método replica el algoritmo de Minecraft para generar UUIDs offline
     * y evita que el juego se lance en modo demo
     */
    private String generateOfflineUUID(String playerName) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            md.update(("OfflinePlayer:" + playerName).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] digest = md.digest();

            // Convertir a UUID (formato estándar de Minecraft para offline)
            digest[6] &= 0x0f; // Limpiar bits de versión
            digest[6] |= 0x30; // Establecer versión 3
            digest[8] &= 0x3f; // Limpiar bits de variante
            digest[8] |= 0x80; // Establecer variante RFC 4122

            StringBuilder uuid = new StringBuilder();
            uuid.append(String.format("%02x%02x%02x%02x", digest[0], digest[1], digest[2], digest[3]));
            uuid.append("-");
            uuid.append(String.format("%02x%02x", digest[4], digest[5]));
            uuid.append("-");
            uuid.append(String.format("%02x%02x", digest[6], digest[7]));
            uuid.append("-");
            uuid.append(String.format("%02x%02x", digest[8], digest[9]));
            uuid.append("-");
            uuid.append(String.format("%02x%02x%02x%02x%02x%02x", digest[10], digest[11], digest[12], digest[13],
                    digest[14], digest[15]));

            return uuid.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            logger.warn("Error al generar UUID offline, usando UUID por defecto", e);
            // Fallback: UUID basado en hash simple
            return String.format("%08x-0000-0000-0000-%012x",
                    playerName.hashCode() & 0xFFFFFFFFL,
                    Math.abs(playerName.hashCode()) & 0xFFFFFFFFFFFFL);
        }
    }
}