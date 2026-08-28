package com.multiminecraft.launcher.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.multiminecraft.launcher.util.FileUtil;
import com.multiminecraft.launcher.util.PlatformUtil;
import com.multiminecraft.launcher.util.ZipUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Gestiona runtimes de Java para Minecraft según la versión de instancia.
 */
public class JavaRuntimeService {

    private static final Logger logger = LoggerFactory.getLogger(JavaRuntimeService.class);

    private final DownloadService downloadService;

    public JavaRuntimeService() {
        this.downloadService = new DownloadService();
    }

    /**
     * Asegura que exista una JVM compatible para la versión de Minecraft indicada.
     *
     * Reglas:
     * - 26.x+ -> Java 25 o superior
     * - 25.x / 1.20.5+ -> Java 21 o superior
     * - 1.17 - 1.20.4 -> Java 17
     * - <= 1.16 -> Java 8
     */
    public String ensureJavaForMinecraftVersion(String minecraftVersion, Consumer<String> statusCallback) throws Exception {
        return ensureJavaForMinecraftVersion(minecraftVersion, statusCallback, null);
    }

    public String ensureJavaForMinecraftVersion(String minecraftVersion, Consumer<String> statusCallback,
            Consumer<Double> progressCallback) throws Exception {
        int targetJava = PlatformUtil.getRequiredJavaVersion(minecraftVersion);
        boolean allowHigher = targetJava >= 21;

        String installed = resolveInstalledJava(targetJava, allowHigher);
        if (installed != null && !installed.isBlank()) {
            logger.info("Java ya disponible para Minecraft {}: {}", minecraftVersion, installed);
            if (progressCallback != null) {
                progressCallback.accept(1.0);
            }
            return installed;
        }

        if (statusCallback != null) {
            statusCallback.accept("Instalando Java " + targetJava + " para Minecraft " + minecraftVersion + "...");
        }

        installManagedJava(targetJava, statusCallback, progressCallback);

        String installedAfter = resolveInstalledJava(targetJava, allowHigher);
        if (installedAfter == null || installedAfter.isBlank()) {
            throw new IllegalStateException("Se instaló Java " + targetJava + " pero no se pudo detectar su ejecutable.");
        }
        return installedAfter;
    }

    private String resolveInstalledJava(int targetJava, boolean allowHigher) {
        if (allowHigher) {
            return PlatformUtil.findJavaInstallation(targetJava);
        }
        return PlatformUtil.findJavaInstallationExact(targetJava);
    }

