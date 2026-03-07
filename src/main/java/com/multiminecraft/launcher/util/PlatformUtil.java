package com.multiminecraft.launcher.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utilidad para detectar la plataforma y obtener rutas específicas del sistema
 */
public class PlatformUtil {

    private static final Logger logger = LoggerFactory.getLogger(PlatformUtil.class);

    public enum OS {
        WINDOWS, MACOS, LINUX, UNKNOWN
    }

    private static OS currentOS;

    /**
     * Detecta el sistema operativo actual
     */
    public static OS getOS() {
        if (currentOS == null) {
            String osName = System.getProperty("os.name").toLowerCase();

            if (osName.contains("win")) {
                currentOS = OS.WINDOWS;
            } else if (osName.contains("mac")) {
                currentOS = OS.MACOS;
            } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
                currentOS = OS.LINUX;
            } else {
                currentOS = OS.UNKNOWN;
            }

            logger.info("Sistema operativo detectado: {}", currentOS);
        }

        return currentOS;
    }

    /**
     * Obtiene el directorio base del launcher según el sistema operativo
     */
    public static Path getLauncherDirectory() {
        String userHome = System.getProperty("user.home");
        Path launcherDir;

        switch (getOS()) {
            case WINDOWS:
                String appData = System.getenv("APPDATA");
                if (appData != null) {
                    launcherDir = Paths.get(appData, ".MultiMinecraft_Java");
                } else {
                    launcherDir = Paths.get(userHome, ".MultiMinecraft_Java");
                }
                break;

            case MACOS:
                launcherDir = Paths.get(userHome, "Library", "Application Support", "MultiMinecraft_Java");
                break;

            case LINUX:
                String xdgData = System.getenv("XDG_DATA_HOME");
                if (xdgData != null) {
                    launcherDir = Paths.get(xdgData, "MultiMinecraft_Java");
                } else {
                    launcherDir = Paths.get(userHome, ".MultiMinecraft_Java");
                }
                break;

            default:
                launcherDir = Paths.get(userHome, ".MultiMinecraft_Java");
                break;
        }

        // Crear directorio si no existe
        File dir = launcherDir.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
            logger.info("Directorio del launcher creado: {}", launcherDir);
        }

        return launcherDir;
    }

    /**
     * Obtiene el directorio de instancias
     */
    public static Path getInstancesDirectory() {
        return getLauncherDirectory().resolve("Instancias");
    }

    /**
     * Obtiene el directorio de logs
     */
    public static Path getLogsDirectory() {
        return getLauncherDirectory().resolve("logs");
    }

    /**
     * Obtiene el directorio de configuración
     */
    public static Path getConfigDirectory() {
        return getLauncherDirectory().resolve("config");
    }

    /**
     * Obtiene el directorio compartido de librerías (compartido entre todas las
     * instancias)
     */
    public static Path getSharedLibrariesDirectory() {
        Path libsDir = getLauncherDirectory().resolve("libraries");
        File dir = libsDir.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
            logger.info("Directorio compartido de librerías creado: {}", libsDir);
        }
        return libsDir;
    }

    /**
     * Obtiene el directorio compartido de assets (compartido entre todas las
     * instancias)
     */
    public static Path getSharedAssetsDirectory() {
        Path assetsDir = getLauncherDirectory().resolve("assets");
        File dir = assetsDir.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
            logger.info("Directorio compartido de assets creado: {}", assetsDir);
        }
        return assetsDir;
    }

    /**
     * Obtiene la ruta del ejecutable de Java del sistema
     */
    public static String getSystemJavaPath() {
        String javaHome = System.getProperty("java.home");
        String javaBin = getOS() == OS.WINDOWS ? "java.exe" : "java";
        return Paths.get(javaHome, "bin", javaBin).toString();
    }

    /**
     * Determina la versión mínima de Java requerida para una versión de Minecraft.
     * - Minecraft 1.20.5+ requiere Java 21
     * - Minecraft 1.17+ requiere Java 17
     * - Versiones anteriores funcionan con Java 8+
     *
     * @param minecraftVersion Versión de Minecraft (ej: "1.21.11", "1.20.1")
     * @return Versión mínima de Java requerida (21, 17, 8)
     */
    public static int getRequiredJavaVersion(String minecraftVersion) {
        try {
            String[] parts = minecraftVersion.split("\\.");
            if (parts.length >= 2) {
                int major = Integer.parseInt(parts[0]);
                int minor = Integer.parseInt(parts[1]);
                // 1.20.5+ requiere Java 21
                if (major > 1 || (major == 1 && minor >= 21)) {
                    return 21;
                }
                if (major == 1 && minor == 20) {
                    // 1.20.5+ requiere Java 21, 1.20.0-1.20.4 requiere Java 17
                    if (parts.length >= 3) {
                        int patch = Integer.parseInt(parts[2]);
                        if (patch >= 5) return 21;
                    }
                    return 17;
                }
                if (major == 1 && minor >= 17) {
                    return 17;
                }
            }
        } catch (NumberFormatException e) {
            logger.debug("No se pudo parsear versión de Minecraft: {}", minecraftVersion);
        }
        return 8; // Java 8 por defecto para versiones antiguas
    }

    /**
     * Busca automáticamente una instalación de Java con la versión mínima requerida.
     * Busca en ubicaciones comunes del sistema operativo.
     *
     * @param minRequiredVersion Versión mínima requerida (ej: 21, 17, 8)
     * @return Ruta al ejecutable de Java encontrado, o null si no se encuentra
     */
    public static String findJavaInstallation(int minRequiredVersion) {
        String javaBin = getOS() == OS.WINDOWS ? "java.exe" : "java";
        
        // Verificar si el Java actual del sistema cumple el requisito
        int currentVersion = getCurrentJavaVersion();
        if (currentVersion >= minRequiredVersion) {
            logger.debug("Java del sistema (versión {}) cumple el requisito (>= {})", currentVersion, minRequiredVersion);
            return getSystemJavaPath();
        }

        logger.info("Java del sistema es versión {} pero se requiere {}. Buscando otra instalación...", 
                     currentVersion, minRequiredVersion);
        
        // Lista de rutas candidatas para buscar
        java.util.List<Path> candidates = new java.util.ArrayList<>();
        
        if (getOS() == OS.WINDOWS) {
            // Eclipse Adoptium / Temurin
            addJavaCandidates(candidates, "C:\\Program Files\\Eclipse Adoptium", minRequiredVersion, javaBin);
            // Oracle JDK
            addJavaCandidates(candidates, "C:\\Program Files\\Java", minRequiredVersion, javaBin);
            // Microsoft JDK
            addJavaCandidates(candidates, "C:\\Program Files\\Microsoft", minRequiredVersion, javaBin);
            // Amazon Corretto
            addJavaCandidates(candidates, "C:\\Program Files\\Amazon Corretto", minRequiredVersion, javaBin);
            // Zulu
            addJavaCandidates(candidates, "C:\\Program Files\\Zulu", minRequiredVersion, javaBin);
            // BellSoft Liberica
            addJavaCandidates(candidates, "C:\\Program Files\\BellSoft", minRequiredVersion, javaBin);
        } else if (getOS() == OS.MACOS) {
            addJavaCandidates(candidates, "/Library/Java/JavaVirtualMachines", minRequiredVersion, javaBin);
            addJavaCandidates(candidates, System.getProperty("user.home") + "/.sdkman/candidates/java", minRequiredVersion, javaBin);
        } else { // Linux
            addJavaCandidates(candidates, "/usr/lib/jvm", minRequiredVersion, javaBin);
            addJavaCandidates(candidates, System.getProperty("user.home") + "/.sdkman/candidates/java", minRequiredVersion, javaBin);
        }

        // Verificar cada candidato
        for (Path candidate : candidates) {
            if (java.nio.file.Files.exists(candidate) && java.nio.file.Files.isExecutable(candidate)) {
                int ver = detectJavaVersionFromPath(candidate.getParent().getParent().toString());
                if (ver >= minRequiredVersion) {
                    logger.info("Encontrada instalación de Java {} en: {}", ver, candidate);
                    return candidate.toString();
                }
            }
        }

        logger.warn("No se encontró una instalación de Java >= {} en el sistema", minRequiredVersion);
        return null;
    }

    /**
     * Obtiene la versión de Java del runtime actual
     */
    public static int getCurrentJavaVersion() {
        String version = System.getProperty("java.version");
        return parseJavaVersionString(version);
    }

    /**
     * Parsea un string de versión de Java y devuelve el número mayor.
     * Ejemplos: "17.0.17" -> 17, "21.0.7" -> 21, "1.8.0_402" -> 8
     */
    public static int parseJavaVersionString(String version) {
        try {
            if (version.startsWith("1.")) {
                // Formato antiguo: 1.8.0_xxx
                return Integer.parseInt(version.split("\\.")[1]);
            } else {
                // Formato moderno: 17.0.x, 21.0.x
                String majorStr = version.split("[.+-]")[0];
                return Integer.parseInt(majorStr);
            }
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Busca en un directorio base instalaciones de Java que cumplan la versión mínima
     */
    private static void addJavaCandidates(java.util.List<Path> candidates, String baseDir, int minVersion, String javaBin) {
        File base = new File(baseDir);
        if (!base.exists() || !base.isDirectory()) return;

        File[] children = base.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (!child.isDirectory()) continue;
            int detectedVersion = detectJavaVersionFromPath(child.getAbsolutePath());
            if (detectedVersion >= minVersion) {
                // Buscar el ejecutable en bin/
                Path javaExe = child.toPath().resolve("bin").resolve(javaBin);
                if (!java.nio.file.Files.exists(javaExe)) {
                    // Puede estar en un subdirectorio (macOS: Contents/Home/bin/)
                    javaExe = child.toPath().resolve("Contents").resolve("Home").resolve("bin").resolve(javaBin);
                }
                candidates.add(javaExe);
            }
        }

        // Ordenar por versión descendente para preferir la más reciente
        candidates.sort((a, b) -> {
            int verA = detectJavaVersionFromPath(a.getParent().getParent().toString());
            int verB = detectJavaVersionFromPath(b.getParent().getParent().toString());
            return Integer.compare(verB, verA);
        });
    }

    /**
     * Detecta la versión de Java a partir de la ruta de instalación.
     * Busca patrones como "jdk-21", "jdk-17.0.17", "java-21-openjdk", etc.
     */
    private static int detectJavaVersionFromPath(String path) {
        String dirName = new File(path).getName().toLowerCase();
        
        // Patrones comunes: jdk-21.0.7.6-hotspot, jdk-17, jdk17, java-21-openjdk, corretto-21
        java.util.regex.Matcher matcher;
        
        // Patrón: jdk-21, jdk-17.0.x, jdk-21.0.7.6-hotspot
        matcher = java.util.regex.Pattern.compile("jdk-?(\\d+)").matcher(dirName);
        if (matcher.find()) {
            try { return Integer.parseInt(matcher.group(1)); } catch (NumberFormatException ignored) {}
        }
        
        // Patrón: java-21-openjdk, java-17-openjdk
        matcher = java.util.regex.Pattern.compile("java-?(\\d+)").matcher(dirName);
        if (matcher.find()) {
            try { return Integer.parseInt(matcher.group(1)); } catch (NumberFormatException ignored) {}
        }
        
        // Patrón: corretto-21, corretto-17
        matcher = java.util.regex.Pattern.compile("corretto-?(\\d+)").matcher(dirName);
        if (matcher.find()) {
            try { return Integer.parseInt(matcher.group(1)); } catch (NumberFormatException ignored) {}
        }
        
        // Patrón: zulu-21, zulu21
        matcher = java.util.regex.Pattern.compile("zulu-?(\\d+)").matcher(dirName);
        if (matcher.find()) {
            try { return Integer.parseInt(matcher.group(1)); } catch (NumberFormatException ignored) {}
        }
        
        return 0;
    }

    /**
     * Obtiene la ruta de Java adecuada para una versión de Minecraft.
     * Si la versión requiere Java 21 y el sistema tiene Java 17, busca una instalación de Java 21.
     *
     * @param minecraftVersion Versión de Minecraft
     * @return Ruta al ejecutable de Java apropiado
     */
    public static String getJavaPathForMinecraft(String minecraftVersion) {
        int requiredVersion = getRequiredJavaVersion(minecraftVersion);
        String javaPath = findJavaInstallation(requiredVersion);
        if (javaPath != null) {
            return javaPath;
        }
        // Fallback: usar el Java del sistema aunque no cumpla (el usuario verá el error)
        logger.warn("No se encontró Java >= {} para Minecraft {}. Usando Java del sistema.", 
                     requiredVersion, minecraftVersion);
        return getSystemJavaPath();
    }

    /**
     * Verifica si el sistema es de 64 bits
     */
    public static boolean is64Bit() {
        String arch = System.getProperty("os.arch");
        return arch.contains("64");
    }

    /**
     * Detecta si hay ventanas visibles asociadas a un proceso en Windows
     * Busca en el proceso principal y sus procesos hijos
     * 
     * @param process El proceso a verificar
     * @return true si se detecta una ventana visible, false en caso contrario
     */
    public static boolean hasVisibleWindow(Process process) {
        if (getOS() != OS.WINDOWS) {
            // En otros sistemas operativos, asumimos que la ventana aparece después de un
            // tiempo
            return process.isAlive();
        }

        try {
            long pid = process.pid();

            // Usar PowerShell para buscar si el proceso tiene un título de ventana
            // principal
            String psCommand = String.format(
                    "Get-Process -Id %d -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -ne '' } | Select-Object -ExpandProperty MainWindowTitle",
                    pid);

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell",
                    "-Command",
                    psCommand);

            Process checkProcess = pb.start();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(checkProcess.getInputStream()));

            String line = reader.readLine();
            checkProcess.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS);

            // Si hay una línea con contenido, significa que hay una ventana con título
            boolean hasWindow = line != null && !line.trim().isEmpty();

            if (hasWindow) {
                logger.info("Ventana detectada para proceso PID {}: {}", pid, line);
            }

            return hasWindow;

        } catch (Exception e) {
            logger.debug("Error al verificar ventana del proceso: {}", e.getMessage());
            // Si hay error, retornar false para seguir intentando
            return false;
        }
    }

    /**
     * Busca ventanas de Minecraft de forma más agresiva
     * Busca cualquier ventana que contenga "Minecraft" o "Mojang" en el título
     * 
     * @return true si encuentra una ventana de Minecraft
     */
    public static boolean findMinecraftWindow() {
        if (getOS() != OS.WINDOWS) {
            return false;
        }

        try {
            // Buscar todas las ventanas que contengan "Minecraft" o "Mojang" en el título
            String psCommand = "Get-Process | Where-Object { $_.MainWindowTitle -ne '' -and " +
                    "($_.MainWindowTitle -like '*Minecraft*' -or $_.MainWindowTitle -like '*Mojang*') } | " +
                    "Select-Object -First 1 -ExpandProperty MainWindowTitle";

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell",
                    "-Command",
                    psCommand);

            Process checkProcess = pb.start();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(checkProcess.getInputStream()));

            String line = reader.readLine();
            checkProcess.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS);

            boolean found = line != null && !line.trim().isEmpty();

            if (found) {
                logger.info("Ventana de Minecraft encontrada: {}", line);
            }

            return found;

        } catch (Exception e) {
            logger.debug("Error al buscar ventana de Minecraft: {}", e.getMessage());
            return false;
        }
    }
}
