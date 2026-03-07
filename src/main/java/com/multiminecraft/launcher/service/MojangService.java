package com.multiminecraft.launcher.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.multiminecraft.launcher.model.MinecraftVersion;
import com.multiminecraft.launcher.util.FileUtil;
import com.multiminecraft.launcher.util.PlatformUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Servicio para interactuar con la API de Mojang y descargar archivos de
 * Minecraft
 */
public class MojangService {

    private static final Logger logger = LoggerFactory.getLogger(MojangService.class);
    private static final String VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private final DownloadService downloadService;
    private List<MinecraftVersion> availableVersions;

    public MojangService() {
        this.downloadService = new DownloadService();
        this.availableVersions = new ArrayList<>();
    }

    /**
     * Obtiene la lista de versiones disponibles de Minecraft
     */
    public List<MinecraftVersion> fetchVersions() throws IOException, InterruptedException {
        logger.info("Obteniendo lista de versiones de Minecraft...");

        String manifestJson = downloadService.downloadString(VERSION_MANIFEST_URL);
        JsonObject manifest = JsonParser.parseString(manifestJson).getAsJsonObject();

        JsonArray versions = manifest.getAsJsonArray("versions");
        availableVersions.clear();

        for (JsonElement versionElement : versions) {
            JsonObject versionObj = versionElement.getAsJsonObject();

            String id = versionObj.get("id").getAsString();
            String type = versionObj.get("type").getAsString();
            String url = versionObj.get("url").getAsString();
            String releaseTime = versionObj.get("releaseTime").getAsString();

            MinecraftVersion version = new MinecraftVersion(id, type, url);
            version.setReleaseTime(releaseTime);
            availableVersions.add(version);
        }

        logger.info("Se encontraron {} versiones de Minecraft", availableVersions.size());
        return availableVersions;
    }

    /**
     * Obtiene solo las versiones release
     */
    public List<MinecraftVersion> getReleaseVersions() throws IOException, InterruptedException {
        if (availableVersions.isEmpty()) {
            fetchVersions();
        }

        return availableVersions.stream()
                .filter(MinecraftVersion::isRelease)
                .toList();
    }

    /**
     * Busca una versión específica por ID
     */
    public MinecraftVersion getVersion(String versionId) throws IOException, InterruptedException {
        if (availableVersions.isEmpty()) {
            fetchVersions();
        }

        return availableVersions.stream()
                .filter(v -> v.getId().equals(versionId))
                .findFirst()
                .orElseThrow(() -> new IOException("Versión no encontrada: " + versionId));
    }

    /**
     * Descarga los archivos de una versión de Minecraft
     */
    public void downloadVersion(String versionId, Path minecraftDir, Consumer<String> statusCallback)
            throws IOException, InterruptedException, ExecutionException {
        downloadVersion(versionId, minecraftDir, statusCallback, null);
    }

    /**
     * Descarga los archivos de una versión de Minecraft con progreso numérico
     */
    public void downloadVersion(String versionId, Path minecraftDir, Consumer<String> statusCallback, Consumer<Double> progressCallback)
            throws IOException, InterruptedException, ExecutionException {

        logger.info("Descargando Minecraft {}...", versionId);

        // Obtener información de la versión
        MinecraftVersion version = getVersion(versionId);

        // Descargar el JSON de la versión
        if (statusCallback != null)
            statusCallback.accept("Descargando información de versión...");
        if (progressCallback != null)
            progressCallback.accept(0.0);
        String versionJson = downloadService.downloadString(version.getUrl());
        JsonObject versionData = JsonParser.parseString(versionJson).getAsJsonObject();

        // Crear directorios necesarios
        Path versionsDir = minecraftDir.resolve("versions").resolve(versionId);
        // PRIORIDAD: Usar directorio COMPARTIDO de librerías para evitar duplicados
        // Las librerías se comparten entre todas las instancias
        Path librariesDir = PlatformUtil.getSharedLibrariesDirectory();
        FileUtil.createDirectory(versionsDir);
        FileUtil.createDirectory(librariesDir);

        // Guardar JSON de versión
        Path versionJsonFile = versionsDir.resolve(versionId + ".json");
        FileUtil.writeStringToFile(versionJsonFile, versionJson);
        if (progressCallback != null)
            progressCallback.accept(0.05);

        // Descargar client JAR (5% - 30%)
        if (statusCallback != null)
            statusCallback.accept("Descargando cliente de Minecraft...");
        JsonObject downloads = versionData.getAsJsonObject("downloads");
        JsonObject client = downloads.getAsJsonObject("client");
        String clientUrl = client.get("url").getAsString();
        String clientSha1 = client.get("sha1").getAsString();

        Path clientJar = versionsDir.resolve(versionId + ".jar");
        if (!FileUtil.verifyFileHash(clientJar, clientSha1)) {
            downloadService.downloadFile(clientUrl, clientJar, progress -> {
                if (progressCallback != null) {
                    progressCallback.accept(0.05 + progress * 0.25);
                }
            });
        } else {
            logger.info("Cliente ya descargado y verificado");
        }
        if (progressCallback != null)
            progressCallback.accept(0.30);

        // Descargar librerías (30% - 95%)
        if (statusCallback != null)
            statusCallback.accept("Descargando librerías...");
        downloadLibraries(versionData, librariesDir, progress -> {
            if (progressCallback != null) {
                progressCallback.accept(0.30 + progress * 0.65);
            }
        });
        if (progressCallback != null)
            progressCallback.accept(0.95);

        // Descargar solo el índice de assets (los assets se descargarán cuando se lance
        // el juego)
        if (statusCallback != null)
            statusCallback.accept("Preparando assets...");
        downloadAssetIndex(versionData, minecraftDir);
        if (progressCallback != null)
            progressCallback.accept(1.0);

        logger.info("Descarga de Minecraft {} completada", versionId);
    }

