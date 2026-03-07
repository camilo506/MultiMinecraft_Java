package com.multiminecraft.launcher.installer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.multiminecraft.launcher.config.Config;
import com.multiminecraft.launcher.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Instalador de Fabric
 * Obtiene la versión más reciente de Fabric y crea el perfil de versión
 */
public class FabricInstaller {
    
    private static final Logger logger = LoggerFactory.getLogger(FabricInstaller.class);
    private static final String FABRIC_LOADER_API = "https://meta.fabricmc.net/v2/versions/loader";
    private static final String FABRIC_INSTALLER_URL = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/";
    
    /**
     * Instala Fabric Loader para una versión de Minecraft
     * @param minecraftVersion Versión de Minecraft
     * @param minecraftDir Directorio .minecraft
     * @param callback Callback de progreso
     * @throws Exception Si hay error en la instalación
     */
    public void install(String minecraftVersion, Path minecraftDir, Consumer<String> callback) throws Exception {
        logger.info("Instalando Fabric para Minecraft {}...", minecraftVersion);
        
        // Obtener versión de Fabric más reciente
        String fabricLoaderVersion = getLatestFabricLoaderVersion();
        if (fabricLoaderVersion == null) {
            throw new Exception("No se pudo obtener la versión de Fabric Loader");
        }
        
        logger.info("Versión de Fabric Loader: {}", fabricLoaderVersion);
        
        if (callback != null) {
            callback.accept("Instalando Fabric Loader...");
        }
        
        // Crear perfil de versión con Fabric
        createFabricVersionProfile(minecraftVersion, fabricLoaderVersion, minecraftDir);
        
        logger.info("Fabric instalado correctamente");
        if (callback != null) {
            callback.accept("Fabric instalado correctamente");
        }
    }
    
    /**
     * Obtiene la versión más reciente de Fabric Loader
     */
    private String getLatestFabricLoaderVersion() {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(Config.DOWNLOAD_TIMEOUT))
                    .build();
            
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(FABRIC_LOADER_API))
                    .timeout(java.time.Duration.ofSeconds(Config.DOWNLOAD_TIMEOUT))
                    .GET()
                    .build();
            
            java.net.http.HttpResponse<String> response = client.send(
                    request, 
                    java.net.http.HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                logger.warn("Error al obtener versiones de Fabric desde API");
                return null;
            }
            
            JsonArray loaders = JsonParser.parseString(response.body()).getAsJsonArray();
            
            // Obtener la primera versión (más reciente)
            if (loaders.size() > 0) {
                JsonObject loader = loaders.get(0).getAsJsonObject();
                return loader.get("version").getAsString();
            }
            
            return null;
            
        } catch (Exception e) {
            logger.error("Error al obtener versión de Fabric Loader", e);
            return null;
        }
    }
    
    /**
     * Crea el perfil de versión con Fabric
     */
    private void createFabricVersionProfile(String minecraftVersion, String fabricLoaderVersion, 
                                           Path minecraftDir) throws Exception {
        // Obtener información de la versión base
        String versionId = minecraftVersion + "-fabric-" + fabricLoaderVersion;
        Path versionsDir = minecraftDir.resolve("versions").resolve(versionId);
        FileUtil.createDirectory(versionsDir);
        
        // Descargar JSON de versión de Fabric
        String fabricVersionJson = downloadFabricVersionJson(minecraftVersion, fabricLoaderVersion);
        
        // Guardar JSON de versión
        Path versionJsonFile = versionsDir.resolve(versionId + ".json");
        FileUtil.writeStringToFile(versionJsonFile, fabricVersionJson);
        
        // Descargar cliente JAR de Fabric si es necesario
        // (Fabric usa el mismo cliente que la versión base, pero puede tener modificaciones)
        logger.debug("Perfil de versión Fabric creado: {}", versionId);
    }
    
    /**
     * Descarga el JSON de versión de Fabric
     */
    private String downloadFabricVersionJson(String minecraftVersion, String fabricLoaderVersion) throws Exception {
        // Construir URL del JSON de versión de Fabric
        String versionUrl = String.format(
                "https://meta.fabricmc.net/v2/versions/loader/%s/%s/profile/json",
                minecraftVersion,
                fabricLoaderVersion
        );
        
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
            throw new IOException("Error HTTP " + response.statusCode() + " al descargar versión de Fabric");
        }
        
        return response.body();
    }
}
