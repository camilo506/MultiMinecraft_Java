package com.multiminecraft.launcher.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.multiminecraft.launcher.config.Config;
import com.multiminecraft.launcher.util.FileUtil;
import com.multiminecraft.launcher.util.PlatformUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Servicio para instalar Fabric Loader.
 * Descarga el perfil de versión desde la API de Fabric Meta y las librerías necesarias.
 */
public class FabricService {

    private static final Logger logger = LoggerFactory.getLogger(FabricService.class);
    private static final String FABRIC_META_API = "https://meta.fabricmc.net/v2/versions/loader";
    private static final String FABRIC_MAVEN = "https://maven.fabricmc.net/";
    private static final String MOJANG_MAVEN = "https://libraries.minecraft.net/";

    private final DownloadService downloadService = new DownloadService();

    /**
     * Instala Fabric para una versión específica de Minecraft.
     * 1. Obtiene la última versión de Fabric Loader
     * 2. Descarga el JSON del perfil de versión
     * 3. Descarga todas las librerías declaradas en el perfil
     */
    public void installFabric(String minecraftVersion, Path minecraftDir, Consumer<String> statusCallback)
            throws Exception {
        logger.info("Instalando Fabric para Minecraft {}...", minecraftVersion);

        // 1. Obtener versión más reciente de Fabric Loader
        if (statusCallback != null) statusCallback.accept("Obteniendo versión de Fabric Loader...");
        String loaderVersion = getLatestFabricLoaderVersion();
        if (loaderVersion == null) {
            throw new IOException("No se pudo obtener la versión de Fabric Loader desde la API");
        }
        logger.info("Fabric Loader versión: {}", loaderVersion);

        String versionId = minecraftVersion + "-fabric-" + loaderVersion;

        // 2. Descargar y guardar JSON del perfil
        if (statusCallback != null) statusCallback.accept("Descargando perfil de Fabric...");
        String profileJson = downloadProfileJson(minecraftVersion, loaderVersion);
        JsonObject profile = JsonParser.parseString(profileJson).getAsJsonObject();

        Path versionDir = minecraftDir.resolve("versions").resolve(versionId);
        FileUtil.createDirectory(versionDir);
        FileUtil.writeStringToFile(versionDir.resolve(versionId + ".json"), profileJson);
        logger.info("Perfil de versión guardado: {}", versionId);

        // 3. Descargar librerías del perfil
        if (statusCallback != null) statusCallback.accept("Descargando librerías de Fabric...");
        downloadFabricLibraries(profile, minecraftDir, statusCallback);

        // 4. Asegurar que la versión vanilla base esté descargada
        if (statusCallback != null) statusCallback.accept("Verificando versión vanilla base...");
        ensureVanillaVersion(minecraftVersion, minecraftDir, statusCallback);

        logger.info("Fabric {} instalado correctamente para MC {}", loaderVersion, minecraftVersion);
        if (statusCallback != null) statusCallback.accept("Fabric instalado correctamente");
    }