    /**
     * Descarga las librerías necesarias en paralelo
     */
    public void downloadLibraries(JsonObject versionData, Path librariesDir)
            throws IOException, InterruptedException, ExecutionException {
        downloadLibraries(versionData, librariesDir, null);
    }

    /**
     * Descarga las librerías necesarias en paralelo con callback de progreso
     */
    public void downloadLibraries(JsonObject versionData, Path librariesDir, Consumer<Double> progressCallback)
            throws IOException, InterruptedException, ExecutionException {

        JsonArray libraries = versionData.getAsJsonArray("libraries");
        List<DownloadService.DownloadTask> downloadTasks = new ArrayList<>();

        for (JsonElement libElement : libraries) {
            JsonObject library = libElement.getAsJsonObject();

            // Verificar reglas (compatibilidad de OS)
            if (library.has("rules") && !checkRules(library.getAsJsonArray("rules"))) {
                continue;
            }

            // Obtener información de descarga
            JsonObject downloads = library.has("downloads") ? library.getAsJsonObject("downloads") : null;
            String path = null;
            String url = null;
            String sha1 = null;

            if (downloads != null && downloads.has("artifact")) {
                // Formato estándar de Mojang
                JsonObject artifact = downloads.getAsJsonObject("artifact");
                path = artifact.get("path").getAsString();
                url = artifact.get("url").getAsString();
                sha1 = artifact.has("sha1") ? artifact.get("sha1").getAsString() : null;
            } else if (library.has("name")) {
                // Formato Maven (común en Forge)
                String name = library.get("name").getAsString();
                path = getPathFromMavenCoordinates(name);

                String baseUrl = "https://repo1.maven.org/maven2/"; // Default Maven Central
                if (library.has("url")) {
                    baseUrl = library.get("url").getAsString();
                } else if (name.startsWith("net.minecraftforge")) {
                    baseUrl = "https://maven.minecraftforge.net/";
                }

                url = baseUrl + path;
            } else {
                continue;
            }

            Path libraryFile = librariesDir.resolve(path);

            // Agregar a la lista si no existe o hash no coincide
            // Nota: Para Maven libs sin sha1, solo verificamos existencia
            if (sha1 == null) {
                if (!libraryFile.toFile().exists()) {
                    downloadTasks.add(new DownloadService.DownloadTask(url, libraryFile));
                }
            } else {
                if (!FileUtil.verifyFileHash(libraryFile, sha1)) {
                    downloadTasks.add(new DownloadService.DownloadTask(url, libraryFile));
                }
            }
        }

        if (!downloadTasks.isEmpty()) {
            logger.info("Descargando {} librerías en paralelo...", downloadTasks.size());
            downloadService.downloadFilesParallel(downloadTasks, 8, progress -> {
                logger.debug("Progreso de librerías: {:.0f}%", progress * 100);
                if (progressCallback != null) {
                    progressCallback.accept(progress);
                }
            });
            logger.info("Librerías descargadas: {}", downloadTasks.size());
        } else {
            logger.info("Todas las librerías ya están descargadas");
        }
    }

    private String getPathFromMavenCoordinates(String name) {
        String[] parts = name.split(":");
        String domain = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return domain + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
    }

    /**
     * Descarga solo el índice de assets (los assets se descargarán cuando se lance
     * el juego)
     */
    private void downloadAssetIndex(JsonObject versionData, Path minecraftDir)
            throws IOException, InterruptedException {

        JsonObject assetIndex = versionData.getAsJsonObject("assetIndex");
        String assetIndexUrl = assetIndex.get("url").getAsString();
        String assetIndexId = assetIndex.get("id").getAsString();

        // Usar directorio de assets de la instancia
        Path assetsDir = minecraftDir.resolve("assets");
        Path indexesDir = assetsDir.resolve("indexes");
        Path objectsDir = assetsDir.resolve("objects");

        FileUtil.createDirectory(indexesDir);
        FileUtil.createDirectory(objectsDir);

        // Descargar solo el índice de assets (no los assets en sí)
        Path indexFile = indexesDir.resolve(assetIndexId + ".json");
        if (!indexFile.toFile().exists()) {
            String indexJson = downloadService.downloadString(assetIndexUrl);
            FileUtil.writeStringToFile(indexFile, indexJson);
            logger.info("Índice de assets descargado: {}", assetIndexId);
        } else {
            logger.info("Índice de assets ya existe: {}", assetIndexId);
        }
    }

