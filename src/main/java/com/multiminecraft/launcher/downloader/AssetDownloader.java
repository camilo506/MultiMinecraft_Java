package com.multiminecraft.launcher.downloader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.multiminecraft.launcher.config.Config;
import com.multiminecraft.launcher.manager.VersionManager;
import com.multiminecraft.launcher.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Descargador de assets de Minecraft
 * Descarga todos los assets de una versión en paralelo
 */
public class AssetDownloader {
    
    private static final Logger logger = LoggerFactory.getLogger(AssetDownloader.class);
    private static final String ASSETS_BASE_URL = "https://resources.download.minecraft.net/";
    
    private final ParallelDownloader downloader;
    
    public AssetDownloader() {
        this.downloader = new ParallelDownloader();
    }
    
    /**
     * Descarga todos los assets de una versión
     * @param versionInfo Información de la versión
     * @param destination Directorio de destino (.minecraft)
     * @param callback Callback de progreso
     * @return true si la descarga fue exitosa
     */
    public boolean downloadAssets(VersionManager.VersionInfo versionInfo, String destination, 
                                 Consumer<String> callback) {
        try {
            if (callback != null) {
                callback.accept("Preparando descarga de assets...");
            }
            
            // Obtener el JSON de la versión para obtener el asset index
            String versionJson = downloadVersionJson(versionInfo.getUrl());
            JsonObject versionData = JsonParser.parseString(versionJson).getAsJsonObject();
            
            if (!versionData.has("assetIndex")) {
                logger.warn("La versión no tiene assetIndex");
                return false;
            }
            
            JsonObject assetIndex = versionData.getAsJsonObject("assetIndex");
            String assetIndexId = assetIndex.get("id").getAsString();
            String assetIndexUrl = assetIndex.get("url").getAsString();
            
            Path minecraftDir = Path.of(destination);
            Path assetsDir = minecraftDir.resolve("assets");
            Path indexesDir = assetsDir.resolve("indexes");
            Path objectsDir = assetsDir.resolve("objects");
            
            FileUtil.createDirectory(indexesDir);
            FileUtil.createDirectory(objectsDir);
            
            // Descargar índice de assets
            Path indexFile = indexesDir.resolve(assetIndexId + ".json");
            if (!Files.exists(indexFile)) {
                if (callback != null) {
                    callback.accept("Descargando índice de assets...");
                }
                String indexJson = downloadString(assetIndexUrl);
                FileUtil.writeStringToFile(indexFile, indexJson);
            }
            
            // Cargar índice de assets
            String indexJson = FileUtil.readFileAsString(indexFile);
            JsonObject indexData = JsonParser.parseString(indexJson).getAsJsonObject();
            JsonObject objects = indexData.getAsJsonObject("objects");
            
            // Preparar lista de tareas de descarga
            List<ParallelDownloader.DownloadTask> downloadTasks = new ArrayList<>();
            
            for (String key : objects.keySet()) {
                JsonObject assetObj = objects.getAsJsonObject(key);
                String hash = assetObj.get("hash").getAsString();
                
                String hashPrefix = hash.substring(0, 2);
                Path assetFile = objectsDir.resolve(hashPrefix).resolve(hash);
                
                // Solo agregar si no existe
                if (!Files.exists(assetFile)) {
                    String assetUrl = ASSETS_BASE_URL + hashPrefix + "/" + hash;
                    downloadTasks.add(new ParallelDownloader.DownloadTask(assetUrl, assetFile, hash));
                }
            }
            
            int totalAssets = objects.size();
            int missingAssets = downloadTasks.size();
            
            if (downloadTasks.isEmpty()) {
                logger.info("Todos los assets ya están descargados ({})", totalAssets);
                if (callback != null) {
                    callback.accept("Todos los assets ya están descargados");
                }
                return true;
            }
            
            if (callback != null) {
                callback.accept(String.format("Descargando %d assets en paralelo...", missingAssets));
            }
            logger.info("Descargando {} assets en paralelo (de {} totales)...", missingAssets, totalAssets);
            
            // Descargar en paralelo
            downloader.downloadFiles(downloadTasks, (progress, completed, total) -> {
                if (callback != null) {
                    callback.accept(String.format("Descargando assets: %d/%d", completed, missingAssets));
                }
            });
            
            logger.info("Assets descargados: {}/{}", missingAssets, totalAssets);
            if (callback != null) {
                callback.accept("Assets descargados correctamente");
            }
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error al descargar assets", e);
            if (callback != null) {
                callback.accept("Error al descargar assets: " + e.getMessage());
            }
            return false;
        }
    }
    
    /**
     * Descarga el JSON de una versión
     */
    private String downloadVersionJson(String versionUrl) throws IOException, InterruptedException {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(Config.DOWNLOAD_TIMEOUT))
                .build();
        
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(versionUrl))
                .timeout(java.time.Duration.ofSeconds(Config.DOWNLOAD_TIMEOUT))
                .GET()
                .build();
        
        java.net.http.HttpResponse<String> response = client.send(
                request, 
                java.net.http.HttpResponse.BodyHandlers.ofString()
        );
        
        if (response.statusCode() != 200) {
            throw new IOException("Error HTTP " + response.statusCode() + " al descargar versión");
        }
        
        return response.body();
    }
    
    /**
     * Descarga una cadena desde una URL
     */
    private String downloadString(String url) throws IOException, InterruptedException {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(Config.DOWNLOAD_TIMEOUT))
                .build();
        
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(Config.DOWNLOAD_TIMEOUT))
                .GET()
                .build();
        
        java.net.http.HttpResponse<String> response = client.send(
                request, 
                java.net.http.HttpResponse.BodyHandlers.ofString()
        );
        
        if (response.statusCode() != 200) {
            throw new IOException("Error HTTP " + response.statusCode() + " al descargar: " + url);
        }
        
        return response.body();
    }
    
    /**
     * Cierra el descargador
     */
    public void shutdown() {
        downloader.shutdown();
    }
}
