package com.multiminecraft.launcher.installer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.multiminecraft.launcher.config.Config;
import com.multiminecraft.launcher.downloader.AssetDownloader;
import com.multiminecraft.launcher.downloader.LibraryDownloader;
import com.multiminecraft.launcher.downloader.ParallelDownloader;
import com.multiminecraft.launcher.manager.VersionManager;
import com.multiminecraft.launcher.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Instalador de versiones Vanilla de Minecraft
 * Descarga e instala todas las dependencias necesarias
 */
public class MinecraftInstaller {
    
    private static final Logger logger = LoggerFactory.getLogger(MinecraftInstaller.class);
    
    private final AssetDownloader assetDownloader;
    private final LibraryDownloader libraryDownloader;
    private final ParallelDownloader parallelDownloader;
    
    public MinecraftInstaller() {
        this.assetDownloader = new AssetDownloader();
        this.libraryDownloader = new LibraryDownloader();
        this.parallelDownloader = new ParallelDownloader();
    }
    
    /**
     * Instala una versión Vanilla de Minecraft
     * @param versionId ID de la versión (ej: "1.20.4")
     * @param minecraftDir Directorio .minecraft de destino
     * @param callback Callback de progreso
     * @throws Exception Si hay error en la instalación
     */
    public void install(String versionId, Path minecraftDir, Consumer<String> callback) throws Exception {
        logger.info("Instalando Minecraft {}...", versionId);
        
        try {
            // Obtener información de la versión
            VersionManager versionManager = new VersionManager();
            VersionManager.VersionInfo versionInfo = versionManager.getVersionInfo(versionId);
            
            if (versionInfo == null) {
                throw new Exception("Versión no encontrada: " + versionId);
            }
            
            // Crear estructura de directorios
            if (callback != null) {
                callback.accept("Creando estructura de directorios...");
            }
            createDirectories(minecraftDir);
            
            // Descargar manifest de versión
            if (callback != null) {
                callback.accept("Descargando información de versión...");
            }
            String versionJson = downloadVersionJson(versionInfo.getUrl());
            JsonObject versionData = JsonParser.parseString(versionJson).getAsJsonObject();
            
            // Guardar JSON de versión
            Path versionsDir = minecraftDir.resolve("versions").resolve(versionId);
            FileUtil.createDirectory(versionsDir);
            Path versionJsonFile = versionsDir.resolve(versionId + ".json");
            FileUtil.writeStringToFile(versionJsonFile, versionJson);
            
            // Descargar cliente JAR
            if (callback != null) {
                callback.accept("Descargando cliente de Minecraft...");
            }
            downloadClientJar(versionData, versionsDir, versionId, callback);
            
            // Descargar librerías en paralelo
            if (callback != null) {
                callback.accept("Descargando librerías...");
            }
            libraryDownloader.downloadLibraries(versionInfo, minecraftDir.toString(), callback);
            
            // Descargar assets en paralelo
            if (callback != null) {
                callback.accept("Descargando assets...");
            }
            assetDownloader.downloadAssets(versionInfo, minecraftDir.toString(), callback);
            
            // Descargar natives si es necesario
            if (versionData.has("natives")) {
                if (callback != null) {
                    callback.accept("Descargando natives...");
                }
                downloadNatives(versionData, minecraftDir, callback);
            }
            
            logger.info("Instalación de Minecraft {} completada", versionId);
            if (callback != null) {
                callback.accept("Instalación completada");
            }
            
        } catch (Exception e) {
            logger.error("Error al instalar Minecraft {}", versionId, e);
            throw e;
        } finally {
            // Limpiar recursos
            assetDownloader.shutdown();
            libraryDownloader.shutdown();
            parallelDownloader.shutdown();
        }
    }
    
    /**
     * Crea la estructura de directorios necesaria
     */
    private void createDirectories(Path minecraftDir) throws IOException {
        FileUtil.createDirectory(minecraftDir);
        FileUtil.createDirectory(minecraftDir.resolve("versions"));
        FileUtil.createDirectory(minecraftDir.resolve("assets"));
        FileUtil.createDirectory(minecraftDir.resolve("libraries"));
        FileUtil.createDirectory(minecraftDir.resolve("natives"));
        FileUtil.createDirectory(minecraftDir.resolve("config"));
        FileUtil.createDirectory(minecraftDir.resolve("saves"));
        FileUtil.createDirectory(minecraftDir.resolve("resourcepacks"));
        FileUtil.createDirectory(minecraftDir.resolve("mods"));
        FileUtil.createDirectory(minecraftDir.resolve("shaderpacks"));
        FileUtil.createDirectory(minecraftDir.resolve("logs"));
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
     * Descarga el cliente JAR
     */
    private void downloadClientJar(JsonObject versionData, Path versionsDir, String versionId, 
                                   Consumer<String> callback) throws IOException, InterruptedException {
        JsonObject downloads = versionData.getAsJsonObject("downloads");
        JsonObject client = downloads.getAsJsonObject("client");
        String clientUrl = client.get("url").getAsString();
        String clientSha1 = client.get("sha1").getAsString();
        
        Path clientJar = versionsDir.resolve(versionId + ".jar");
        
        // Verificar si ya existe y tiene el hash correcto
        if (Files.exists(clientJar)) {
            if (FileUtil.verifyFileHash(clientJar, clientSha1)) {
                logger.info("Cliente JAR ya descargado y verificado");
                return;
            }
        }
        
        // Descargar cliente
        ParallelDownloader.DownloadTask task = new ParallelDownloader.DownloadTask(
                clientUrl, clientJar, clientSha1
        );
        
        parallelDownloader.downloadFiles(
                java.util.Collections.singletonList(task),
                (progress, completed, total) -> {
                    if (callback != null) {
                        callback.accept(String.format("Descargando cliente: %.0f%%", progress * 100));
                    }
                }
        );
    }
    
    /**
     * Descarga los natives necesarios
     */
    private void downloadNatives(JsonObject versionData, Path minecraftDir, Consumer<String> callback) {
        // Los natives se descargan junto con las librerías
        // Esta función puede ser expandida si se necesita lógica adicional
        logger.debug("Natives se descargan junto con las librerías");
    }
}
