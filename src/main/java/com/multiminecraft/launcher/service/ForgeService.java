package com.multiminecraft.launcher.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.multiminecraft.launcher.util.FileUtil;
import com.multiminecraft.launcher.util.PlatformUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Servicio para instalar Forge
 */
public class ForgeService {
    
    private static final Logger logger = LoggerFactory.getLogger(ForgeService.class);
    private static final String FORGE_VERSION_API = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    private static final String FORGE_MAVEN_BASE = "https://maven.minecraftforge.net/net/minecraftforge/forge/";
    
    private final DownloadService downloadService;
    private final ConfigService configService;
    private final MojangService mojangService;
    
    public ForgeService() {
        this.downloadService = new DownloadService();
        this.configService = ConfigService.getInstance();
        this.mojangService = new MojangService();
    }
    
    /**
     * Instala Forge para una versión específica de Minecraft
     */
    public void installForge(String minecraftVersion, Path minecraftDir, Consumer<String> statusCallback) throws Exception {
        logger.info("Instalando Forge para Minecraft {}...", minecraftVersion);
        
        if (statusCallback != null) statusCallback.accept("Obteniendo versiones de Forge disponibles...");
        
        // Obtener la versión de Forge recomendada para esta versión de Minecraft
        String forgeVersion = getRecommendedForgeVersion(minecraftVersion);
        if (forgeVersion == null) {
            throw new Exception("No se encontró una versión de Forge compatible para Minecraft " + minecraftVersion);
        }
        
        logger.info("Versión de Forge seleccionada: {}", forgeVersion);
        
        // IMPORTANTE: Limpiar archivos temporales bloqueados ANTES de descargar el instalador (como en Python)
        // Esto evita errores de archivos bloqueados que pueden causar fallos en la instalación
        if (statusCallback != null) statusCallback.accept("Limpiando archivos temporales bloqueados...");
        cleanBlockedTemporaryFiles(minecraftDir);
        
        // Esperar 1 segundo después de limpiar para liberar archivos (como en Python)
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Construir URL del instalador
        String installerUrl = FORGE_MAVEN_BASE + forgeVersion + "/forge-" + forgeVersion + "-installer.jar";
        
        // Crear directorio temporal
        Path tempDir = minecraftDir.resolve("temp");
        FileUtil.createDirectory(tempDir);
        
        // Descargar instalador
        if (statusCallback != null) statusCallback.accept("Descargando instalador de Forge...");
        Path installerPath = tempDir.resolve("forge-installer.jar");
        
        try {
            downloadService.downloadFile(installerUrl, installerPath, progress -> {
                if (statusCallback != null && progress != null) {
                    statusCallback.accept(String.format("Descargando instalador de Forge... %.0f%%", progress * 100));
                }
            });
        } catch (Exception e) {
            logger.warn("Error al descargar desde URL principal, intentando URL alternativa: {}", e.getMessage());
            // Intentar con URL alternativa (formato legacy)
            installerUrl = "https://files.minecraftforge.net/maven/net/minecraftforge/forge/" + forgeVersion + "/forge-" + forgeVersion + "-installer.jar";
            try {
                downloadService.downloadFile(installerUrl, installerPath, progress -> {
                    if (statusCallback != null && progress != null) {
                        statusCallback.accept(String.format("Descargando instalador de Forge (URL alternativa)... %.0f%%", progress * 100));
                    }
                });
            } catch (Exception e2) {
                logger.error("Error al descargar desde URL alternativa: {}", e2.getMessage());
                throw new Exception("No se pudo descargar el instalador de Forge. Verifique su conexión a internet y que la versión de Forge sea válida.", e2);
            }
        }
        
        // Obtener ruta de Java (usar la versión correcta según la versión de MC)
        String javaPath = configService.getJavaPathForVersion(minecraftVersion);
        if (javaPath == null || javaPath.isEmpty()) {
            // Fallback: intentar auto-detectar según la versión de MC
            javaPath = PlatformUtil.getJavaPathForMinecraft(minecraftVersion);
        }
        
        // Verificar que Java existe
        File javaFile = new File(javaPath);
        if (!javaFile.exists()) {
            throw new Exception("No se encontró Java en: " + javaPath + ". Por favor, configure la ruta de Java en la configuración.");
        }
        
        // Ejecutar instalador
        if (statusCallback != null) statusCallback.accept("Instalando Forge...");
        logger.info("Ejecutando instalador de Forge: {} --installClient --target {}", installerPath, minecraftDir);
        
        // El instalador de Forge necesita que el directorio .minecraft ya exista con cierta estructura
        // Asegurarse de que el directorio versions existe
        Path versionsDir = minecraftDir.resolve("versions");
        FileUtil.createDirectory(versionsDir);
        
        // El instalador de Forge requiere un archivo launcher_profiles.json
        // Crear un perfil básico del launcher para que el instalador funcione
        Path launcherProfilesPath = minecraftDir.resolve("launcher_profiles.json");
        if (!Files.exists(launcherProfilesPath)) {
            String launcherProfilesJson = "{\n" +
                "  \"profiles\": {},\n" +
                "  \"settings\": {\n" +
                "    \"crashAssistance\": true,\n" +
                "    \"enableAdvanced\": false,\n" +
                "    \"enableAnalytics\": false,\n" +
                "    \"enableHistorical\": false,\n" +
                "    \"enableReleases\": true,\n" +
                "    \"enableSnapshots\": false,\n" +
                "    \"keepLauncherOpen\": false,\n" +
                "    \"profileSorting\": \"ByLastPlayed\",\n" +
                "    \"showGameLog\": false,\n" +
                "    \"showMenu\": false,\n" +
                "    \"soundOn\": false\n" +
                "  },\n" +
                "  \"version\": 2\n" +
                "}";
            FileUtil.writeStringToFile(launcherProfilesPath, launcherProfilesJson);
            logger.debug("Archivo launcher_profiles.json creado");
        }
        
        // Pre-descargar librerías críticas de Forge antes de ejecutar el instalador
        if (statusCallback != null) statusCallback.accept("Pre-descargando librerías críticas de Forge...");
        preDownloadCriticalForgeLibraries(forgeVersion, minecraftDir, statusCallback);
        
        // El instalador de Forge necesita que el directorio de trabajo sea el directorio .minecraft
        // y que el directorio ya tenga la estructura básica (versions, etc.)
        // NOTA: El instalador de Forge moderno NO acepta --target, usa el directorio de trabajo actual
        // Agregar argumentos JVM para aumentar timeouts de red
        List<String> installerCommand = new ArrayList<>();
        installerCommand.add(javaPath);
        installerCommand.add("-Djava.net.useSystemProxies=true");
        installerCommand.add("-Dsun.net.client.defaultConnectTimeout=300000"); // 5 minutos
        installerCommand.add("-Dsun.net.client.defaultReadTimeout=300000"); // 5 minutos
        installerCommand.add("-Dhttp.keepAlive=true");
        installerCommand.add("-jar");
        installerCommand.add(installerPath.toString());
        installerCommand.add("--installClient");
        
        ProcessBuilder processBuilder = new ProcessBuilder(installerCommand);
        
        // El directorio de trabajo debe ser el directorio .minecraft
        processBuilder.directory(minecraftDir.toFile());
        processBuilder.redirectErrorStream(true);
        
        // IMPORTANTE: Establecer variables de entorno para forzar al instalador a usar el directorio correcto
        // El instalador de Forge puede usar APPDATA o detectar el .minecraft desde variables de entorno
        // Por lo tanto, establecemos estas variables para que use nuestro directorio de instancia
        PlatformUtil.OS os = PlatformUtil.getOS();
        java.util.Map<String, String> env = processBuilder.environment();
        
        if (os == PlatformUtil.OS.WINDOWS) {
            // Establecer APPDATA para que apunte al directorio padre de .minecraft
            // Si minecraftDir es: C:\Users\...\.MultiMinecraft\Instancias\prueba4\.minecraft
            // Entonces APPDATA debería ser: C:\Users\...\.MultiMinecraft\Instancias\prueba4
            Path appDataPath = minecraftDir.getParent();
            if (appDataPath != null) {
                env.put("APPDATA", appDataPath.toString());
                logger.debug("APPDATA establecido temporalmente a: {}", appDataPath);
            }
            
            // También establecer MINECRAFT_HOME (algunos instaladores lo usan)
            env.put("MINECRAFT_HOME", minecraftDir.toString());
            logger.debug("MINECRAFT_HOME establecido a: {}", minecraftDir);
        } else if (os == PlatformUtil.OS.MACOS) {
            // En macOS, establecer HOME temporalmente
            Path homePath = minecraftDir.getParent();
            if (homePath != null) {
                env.put("HOME", homePath.toString());
                logger.debug("HOME establecido temporalmente a: {}", homePath);
            }
            env.put("MINECRAFT_HOME", minecraftDir.toString());
        } else if (os == PlatformUtil.OS.LINUX) {
            // En Linux, establecer HOME temporalmente
            Path homePath = minecraftDir.getParent();
            if (homePath != null) {
                env.put("HOME", homePath.toString());
                logger.debug("HOME establecido temporalmente a: {}", homePath);
            }
            env.put("MINECRAFT_HOME", minecraftDir.toString());
        }
        
        // Ejecutar instalador con reintentos automáticos
        int maxRetries = 3;
        int attempt = 0;
        int exitCode = -1;
        String fullOutput = "";
        String fullError = "";
        boolean success = false;
        
        while (attempt < maxRetries && !success) {
            attempt++;
            if (attempt > 1) {
                logger.info("Reintentando instalación de Forge (intento {}/{})...", attempt, maxRetries);
                if (statusCallback != null) {
                    statusCallback.accept(String.format("Reintentando instalación de Forge (intento %d/%d)...", attempt, maxRetries));
                }
                // Esperar 5 segundos entre reintentos
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            Process process = processBuilder.start();
            
            // Capturar salida y errores
            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();
            
            // Leer salida estándar
            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        logger.debug("Forge installer stdout: {}", line);
                    }
                } catch (Exception e) {
                    logger.error("Error al leer salida del instalador", e);
                }
            });
            
            // Leer salida de error (aunque redirectErrorStream está en true, por si acaso)
            Thread errorThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                        logger.warn("Forge installer stderr: {}", line);
                    }
                } catch (Exception e) {
                    logger.error("Error al leer errores del instalador", e);
                }
            });
            
            outputThread.start();
            errorThread.start();
            
            // Esperar a que el proceso termine (puede tardar varios minutos)
            // Usar waitFor con timeout para evitar bloqueos indefinidos
            boolean finished = false;
            long startTime = System.currentTimeMillis();
            long maxWaitTime = 600000; // 10 minutos máximo
            
            while (!finished && (System.currentTimeMillis() - startTime) < maxWaitTime) {
                try {
                    // Esperar con timeout de 30 segundos
                    finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
                    if (!finished) {
                        logger.debug("Instalador aún ejecutándose, esperando más...");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Espera del instalador interrumpida");
                    break;
                }
            }
            
            if (!finished) {
                logger.error("El instalador de Forge excedió el tiempo máximo de espera (10 minutos)");
                process.destroyForcibly();
                exitCode = -1;
            } else {
                exitCode = process.exitValue();
            }
            
            // Esperar a que los threads terminen de leer (dar más tiempo - aumentado a 30 segundos)
            outputThread.join(30000); // Esperar hasta 30 segundos
            errorThread.join(30000);
            
            fullOutput = output.toString();
            fullError = errorOutput.toString();
            
            // Loggear la salida completa para debugging
            logger.info("Instalador de Forge terminado con código: {} (intento {}/{})", exitCode, attempt, maxRetries);
            if (!fullOutput.isEmpty()) {
                logger.debug("Salida del instalador (intento {}):\n{}", attempt, fullOutput);
            }
            if (!fullError.isEmpty()) {
                logger.debug("Errores del instalador (intento {}):\n{}", attempt, fullError);
            }
            
            // Verificar si fue exitoso
            if (exitCode == 0) {
                success = true;
                logger.info("Instalador de Forge completado exitosamente en intento {}", attempt);
            } else if (fullOutput.contains("already installed") || fullOutput.contains("ya está instalado")) {
                success = true;
                logger.info("Forge ya está instalado, continuando...");
            } else {
                // Detectar si es un error de timeout
                boolean isTimeoutError = detectTimeoutError(fullOutput, fullError);
                
                if (isTimeoutError && attempt < maxRetries) {
                    logger.warn("Error de timeout detectado en intento {}. Intentando recuperar librerías faltantes...", attempt);
                    
                    // Intentar descargar librerías faltantes detectadas
                    List<String> missingLibraries = extractMissingLibraries(fullOutput);
                    if (!missingLibraries.isEmpty()) {
                        logger.info("Intentando descargar {} librerías faltantes antes del siguiente intento...", missingLibraries.size());
                        downloadMissingLibraries(missingLibraries, minecraftDir, statusCallback);
                    }
                }
            }
        }
        
        // Si después de todos los reintentos aún falló
        if (!success) {
            logger.error("El instalador de Forge falló después de {} intentos. Código de salida: {}\nSalida estándar: {}\nSalida de error: {}", 
                maxRetries, exitCode, fullOutput, fullError);
            
            // Intentar proporcionar un mensaje más útil
            String errorMessage = "El instalador de Forge falló después de " + maxRetries + " intentos. Código de salida: " + exitCode;
            
            // Detectar librerías faltantes y intentar descargarlas
            List<String> missingLibraries = extractMissingLibraries(fullOutput);
            if (!missingLibraries.isEmpty()) {
                logger.info("Intentando descargar {} librerías faltantes como último recurso...", missingLibraries.size());
                try {
                    downloadMissingLibraries(missingLibraries, minecraftDir, statusCallback);
                    logger.info("Librerías faltantes descargadas. Continuando con verificación...");
                    // No lanzar excepción todavía, intentar verificar instalación
                } catch (Exception e) {
                    logger.error("Error al descargar librerías faltantes: {}", e.getMessage());
                    errorMessage += "\nLibrerías que fallaron al descargar: " + String.join(", ", missingLibraries);
                }
            }
            
            if (!fullOutput.isEmpty()) {
                // Buscar mensajes de error específicos
                if (detectTimeoutError(fullOutput, fullError)) {
                    errorMessage += "\nError: Timeout al descargar librerías. Verifique su conexión a internet.";
                } else if (fullOutput.contains("Error") || fullOutput.contains("error") || 
                          fullOutput.contains("Exception") || fullOutput.contains("Failed")) {
                    // Extraer el mensaje de error más relevante
                    String errorDetail = fullOutput;
                    if (fullOutput.length() > 500) {
                        errorDetail = fullOutput.substring(0, 500) + "...";
                    }
                    errorMessage += "\nDetalles: " + errorDetail;
                } else {
                    // Si no hay mensaje de error claro, mostrar las últimas líneas
                    String[] lines = fullOutput.split("\n");
                    int start = Math.max(0, lines.length - 10);
                    String lastLines = String.join("\n", java.util.Arrays.copyOfRange(lines, start, lines.length));
                    errorMessage += "\nÚltimas líneas de salida:\n" + lastLines;
                }
            }
            
            // Solo lanzar excepción si realmente falló y no se pudo recuperar
            throw new Exception(errorMessage);
        }
        
        // Limpiar archivos temporales
        if (statusCallback != null) statusCallback.accept("Limpiando archivos temporales...");
        try {
            Files.deleteIfExists(installerPath);
            // Eliminar directorio temporal recursivamente
            if (Files.exists(tempDir)) {
                try (Stream<Path> paths = Files.walk(tempDir)) {
                    paths.sorted((a, b) -> b.compareTo(a)) // Eliminar archivos antes que directorios
                         .forEach(path -> {
                             try {
                                 Files.delete(path);
                             } catch (IOException e) {
                                 logger.debug("No se pudo eliminar: {}", path, e);
                             }
                         });
                }
            }
        } catch (Exception e) {
            logger.warn("No se pudieron eliminar algunos archivos temporales", e);
        }
        
        // Verificar instalación y descargar librerías faltantes
        if (statusCallback != null) statusCallback.accept("Verificando instalación de Forge...");
        String installedForgeVersion = verifyAndCompleteForgeInstallation(minecraftVersion, minecraftDir, statusCallback);
        
        if (installedForgeVersion == null) {
            throw new Exception("No se pudo verificar la instalación de Forge. La versión Forge no se encontró después de la instalación.");
        }
        
        logger.info("Forge instalado exitosamente para Minecraft {}: {}", minecraftVersion, installedForgeVersion);
    }
    
    /**
     * Obtiene la versión recomendada de Forge para una versión de Minecraft
     */
    private String getRecommendedForgeVersion(String minecraftVersion) throws Exception {
        try {
            // Descargar información de promociones de Forge
            String promotionsJson = downloadService.downloadString(FORGE_VERSION_API);
            JsonObject promotions = JsonParser.parseString(promotionsJson).getAsJsonObject();
            
            // Buscar en promociones
            JsonObject promos = promotions.getAsJsonObject("promos");
            String promoKey = minecraftVersion + "-recommended";
            
            if (promos.has(promoKey)) {
                String forgeVersion = promos.get(promoKey).getAsString();
                logger.debug("Versión recomendada de Forge para {}: {}", minecraftVersion, forgeVersion);
                return minecraftVersion + "-" + forgeVersion;
            }
            
            // Si no hay recomendada, buscar latest
            promoKey = minecraftVersion + "-latest";
            if (promos.has(promoKey)) {
                String forgeVersion = promos.get(promoKey).getAsString();
                logger.debug("Versión latest de Forge para {}: {}", minecraftVersion, forgeVersion);
                return minecraftVersion + "-" + forgeVersion;
            }
            
            // Si no se encuentra en promociones, intentar buscar en la lista de versiones
            return findForgeVersionInList(minecraftVersion, promotions);
            
        } catch (Exception e) {
            logger.error("Error al obtener versión de Forge", e);
            // Intentar formato simple: usar la versión de Minecraft como base
            // Esto es un fallback, puede no funcionar para todas las versiones
            return minecraftVersion + "-47.1.0"; // Versión de ejemplo, debería ser dinámico
        }
    }
    
    /**
     * Busca una versión de Forge en la lista de versiones disponibles
     */
    private String findForgeVersionInList(String minecraftVersion, JsonObject promotions) {
        // Intentar buscar en la estructura de versiones si existe
        if (promotions.has("versionInfo")) {
            JsonObject versionInfo = promotions.getAsJsonObject("versionInfo");
            // Buscar la versión más reciente que coincida con la versión de Minecraft
            for (String key : versionInfo.keySet()) {
                if (key.startsWith(minecraftVersion + "-")) {
                    return key;
                }
            }
        }
        return null;
    }
    
    /**
     * Verifica y completa la instalación de Forge
     * Busca la versión instalada, verifica estructura y descarga librerías faltantes
     * Mejora: Con reintentos para esperar a que el instalador termine de escribir archivos
     */
    private String verifyAndCompleteForgeInstallation(String minecraftVersion, Path minecraftDir, Consumer<String> statusCallback) {
        // Reintentar verificación hasta 5 veces con esperas (el instalador puede tardar en escribir archivos)
        int maxRetries = 5;
        int retryDelay = 2000; // 2 segundos entre reintentos
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // Buscar la versión de Forge instalada
                String forgeVersion = findInstalledForgeVersion(minecraftDir, minecraftVersion);
                if (forgeVersion == null) {
                    if (attempt < maxRetries) {
                        logger.debug("No se encontró versión Forge (intento {}/{}), esperando y reintentando...", attempt, maxRetries);
                        try {
                            Thread.sleep(retryDelay);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        continue;
                    }
                    logger.error("No se encontró la versión de Forge instalada para Minecraft {} después de {} intentos", minecraftVersion, maxRetries);
                    return null;
                }
                
                logger.info("Versión Forge detectada: {}", forgeVersion);
                
                // Verificar estructura de carpetas
                if (!verifyForgeInstallation(forgeVersion, minecraftDir)) {
                    if (attempt < maxRetries) {
                        logger.debug("Instalación de Forge aún no válida (intento {}/{}), esperando y reintentando...", attempt, maxRetries);
                        try {
                            Thread.sleep(retryDelay);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        continue;
                    }
                    logger.error("La instalación de Forge no es válida para la versión: {} después de {} intentos", forgeVersion, maxRetries);
                    return null;
                }
                
                // Cargar JSON de versión Forge
                Path versionJsonPath = minecraftDir.resolve("versions").resolve(forgeVersion)
                        .resolve(forgeVersion + ".json");
                
                if (!Files.exists(versionJsonPath)) {
                    if (attempt < maxRetries) {
                        logger.debug("JSON de versión Forge aún no existe (intento {}/{}), esperando y reintentando...", attempt, maxRetries);
                        try {
                            Thread.sleep(retryDelay);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        continue;
                    }
                    logger.error("No se encontró el JSON de versión Forge: {} después de {} intentos", versionJsonPath, maxRetries);
                    return null;
                }
                
                String versionJson = FileUtil.readFileAsString(versionJsonPath);
                JsonObject versionData = JsonParser.parseString(versionJson).getAsJsonObject();
                
                // Verificar y descargar librerías faltantes
                if (statusCallback != null) {
                    statusCallback.accept("Verificando librerías de Forge...");
                }
                verifyAndDownloadForgeLibraries(versionData, minecraftDir, statusCallback);
                
                // Extraer natives si es necesario
                if (statusCallback != null) {
                    statusCallback.accept("Extrayendo natives...");
                }
                extractNativesIfNeeded(versionData, forgeVersion, minecraftDir);
                
                logger.info("Instalación de Forge verificada exitosamente en intento {}", attempt);
                return forgeVersion;
                
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    logger.warn("Error al verificar instalación de Forge (intento {}/{}): {}. Reintentando...", attempt, maxRetries, e.getMessage());
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }
                logger.error("Error al verificar y completar instalación de Forge después de {} intentos", maxRetries, e);
                return null;
            }
        }
        
        return null;
    }
    
    /**
     * Busca la versión de Forge instalada en el directorio de versiones
     * Mejora: Busca también variaciones del formato (como en Python)
     */
    private String findInstalledForgeVersion(Path minecraftDir, String minecraftVersion) {
        try {
            Path versionsDir = minecraftDir.resolve("versions");
            logger.debug("Buscando versión Forge en: {}", versionsDir);
            
            if (!Files.exists(versionsDir)) {
                logger.warn("El directorio versions no existe: {}", versionsDir);
                return null;
            }
            
            // Patrones de búsqueda (múltiples formatos posibles)
            List<String> searchPatterns = new ArrayList<>();
            searchPatterns.add(minecraftVersion + "-forge-");  // Formato estándar: 1.20.1-forge-47.4.10
            searchPatterns.add(minecraftVersion + "-");        // Formato alternativo: 1.20.1-47.4.10
            
            logger.debug("Buscando directorios con patrones: {}", searchPatterns);
            
            try (Stream<Path> stream = Files.list(versionsDir)) {
                List<String> allDirs = stream
                        .filter(Files::isDirectory)
                        .map(path -> path.getFileName().toString())
                        .collect(java.util.stream.Collectors.toList());
                
                logger.debug("Directorios encontrados en versions: {}", allDirs);
                
                // Buscar con cada patrón
                String found = null;
                for (String pattern : searchPatterns) {
                    found = allDirs.stream()
                            .filter(name -> name.startsWith(pattern))
                            .findFirst()
                            .orElse(null);
                    
                    if (found != null) {
                        logger.info("Versión Forge encontrada con patrón '{}': {}", pattern, found);
                        break;
                    }
                }
                
                // Si no se encontró con patrones, buscar cualquier directorio que contenga "forge"
                if (found == null) {
                    found = allDirs.stream()
                            .filter(name -> name.toLowerCase().contains("forge") && name.contains(minecraftVersion))
                            .findFirst()
                            .orElse(null);
                    
                    if (found != null) {
                        logger.info("Versión Forge encontrada (búsqueda flexible): {}", found);
                    }
                }
                
                if (found == null) {
                    logger.warn("No se encontró ninguna versión Forge para: {}", minecraftVersion);
                }
                
                return found;
            }
        } catch (IOException e) {
            logger.error("Error al buscar versión Forge instalada", e);
            return null;
        }
    }
    
    /**
     * Verifica que la instalación de Forge sea válida
     */
    private boolean verifyForgeInstallation(String forgeVersion, Path minecraftDir) {
        Path versionDir = minecraftDir.resolve("versions").resolve(forgeVersion);
        Path versionJson = versionDir.resolve(forgeVersion + ".json");
        
        // Verificar que el directorio existe
        if (!Files.exists(versionDir)) {
            logger.error("Directorio de versión Forge no existe: {}", versionDir);
            return false;
        }
        
        // Verificar que el JSON existe
        if (!Files.exists(versionJson)) {
            logger.error("JSON de versión Forge no existe: {}", versionJson);
            return false;
        }
        
        // Verificar que el JSON es válido
        try {
            String jsonContent = FileUtil.readFileAsString(versionJson);
            JsonParser.parseString(jsonContent).getAsJsonObject();
            logger.debug("JSON de versión Forge válido: {}", forgeVersion);
        } catch (Exception e) {
            logger.error("JSON de versión Forge inválido: {}", versionJson, e);
            return false;
        }
        
        // Verificar que el JAR existe (si aplica)
        Path versionJar = versionDir.resolve(forgeVersion + ".jar");
        if (!Files.exists(versionJar)) {
            logger.debug("JAR de versión Forge no existe (puede ser normal si usa herencia): {}", versionJar);
            // No es crítico, algunas versiones usan herencia
        }
        
        logger.info("Instalación de Forge verificada correctamente: {}", forgeVersion);
        return true;
    }
    
    /**
     * Verifica y descarga las librerías de Forge faltantes
     */
    private void verifyAndDownloadForgeLibraries(JsonObject versionData, Path minecraftDir, Consumer<String> statusCallback) {
        try {
            // Obtener todas las librerías (incluyendo herencia)
            List<JsonObject> allLibraries = new ArrayList<>();
            collectAllLibraries(versionData, minecraftDir, allLibraries);
            
            Path instanceLibrariesDir = minecraftDir.resolve("libraries");
            Path sharedLibrariesDir = PlatformUtil.getSharedLibrariesDirectory();
            
            List<String> missingLibraries = new ArrayList<>();
            
            // Verificar cada librería
            for (JsonObject library : allLibraries) {
                if (library.has("rules")) {
                    if (!shouldIncludeLibrary(library)) {
                        continue;
                    }
                }
                
                String libraryPath = getLibraryPath(library);
                if (libraryPath == null) {
                    continue;
                }
                
                Path sharedPath = sharedLibrariesDir.resolve(libraryPath);
                Path instancePath = instanceLibrariesDir.resolve(libraryPath);
                
                if (!Files.exists(sharedPath) && !Files.exists(instancePath)) {
                    missingLibraries.add(libraryPath);
                }
            }
            
            // Descargar librerías faltantes
            if (!missingLibraries.isEmpty()) {
                logger.info("Se encontraron {} librerías faltantes de Forge", missingLibraries.size());
                if (statusCallback != null) {
                    statusCallback.accept("Descargando " + missingLibraries.size() + " librerías de Forge...");
                }
                
                // PRIORIDAD: Descargar primero al directorio COMPARTIDO para evitar duplicados
                // Solo usar el directorio de instancia si hay problemas con el compartido
                try {
                    logger.info("Descargando librerías de Forge al directorio compartido: {}", sharedLibrariesDir);
                    mojangService.downloadLibraries(versionData, sharedLibrariesDir);
                    
                    // Si hay herencia, descargar también las librerías del padre al directorio compartido
                    if (versionData.has("inheritsFrom")) {
                        String parentVersion = versionData.get("inheritsFrom").getAsString();
                        Path parentJsonPath = minecraftDir.resolve("versions").resolve(parentVersion)
                                .resolve(parentVersion + ".json");
                        if (Files.exists(parentJsonPath)) {
                            String parentJson = FileUtil.readFileAsString(parentJsonPath);
                            JsonObject parentData = JsonParser.parseString(parentJson).getAsJsonObject();
                            logger.info("Descargando librerías heredadas al directorio compartido");
                            mojangService.downloadLibraries(parentData, sharedLibrariesDir);
                        }
                    }
                    
                    logger.info("Librerías de Forge descargadas al directorio compartido");
                } catch (IOException | InterruptedException | ExecutionException e) {
                    logger.warn("Error al descargar al directorio compartido, intentando directorio de instancia: {}", e.getMessage());
                    // Fallback: intentar descargar al directorio de instancia si falla el compartido
                    try {
                        mojangService.downloadLibraries(versionData, instanceLibrariesDir);
                        if (versionData.has("inheritsFrom")) {
                            String parentVersion = versionData.get("inheritsFrom").getAsString();
                            Path parentJsonPath = minecraftDir.resolve("versions").resolve(parentVersion)
                                    .resolve(parentVersion + ".json");
                            if (Files.exists(parentJsonPath)) {
                                String parentJson = FileUtil.readFileAsString(parentJsonPath);
                                JsonObject parentData = JsonParser.parseString(parentJson).getAsJsonObject();
                                mojangService.downloadLibraries(parentData, instanceLibrariesDir);
                            }
                        }
                        logger.info("Librerías descargadas al directorio de instancia (fallback)");
                    } catch (Exception e2) {
                        logger.error("Error al descargar librerías de Forge: {}", e2.getMessage(), e2);
                        if (statusCallback != null) {
                            statusCallback.accept("Advertencia: Algunas librerías no se descargaron. Se intentará descargarlas al lanzar el juego.");
                        }
                    }
                }
            } else {
                logger.info("Todas las librerías de Forge están presentes");
            }
            
        } catch (Exception e) {
            logger.warn("Error al verificar/descargar librerías de Forge", e);
            // No lanzar excepción, intentar continuar
        }
    }
    
    /**
     * Recolecta todas las librerías incluyendo las de versiones heredadas
     */
    private void collectAllLibraries(JsonObject versionData, Path minecraftDir, List<JsonObject> allLibraries) {
        if (versionData == null) {
            logger.warn("versionData es null en collectAllLibraries");
            return;
        }
        
        // Agregar librerías de la versión actual
        if (versionData.has("libraries")) {
            try {
                JsonArray libraries = versionData.getAsJsonArray("libraries");
                if (libraries != null) {
                    for (JsonElement libElement : libraries) {
                        if (libElement != null && libElement.isJsonObject()) {
                            allLibraries.add(libElement.getAsJsonObject());
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Error al procesar librerías de la versión actual", e);
            }
        }
        
        // Agregar librerías de versión heredada
        if (versionData.has("inheritsFrom")) {
            try {
                String parentVersion = versionData.get("inheritsFrom").getAsString();
                if (parentVersion != null && !parentVersion.isEmpty()) {
                    Path parentJsonPath = minecraftDir.resolve("versions").resolve(parentVersion)
                            .resolve(parentVersion + ".json");
                    if (Files.exists(parentJsonPath)) {
                        String parentJson = FileUtil.readFileAsString(parentJsonPath);
                        if (parentJson != null && !parentJson.isEmpty()) {
                            JsonObject parentData = JsonParser.parseString(parentJson).getAsJsonObject();
                            collectAllLibraries(parentData, minecraftDir, allLibraries);
                        }
                    } else {
                        logger.debug("Archivo JSON de versión heredada no existe: {}", parentJsonPath);
                    }
                }
            } catch (Exception e) {
                logger.warn("Error al recolectar librerías de versión heredada: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * Obtiene la ruta de una librería desde su definición JSON
     */
    private String getLibraryPath(JsonObject library) {
        // Formato estándar de Mojang
        if (library.has("downloads") && library.getAsJsonObject("downloads").has("artifact")) {
            JsonObject artifact = library.getAsJsonObject("downloads").getAsJsonObject("artifact");
            if (artifact.has("path")) {
                return artifact.get("path").getAsString();
            }
        }
        
        // Formato Maven (común en Forge)
        if (library.has("name")) {
            String name = library.get("name").getAsString();
            return getPathFromMavenCoordinates(name);
        }
        
        return null;
    }
    
    /**
     * Convierte coordenadas Maven a ruta de archivo
     */
    private String getPathFromMavenCoordinates(String name) {
        String[] parts = name.split(":");
        if (parts.length < 3) {
            return null;
        }
        String domain = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return domain + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
    }
    
    /**
     * Verifica si una librería debe ser incluida según las reglas del sistema operativo
     */
    private boolean shouldIncludeLibrary(JsonObject library) {
        if (!library.has("rules")) {
            return true;
        }
        
        JsonArray rules = library.getAsJsonArray("rules");
        boolean shouldInclude = false;
        
        for (JsonElement ruleElement : rules) {
            JsonObject rule = ruleElement.getAsJsonObject();
            String action = rule.has("action") ? rule.get("action").getAsString() : "allow";
            
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
                    matchesOS = true;
                }
                
                if (matchesOS) {
                    shouldInclude = "allow".equals(action);
                }
            } else {
                shouldInclude = "allow".equals(action);
            }
        }
        
        return shouldInclude;
    }
    
    /**
     * Extrae natives si es necesario para la versión
     */
    private void extractNativesIfNeeded(JsonObject versionData, String forgeVersion, Path minecraftDir) {
        try {
            Path nativesDir = minecraftDir.resolve("versions").resolve(forgeVersion).resolve("natives");
            FileUtil.createDirectory(nativesDir);
            
            // Verificar si ya están extraídos
            if (Files.exists(nativesDir) && !FileUtil.isDirectoryEmpty(nativesDir)) {
                logger.debug("Natives ya extraídos para {}", forgeVersion);
                return;
            }
            
            // Obtener todas las librerías (incluyendo herencia)
            List<JsonObject> allLibraries = new ArrayList<>();
            collectAllLibraries(versionData, minecraftDir, allLibraries);
            
            Path instanceLibrariesDir = minecraftDir.resolve("libraries");
            Path sharedLibrariesDir = PlatformUtil.getSharedLibrariesDirectory();
            
            PlatformUtil.OS currentOS = PlatformUtil.getOS();
            String nativeClassifier = getNativeClassifier(currentOS);
            
            // Buscar librerías con natives
            for (JsonObject library : allLibraries) {
                if (!shouldIncludeLibrary(library)) {
                    continue;
                }
                
                // Buscar natives en downloads.classifiers
                if (library.has("downloads") && library.getAsJsonObject("downloads").has("classifiers")) {
                    JsonObject classifiers = library.getAsJsonObject("downloads").getAsJsonObject("classifiers");
                    if (classifiers.has(nativeClassifier)) {
                        JsonObject nativeInfo = classifiers.getAsJsonObject(nativeClassifier);
                        String nativePath = nativeInfo.get("path").getAsString();
                        
                        // Buscar el JAR nativo
                        Path nativeJar = sharedLibrariesDir.resolve(nativePath);
                        if (!Files.exists(nativeJar)) {
                            nativeJar = instanceLibrariesDir.resolve(nativePath);
                        }
                        
                        if (Files.exists(nativeJar)) {
                            extractNativeJar(nativeJar, nativesDir);
                        }
                    }
                }
                
                // También verificar formato Maven con classifier
                if (library.has("name")) {
                    String name = library.get("name").getAsString();
                    if (name.contains(":" + nativeClassifier)) {
                        String libraryPath = getPathFromMavenCoordinates(name);
                        Path nativeJar = sharedLibrariesDir.resolve(libraryPath);
                        if (!Files.exists(nativeJar)) {
                            nativeJar = instanceLibrariesDir.resolve(libraryPath);
                        }
                        
                        if (Files.exists(nativeJar)) {
                            extractNativeJar(nativeJar, nativesDir);
                        }
                    }
                }
            }
            
            logger.info("Natives extraídos para {}", forgeVersion);
            
        } catch (Exception e) {
            logger.warn("Error al extraer natives (puede no ser crítico)", e);
            // No lanzar excepción, algunos mods no requieren natives
        }
    }
    
    /**
     * Obtiene el classifier de natives según el OS
     */
    private String getNativeClassifier(PlatformUtil.OS os) {
        switch (os) {
            case WINDOWS:
                return "natives-windows";
            case MACOS:
                return "natives-osx";
            case LINUX:
                return "natives-linux";
            default:
                return "natives-windows"; // Default
        }
    }
    
    /**
     * Extrae archivos nativos de un JAR
     */
    private void extractNativeJar(Path nativeJar, Path nativesDir) {
        try {
            logger.debug("Extrayendo natives de {} a {}", nativeJar, nativesDir);
            
            try (ZipFile zipFile = new ZipFile(nativeJar.toFile())) {
                var entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    
                    // Solo extraer archivos nativos (.dll, .so, .dylib, .jnilib)
                    if (!entry.isDirectory() && 
                        (name.endsWith(".dll") || name.endsWith(".so") || 
                         name.endsWith(".dylib") || name.endsWith(".jnilib"))) {
                        
                        Path outputFile = nativesDir.resolve(new File(name).getName());
                        if (!Files.exists(outputFile)) {
                            try (var inputStream = zipFile.getInputStream(entry);
                                 var outputStream = Files.newOutputStream(outputFile)) {
                                inputStream.transferTo(outputStream);
                            }
                            logger.debug("Nativo extraído: {}", outputFile.getFileName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error al extraer natives de {}", nativeJar, e);
        }
    }
    
    /**
     * Pre-descarga librerías críticas de Forge antes de ejecutar el instalador
     * Esto reduce la carga del instalador y evita timeouts
     */
    private void preDownloadCriticalForgeLibraries(String forgeVersion, Path minecraftDir, Consumer<String> statusCallback) {
        try {
            logger.info("Pre-descargando librerías críticas de Forge para {}", forgeVersion);
            
            // Librerías críticas comunes de Forge que suelen causar problemas de timeout
            List<String> criticalLibraries = new ArrayList<>();
            
            // Intentar extraer librerías críticas del instalador si es posible
            Path tempDir = minecraftDir.resolve("temp");
            Path installerPath = tempDir.resolve("forge-installer.jar");
            
            if (Files.exists(installerPath)) {
                try (ZipFile zipFile = new ZipFile(installerPath.toFile())) {
                    // Buscar archivo install_profile.json o version.json dentro del instalador
                    ZipEntry installProfile = zipFile.getEntry("install_profile.json");
                    if (installProfile != null) {
                        try (var inputStream = zipFile.getInputStream(installProfile)) {
                            // Leer el contenido del JSON de forma compatible
                            StringBuilder jsonBuilder = new StringBuilder();
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                jsonBuilder.append(new String(buffer, 0, bytesRead));
                            }
                            String installProfileJson = jsonBuilder.toString();
                            JsonObject profile = JsonParser.parseString(installProfileJson).getAsJsonObject();
                            
                            // Extraer librerías críticas del perfil de instalación
                            if (profile.has("libraries")) {
                                JsonArray libraries = profile.getAsJsonArray("libraries");
                                for (JsonElement libElement : libraries) {
                                    JsonObject library = libElement.getAsJsonObject();
                                    if (library.has("name")) {
                                        String name = library.get("name").getAsString();
                                        // Priorizar librerías críticas conocidas
                                        if (name.contains("securejarhandler") || 
                                            name.contains("bootstraplauncher") ||
                                            name.contains("modlauncher")) {
                                            criticalLibraries.add(name);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.debug("No se pudo extraer librerías del instalador, usando lista predeterminada: {}", e.getMessage());
                }
            }
            
            // Si no se encontraron librerías en el instalador, usar lista predeterminada
            if (criticalLibraries.isEmpty()) {
                // Librerías críticas comunes basadas en versiones recientes de Forge
                criticalLibraries.add("cpw.mods:securejarhandler:2.1.10");
                criticalLibraries.add("cpw.mods:bootstraplauncher:1.1.2");
                criticalLibraries.add("cpw.mods:modlauncher:10.0.8");
            }
            
            // Descargar librerías críticas
            Path sharedLibrariesDir = PlatformUtil.getSharedLibrariesDirectory();
            int downloaded = 0;
            
            for (String libraryName : criticalLibraries) {
                try {
                    String libraryPath = getPathFromMavenCoordinates(libraryName);
                    Path libraryFile = sharedLibrariesDir.resolve(libraryPath);
                    
                    // Solo descargar si no existe
                    if (!Files.exists(libraryFile)) {
                        String baseUrl = "https://maven.minecraftforge.net/";
                        if (libraryName.startsWith("cpw.mods")) {
                            baseUrl = "https://maven.minecraftforge.net/";
                        } else {
                            baseUrl = "https://repo1.maven.org/maven2/";
                        }
                        
                        String url = baseUrl + libraryPath;
                        
                        if (statusCallback != null) {
                            statusCallback.accept("Pre-descargando " + libraryName + "...");
                        }
                        
                        logger.info("Pre-descargando librería crítica: {} desde {}", libraryName, url);
                        downloadService.downloadFileWithTimeout(url, libraryFile, 300, null);
                        downloaded++;
                        logger.info("Librería crítica descargada: {}", libraryName);
                    } else {
                        logger.debug("Librería crítica ya existe: {}", libraryName);
                    }
                } catch (Exception e) {
                    logger.warn("No se pudo pre-descargar librería crítica {}: {}. El instalador intentará descargarla.", 
                        libraryName, e.getMessage());
                }
            }
            
            if (downloaded > 0) {
                logger.info("Pre-descargadas {} librerías críticas de Forge", downloaded);
            }
            
        } catch (Exception e) {
            logger.warn("Error al pre-descargar librerías críticas de Forge: {}. Continuando con instalación normal.", e.getMessage());
            // No lanzar excepción, el instalador puede funcionar sin pre-descarga
        }
    }
    
    /**
     * Detecta si un error es de timeout basándose en la salida del instalador
     */
    private boolean detectTimeoutError(String output, String errorOutput) {
        String combined = output + "\n" + errorOutput;
        String lower = combined.toLowerCase();
        
        return lower.contains("timeout") ||
               lower.contains("read timed out") ||
               lower.contains("connect timed out") ||
               lower.contains("socket timeout") ||
               lower.contains("timed out") ||
               lower.contains("failed to download") ||
               (lower.contains("these libraries failed to download") && lower.contains("securejarhandler"));
    }
    
    /**
     * Extrae nombres de librerías faltantes de la salida del instalador
     */
    private List<String> extractMissingLibraries(String output) {
        List<String> missingLibraries = new ArrayList<>();
        
        try {
            // Buscar líneas que mencionen librerías que fallaron
            String[] lines = output.split("\n");
            boolean inFailedSection = false;
            
            for (String line : lines) {
                String lowerLine = line.toLowerCase();
                
                // Detectar inicio de sección de librerías fallidas
                if (lowerLine.contains("these libraries failed to download") ||
                    lowerLine.contains("failed to download") ||
                    lowerLine.contains("libraries failed")) {
                    inFailedSection = true;
                    continue;
                }
                
                // Si estamos en la sección de fallos, buscar nombres de librerías
                if (inFailedSection) {
                    // Formato común: "cpw.mods:securejarhandler:2.1.10"
                    // O: "cpw.mods:securejarhandler:2.1.10 - Failed"
                    if (line.contains(":") && (line.contains("cpw.mods") || 
                                              line.contains("net.minecraftforge") ||
                                              line.contains("org.objectweb.asm"))) {
                        // Extraer el nombre de la librería (formato Maven)
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length > 0) {
                            String libraryName = parts[0].trim();
                            // Verificar que tiene formato Maven (group:artifact:version)
                            if (libraryName.split(":").length >= 3) {
                                missingLibraries.add(libraryName);
                                logger.debug("Librería faltante detectada: {}", libraryName);
                            }
                        }
                    }
                    
                    // Si encontramos una línea vacía o un nuevo mensaje, salir de la sección
                    if (line.trim().isEmpty() && missingLibraries.size() > 0) {
                        break;
                    }
                }
            }
            
            // También buscar patrones específicos en la salida
            // Ejemplo: "cpw.mods:securejarhandler:2.1.10"
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "([a-zA-Z0-9.]+:[a-zA-Z0-9._-]+:[a-zA-Z0-9._-]+)"
            );
            java.util.regex.Matcher matcher = pattern.matcher(output);
            while (matcher.find()) {
                String libraryName = matcher.group(1);
                if (!missingLibraries.contains(libraryName) && 
                    (libraryName.contains("cpw.mods") || 
                     libraryName.contains("net.minecraftforge") ||
                     libraryName.contains("org.objectweb.asm"))) {
                    missingLibraries.add(libraryName);
                    logger.debug("Librería faltante detectada (regex): {}", libraryName);
                }
            }
            
        } catch (Exception e) {
            logger.warn("Error al extraer librerías faltantes de la salida: {}", e.getMessage());
        }
        
        return missingLibraries;
    }
    
    /**
     * Descarga librerías faltantes manualmente
     */
    private void downloadMissingLibraries(List<String> libraryNames, Path minecraftDir, Consumer<String> statusCallback) {
        if (libraryNames.isEmpty()) {
            return;
        }
        
        logger.info("Descargando {} librerías faltantes manualmente...", libraryNames.size());
        
        Path sharedLibrariesDir = PlatformUtil.getSharedLibrariesDirectory();
        int downloaded = 0;
        int failed = 0;
        
        for (String libraryName : libraryNames) {
            try {
                String libraryPath = getPathFromMavenCoordinates(libraryName);
                if (libraryPath == null) {
                    logger.warn("No se pudo convertir a ruta: {}", libraryName);
                    failed++;
                    continue;
                }
                
                Path libraryFile = sharedLibrariesDir.resolve(libraryPath);
                
                // Solo descargar si no existe
                if (!Files.exists(libraryFile)) {
                    // Determinar URL base según el grupo
                    String baseUrl;
                    if (libraryName.startsWith("cpw.mods") || libraryName.startsWith("net.minecraftforge")) {
                        baseUrl = "https://maven.minecraftforge.net/";
                    } else {
                        baseUrl = "https://repo1.maven.org/maven2/";
                    }
                    
                    String url = baseUrl + libraryPath;
                    
                    if (statusCallback != null) {
                        statusCallback.accept("Descargando " + libraryName + "...");
                    }
                    
                    logger.info("Descargando librería faltante: {} desde {}", libraryName, url);
                    downloadService.downloadFileWithTimeout(url, libraryFile, 300, null);
                    downloaded++;
                    logger.info("Librería descargada exitosamente: {}", libraryName);
                } else {
                    logger.debug("Librería ya existe: {}", libraryName);
                }
            } catch (Exception e) {
                logger.error("Error al descargar librería faltante {}: {}", libraryName, e.getMessage());
                failed++;
            }
        }
        
        logger.info("Descarga de librerías faltantes completada: {} exitosas, {} fallidas", downloaded, failed);
        
        if (statusCallback != null && downloaded > 0) {
            statusCallback.accept(String.format("Descargadas %d librerías faltantes", downloaded));
        }
    }
    
    /**
     * Limpia archivos temporales bloqueados del sistema (como en Python)
     * Esto evita errores de archivos bloqueados que pueden causar fallos en la instalación
     */
    private void cleanBlockedTemporaryFiles(Path minecraftDir) {
        try {
            logger.info("Limpiando archivos temporales bloqueados de Forge...");
            
            // Limpiar directorios temporales que empiecen con minecraft-launcher-lib-forge-install-
            Path tempBaseDir = minecraftDir.getParent();
            if (tempBaseDir != null && Files.exists(tempBaseDir)) {
                try (Stream<Path> paths = Files.list(tempBaseDir)) {
                    paths.filter(Files::isDirectory)
                         .filter(path -> {
                             String dirName = path.getFileName().toString();
                             return dirName.startsWith("minecraft-launcher-lib-forge-install-");
                         })
                         .forEach(path -> {
                             try {
                                 logger.debug("Eliminando directorio temporal bloqueado: {}", path);
                                 FileUtil.deleteDirectory(path);
                             } catch (Exception e) {
                                 logger.debug("No se pudo eliminar directorio temporal (puede estar bloqueado): {}", path, e);
                             }
                         });
                }
            }
            
            // Limpiar archivos temporales en el directorio temp de la instancia
            // IMPORTANTE: NO eliminar el instalador actual (forge-installer.jar) que acabamos de descargar
            Path tempDir = minecraftDir.resolve("temp");
            if (Files.exists(tempDir)) {
                try (Stream<Path> paths = Files.walk(tempDir)) {
                    paths.sorted((a, b) -> b.compareTo(a)) // Eliminar archivos antes que directorios
                         .forEach(path -> {
                             try {
                                 // Solo eliminar archivos .lock, .tmp, etc. que puedan estar bloqueados
                                 // NO eliminar forge-installer.jar actual (solo archivos viejos/bloqueados)
                                 String fileName = path.getFileName().toString().toLowerCase();
                                 
                                 // Verificar si es un archivo que debemos eliminar
                                 boolean shouldDelete = false;
                                 
                                 if (fileName.endsWith(".lock") || 
                                     fileName.endsWith(".tmp") || 
                                     fileName.endsWith(".temp")) {
                                     shouldDelete = true;
                                 } else if (fileName.contains("forge-installer")) {
                                     // Solo eliminar instaladores antiguos si tienen extensión .log o .tmp
                                     // NO eliminar el .jar actual
                                     if (fileName.endsWith(".log") || 
                                         fileName.endsWith(".tmp") || 
                                         fileName.endsWith(".temp")) {
                                         shouldDelete = true;
                                     }
                                     // NO eliminar forge-installer.jar (el instalador actual)
                                 }
                                 
                                 if (shouldDelete) {
                                     Files.deleteIfExists(path);
                                     logger.debug("Eliminado archivo temporal: {}", path);
                                 }
                             } catch (Exception e) {
                                 logger.debug("No se pudo eliminar archivo temporal: {}", path, e);
                             }
                         });
                }
            }
            
            logger.info("Limpieza de archivos temporales completada");
        } catch (Exception e) {
            logger.warn("Error al limpiar archivos temporales bloqueados (no crítico): {}", e.getMessage());
            // No lanzar excepción, esto es solo una limpieza preventiva
        }
    }
}