    private void installManagedJava(int major, Consumer<String> statusCallback, Consumer<Double> progressCallback)
            throws Exception {
        if (PlatformUtil.getOS() != PlatformUtil.OS.WINDOWS) {
            throw new UnsupportedOperationException("La instalación automática de Java está soportada por ahora en Windows.");
        }

        String downloadUrl = resolveAdoptiumZipUrl(major);
        Path runtimeRoot = PlatformUtil.getLauncherDirectory().resolve("runtime");
        Path downloadsDir = runtimeRoot.resolve("downloads");
        Path tempDir = runtimeRoot.resolve("tmp").resolve("jdk-" + major + "-" + Instant.now().toEpochMilli());
        Path zipPath = downloadsDir.resolve("jdk-" + major + ".zip");

        Files.createDirectories(downloadsDir);
        Files.createDirectories(tempDir);

        if (statusCallback != null) {
            statusCallback.accept("Descargando Java " + major + "...");
        }
        if (progressCallback != null) {
            progressCallback.accept(0.0);
        }

        downloadService.downloadFile(downloadUrl, zipPath, p -> {
            if (progressCallback != null && p != null) {
                progressCallback.accept(Math.max(0.0, Math.min(0.8, p * 0.8)));
            }
        });

        if (statusCallback != null) {
            statusCallback.accept("Extrayendo Java " + major + "...");
        }
        if (progressCallback != null) {
            progressCallback.accept(0.85);
        }

        ZipUtil.unzip(zipPath, tempDir);

        Path jdkHome = findJavaHomeIn(tempDir);
        if (jdkHome == null) {
            throw new IllegalStateException("No se encontró el ejecutable de Java dentro del ZIP descargado.");
        }

        String folderName = jdkHome.getFileName() != null ? jdkHome.getFileName().toString() : ("jdk-" + major);
        Path finalDir = runtimeRoot.resolve(folderName);

        deleteManagedJavaSameMajor(runtimeRoot, major);

        if (progressCallback != null) {
            progressCallback.accept(0.92);
        }

        Files.move(jdkHome, finalDir, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(zipPath);
        FileUtil.deleteDirectory(tempDir);

        if (progressCallback != null) {
            progressCallback.accept(1.0);
        }

        logger.info("Java {} instalado en: {}", major, finalDir);
    }

    private String resolveAdoptiumZipUrl(int major) throws Exception {
        String apiUrl = "https://api.adoptium.net/v3/assets/latest/" + major
                + "/hotspot?architecture=x64&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=windows&vendor=eclipse";

        String payload = downloadService.downloadString(apiUrl, 60);
        JsonArray assets = JsonParser.parseString(payload).getAsJsonArray();

        for (int i = 0; i < assets.size(); i++) {
            JsonObject asset = assets.get(i).getAsJsonObject();
            if (!asset.has("binary")) {
                continue;
            }
            JsonObject binary = asset.getAsJsonObject("binary");
            if (!binary.has("package")) {
                continue;
            }
            JsonObject packageInfo = binary.getAsJsonObject("package");
            String name = packageInfo.has("name") ? packageInfo.get("name").getAsString() : "";
            String link = packageInfo.has("link") ? packageInfo.get("link").getAsString() : "";
            if (!name.toLowerCase().endsWith(".zip") || link.isBlank()) {
                continue;
            }
            return link;
        }

        // Fallback del endpoint de binario directo.
        return "https://api.adoptium.net/v3/binary/latest/" + major
                + "/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk";
    }

    private Path findJavaHomeIn(Path searchRoot) throws Exception {
        try (Stream<Path> stream = Files.walk(searchRoot)) {
            Path javaExe = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName() != null && p.getFileName().toString().equalsIgnoreCase("java.exe"))
                    .filter(p -> p.getParent() != null && p.getParent().getFileName() != null
                            && p.getParent().getFileName().toString().equalsIgnoreCase("bin"))
                    .findFirst()
                    .orElse(null);

            if (javaExe == null) {
                return null;
            }

            Path binDir = javaExe.getParent();
            return binDir != null ? binDir.getParent() : null;
        }
    }

    private void deleteManagedJavaSameMajor(Path runtimeRoot, int major) {
        if (!Files.isDirectory(runtimeRoot)) {
            return;
        }

        try (Stream<Path> dirs = Files.list(runtimeRoot)) {
            dirs.filter(Files::isDirectory)
                    .filter(dir -> detectJavaMajorFromDirName(dir.getFileName().toString()) == major)
                    .filter(dir -> !Objects.equals(dir.getFileName().toString(), "downloads"))
                    .filter(dir -> !Objects.equals(dir.getFileName().toString(), "tmp"))
                    .sorted(Comparator.reverseOrder())
                    .forEach(dir -> {
                        try {
                            FileUtil.deleteDirectory(dir);
                        } catch (Exception e) {
                            logger.warn("No se pudo eliminar runtime Java previo {}", dir, e);
                        }
                    });
        } catch (Exception e) {
            logger.warn("No se pudo limpiar runtimes Java previos de la versión {}", major, e);
        }
    }

    private int detectJavaMajorFromDirName(String dirName) {
        String normalized = dirName == null ? "" : dirName.toLowerCase();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:jdk|java|corretto|zulu)-?(\\d+)")
                .matcher(normalized);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}