    /**
     * Obtiene la versión más reciente estable de Fabric Loader desde la API
     */
    private String getLatestFabricLoaderVersion() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Config.DOWNLOAD_TIMEOUT))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(FABRIC_META_API))
                    .timeout(Duration.ofSeconds(Config.DOWNLOAD_TIMEOUT))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.error("Error HTTP {} al consultar versiones de Fabric Loader", response.statusCode());
                return null;
            }

            JsonArray loaders = JsonParser.parseString(response.body()).getAsJsonArray();
            // Buscar la primera versión estable
            for (JsonElement el : loaders) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("stable") && obj.get("stable").getAsBoolean()) {
                    return obj.get("version").getAsString();
                }
            }
            // Si no hay estable, tomar la primera
            if (loaders.size() > 0) {
                return loaders.get(0).getAsJsonObject().get("version").getAsString();
            }
            return null;
        } catch (Exception e) {
            logger.error("Error al obtener versión de Fabric Loader", e);
            return null;
        }
    }

    /**
     * Descarga el JSON del perfil de versión desde Fabric Meta
     */
    private String downloadProfileJson(String minecraftVersion, String loaderVersion) throws Exception {
        String url = String.format("%s/%s/%s/profile/json", FABRIC_META_API, minecraftVersion, loaderVersion);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Config.DOWNLOAD_TIMEOUT))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Config.DOWNLOAD_TIMEOUT))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Error HTTP " + response.statusCode() + " al descargar perfil de Fabric");
        }
        return response.body();
    }

    /**
     * Descarga todas las librerías declaradas en el perfil de versión de Fabric.
     * Las coordenadas Maven se convierten a rutas de archivo y se descargan
     * desde el repositorio Maven de Fabric o de Mojang.
     */
    private void downloadFabricLibraries(JsonObject profile, Path minecraftDir, Consumer<String> statusCallback)
            throws Exception {
        if (!profile.has("libraries")) {
            logger.warn("El perfil de Fabric no contiene librerías");
            return;
        }

        JsonArray libraries = profile.getAsJsonArray("libraries");
        Path sharedLibsDir = PlatformUtil.getSharedLibrariesDirectory();
        List<DownloadService.DownloadTask> tasks = new ArrayList<>();

        for (JsonElement el : libraries) {
            JsonObject lib = el.getAsJsonObject();
            String name = lib.get("name").getAsString(); // ej: "net.fabricmc:fabric-loader:0.16.14"

            // Si tiene URL directa, usarla
            String url = null;
            if (lib.has("url") && !lib.get("url").getAsString().isEmpty()) {
                url = lib.get("url").getAsString();
            }

            // Convertir coordenadas Maven a ruta de archivo
            String[] parts = name.split(":");
            if (parts.length < 3) {
                logger.warn("Coordenada Maven inválida: {}", name);
                continue;
            }
            String group = parts[0].replace('.', '/');
            String artifact = parts[1];
            String version = parts[2];
            String classifier = parts.length > 3 ? "-" + parts[3] : "";
            String fileName = artifact + "-" + version + classifier + ".jar";
            String mavenPath = group + "/" + artifact + "/" + version + "/" + fileName;

            Path destFile = sharedLibsDir.resolve(mavenPath.replace('/', java.io.File.separatorChar));

            if (Files.exists(destFile)) {
                logger.debug("Librería ya existe: {}", name);
                continue;
            }

            // Determinar URL de descarga
            String downloadUrl;
            if (url != null) {
                // URL base del repositorio Maven
                downloadUrl = url + mavenPath;
            } else {
                // Intentar primero Fabric Maven, luego Mojang
                downloadUrl = FABRIC_MAVEN + mavenPath;
            }

            tasks.add(new DownloadService.DownloadTask(downloadUrl, destFile));
        }

        if (tasks.isEmpty()) {
            logger.info("Todas las librerías de Fabric ya están descargadas");
            return;
        }

        logger.info("Descargando {} librerías de Fabric...", tasks.size());
        if (statusCallback != null) {
            statusCallback.accept(String.format("Descargando %d librerías de Fabric...", tasks.size()));
        }

        downloadService.downloadFilesParallel(tasks, 8, progress -> {
            if (statusCallback != null) {
                int done = (int) (progress * tasks.size());
                statusCallback.accept(String.format("Librerías de Fabric: %d/%d", done, tasks.size()));
            }
        });

        logger.info("Librerías de Fabric descargadas correctamente");
    }

    /**
     * Asegura que la versión vanilla base esté presente (JSON + client.jar).
     * Fabric hereda de la versión vanilla (inheritsFrom).
     */
    private void ensureVanillaVersion(String minecraftVersion, Path minecraftDir, Consumer<String> statusCallback)
            throws Exception {
        Path vanillaDir = minecraftDir.resolve("versions").resolve(minecraftVersion);
        Path vanillaJson = vanillaDir.resolve(minecraftVersion + ".json");
        Path vanillaJar = vanillaDir.resolve(minecraftVersion + ".jar");

        if (Files.exists(vanillaJson) && Files.exists(vanillaJar)) {
            logger.debug("Versión vanilla {} ya presente", minecraftVersion);
            return;
        }

        logger.info("Descargando versión vanilla base {}...", minecraftVersion);
        if (statusCallback != null) statusCallback.accept("Descargando Minecraft " + minecraftVersion + "...");

        MojangService mojangService = new MojangService();
        mojangService.downloadVersion(minecraftVersion, minecraftDir, statusCallback);
    }
}
