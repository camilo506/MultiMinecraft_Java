package com.multiminecraft.launcher.downloader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.multiminecraft.launcher.config.Config;
import com.multiminecraft.launcher.manager.VersionManager;
import com.multiminecraft.launcher.util.FileUtil;
import com.multiminecraft.launcher.util.PlatformUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Descargador de librerías de Minecraft
 * Descarga todas las librerías necesarias en paralelo
 */
public class LibraryDownloader {
    
    private static final Logger logger = LoggerFactory.getLogger(LibraryDownloader.class);
    private static final String MAVEN_CENTRAL_URL = "https://repo1.maven.org/maven2/";
    private static final String FORGE_MAVEN_URL = "https://maven.minecraftforge.net/";
    
    private final ParallelDownloader downloader;
    
    public LibraryDownloader() {
        this.downloader = new ParallelDownloader();
    }
    
    /**
     * Descarga todas las librerías necesarias para una versión
     * @param versionInfo Información de la versión
     * @param destination Directorio de destino para librerías
     * @param callback Callback de progreso
     * @return true si la descarga fue exitosa
     */
    public boolean downloadLibraries(VersionManager.VersionInfo versionInfo, String destination, 
                                     Consumer<String> callback) {
        try {
            if (callback != null) {
                callback.accept("Preparando descarga de librerías...");
            }
            
            // Obtener el JSON de la versión
            String versionJson = downloadVersionJson(versionInfo.getUrl());
            JsonObject versionData = JsonParser.parseString(versionJson).getAsJsonObject();
            
            if (!versionData.has("libraries")) {
                logger.warn("La versión no tiene librerías");
                return true; // No es un error, algunas versiones no tienen librerías
            }
            
            JsonArray libraries = versionData.getAsJsonArray("libraries");
            
            // Usar directorio compartido de librerías
            Path librariesDir = PlatformUtil.getSharedLibrariesDirectory();
            FileUtil.createDirectory(librariesDir);
            
            // Preparar lista de tareas de descarga
            List<ParallelDownloader.DownloadTask> downloadTasks = new ArrayList<>();
            
            for (JsonElement libElement : libraries) {
                JsonObject library = libElement.getAsJsonObject();
                
                // Verificar reglas de compatibilidad
                if (library.has("rules") && !checkRules(library.getAsJsonArray("rules"))) {
                    continue;
                }
                
                // Obtener información de descarga
                String path = null;
                String url = null;
                String sha1 = null;
                
                if (library.has("downloads") && library.getAsJsonObject("downloads").has("artifact")) {
                    // Formato estándar de Mojang
                    JsonObject artifact = library.getAsJsonObject("downloads").getAsJsonObject("artifact");
                    path = artifact.get("path").getAsString();
                    url = artifact.get("url").getAsString();
                    sha1 = artifact.has("sha1") ? artifact.get("sha1").getAsString() : null;
                } else if (library.has("name")) {
                    // Formato Maven (común en Forge)
                    String name = library.get("name").getAsString();
                    path = getPathFromMavenCoordinates(name);
                    
                    String baseUrl = MAVEN_CENTRAL_URL;
                    if (library.has("url")) {
                        baseUrl = library.get("url").getAsString();
                    } else if (name.startsWith("net.minecraftforge")) {
                        baseUrl = FORGE_MAVEN_URL;
                    }
                    
                    url = baseUrl + path;
                } else {
                    continue;
                }
                
                Path libraryFile = librariesDir.resolve(path);
                
                // Verificar si ya existe y tiene el hash correcto
                boolean needsDownload = true;
                if (Files.exists(libraryFile)) {
                    if (sha1 != null) {
                        needsDownload = !FileUtil.verifyFileHash(libraryFile, sha1);
                    } else {
                        needsDownload = false; // Si no hay hash, asumimos que está bien si existe
                    }
                }
                
                if (needsDownload) {
                    downloadTasks.add(new ParallelDownloader.DownloadTask(url, libraryFile, sha1));
                }
            }
            
            if (downloadTasks.isEmpty()) {
                logger.info("Todas las librerías ya están descargadas");
                if (callback != null) {
                    callback.accept("Todas las librerías ya están descargadas");
                }
                return true;
            }
            
            if (callback != null) {
                callback.accept(String.format("Descargando %d librerías en paralelo...", downloadTasks.size()));
            }
            logger.info("Descargando {} librerías en paralelo...", downloadTasks.size());
            
            // Descargar en paralelo
            downloader.downloadFiles(downloadTasks, (progress, completed, total) -> {
                if (callback != null) {
                    callback.accept(String.format("Descargando librerías: %d/%d", completed, total));
                }
            });
            
            logger.info("Librerías descargadas: {}", downloadTasks.size());
            if (callback != null) {
                callback.accept("Librerías descargadas correctamente");
            }
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error al descargar librerías", e);
            if (callback != null) {
                callback.accept("Error al descargar librerías: " + e.getMessage());
            }
            return false;
        }
    }
    
    /**
     * Convierte coordenadas Maven a ruta de archivo
     */
    private String getPathFromMavenCoordinates(String name) {
        String[] parts = name.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Coordenadas Maven inválidas: " + name);
        }
        
        String domain = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        
        return domain + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
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
     * Cierra el descargador
     */
    public void shutdown() {
        downloader.shutdown();
    }
}