    /**
     * Descarga los assets del juego en paralelo (se llama cuando se lanza el juego
     * por primera vez)
     */
    public void downloadAssets(JsonObject versionData, Path minecraftDir, Consumer<String> statusCallback)
            throws IOException, InterruptedException, ExecutionException {

        if (versionData == null || !versionData.has("assetIndex")) {
            logger.warn("No se encontró información de assets (assetIndex) para la versión");
            if (statusCallback != null) {
                statusCallback.accept("No se requieren assets adicionales");
            }
            return;
        }

        JsonObject assetIndex = versionData.getAsJsonObject("assetIndex");
        if (!assetIndex.has("id") || !assetIndex.has("url")) {
            logger.warn("Información de assets incompleta");
            return;
        }

        String assetIndexId = assetIndex.get("id").getAsString();

        // Usar directorio de assets de la instancia
        Path assetsDir = minecraftDir.resolve("assets");
        Path indexesDir = assetsDir.resolve("indexes");
        Path objectsDir = assetsDir.resolve("objects");

        // Cargar índice de assets
        Path indexFile = indexesDir.resolve(assetIndexId + ".json");
        if (!indexFile.toFile().exists()) {
            downloadAssetIndex(versionData, minecraftDir);
        }

        String indexJson = FileUtil.readFileAsString(indexFile);
        JsonObject indexData = JsonParser.parseString(indexJson).getAsJsonObject();
        JsonObject objects = indexData.getAsJsonObject("objects");

        // Preparar lista de tareas de descarga
        List<DownloadService.DownloadTask> downloadTasks = new ArrayList<>();

        for (String key : objects.keySet()) {
            JsonObject assetObj = objects.getAsJsonObject(key);
            String hash = assetObj.get("hash").getAsString();

            String hashPrefix = hash.substring(0, 2);
            Path assetFile = objectsDir.resolve(hashPrefix).resolve(hash);

            // Solo agregar si no existe
            if (!assetFile.toFile().exists()) {
                String assetUrl = "https://resources.download.minecraft.net/" + hashPrefix + "/" + hash;
                downloadTasks.add(new DownloadService.DownloadTask(assetUrl, assetFile));
            }
        }

        int totalAssets = objects.size();
        int missingAssets = downloadTasks.size();

        if (!downloadTasks.isEmpty()) {
            if (statusCallback != null) {
                statusCallback.accept(String.format("Descargando %d assets en paralelo...", missingAssets));
            }
            logger.info("Descargando {} assets en paralelo (de {} totales)...", missingAssets, totalAssets);

            // Descargar en paralelo con hasta 16 conexiones simultáneas
            downloadService.downloadFilesParallel(downloadTasks, 16, progress -> {
                if (statusCallback != null) {
                    int downloaded = (int) (progress * missingAssets);
                    statusCallback.accept(String.format("Descargando assets: %d/%d", downloaded, missingAssets));
                }
            });

            logger.info("Assets descargados: {}/{}", missingAssets, totalAssets);
        } else {
            logger.info("Todos los assets ya están descargados ({})", totalAssets);
            if (statusCallback != null) {
                statusCallback.accept("Todos los assets ya están descargados");
            }
        }
    }

    /**
     * Verifica las reglas de compatibilidad de una librería
     */
    private boolean checkRules(JsonArray rules) {
        boolean allowed = false;

        for (JsonElement ruleElement : rules) {
            JsonObject rule = ruleElement.getAsJsonObject();
            String action = rule.get("action").getAsString();

            boolean applies = true;
            if (rule.has("os")) {
                JsonObject os = rule.getAsJsonObject("os");
                if (os.has("name")) {
                    String osName = os.get("name").getAsString();
                    applies = matchesCurrentOS(osName);
                }
            }

            if (applies) {
                allowed = action.equals("allow");
            }
        }

        return allowed;
    }

    /**
     * Verifica si el nombre del OS coincide con el sistema actual
     */
    private boolean matchesCurrentOS(String osName) {
        String currentOS = System.getProperty("os.name").toLowerCase();

        return switch (osName.toLowerCase()) {
            case "windows" -> currentOS.contains("win");
            case "osx" -> currentOS.contains("mac");
            case "linux" -> currentOS.contains("nix") || currentOS.contains("nux");
            default -> false;
        };
    }
}
