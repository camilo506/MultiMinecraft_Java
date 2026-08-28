package com.multiminecraft.launcher.installer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.multiminecraft.launcher.config.Config;
import com.multiminecraft.launcher.util.FileUtil;
import com.multiminecraft.launcher.util.PlatformUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Instalador de Forge
 * Maneja errores de checksum con reintentos (máximo 3) y limpieza de archivos temporales
 */
public class ForgeInstaller {
    
    private static final Logger logger = LoggerFactory.getLogger(ForgeInstaller.class);
    private static final String FORGE_VERSION_API = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    private static final String FORGE_MAVEN_BASE = "https://maven.minecraftforge.net/net/minecraftforge/forge/";
    
    /**
     * Instala Forge sobre una versión base de Minecraft
     * @param minecraftVersion Versión base de Minecraft
     * @param minecraftDir Directorio .minecraft
     * @param callback Callback de progreso
     * @throws Exception Si hay error en la instalación
     */
    public void install(String minecraftVersion, Path minecraftDir, Consumer<String> callback) throws Exception {
        logger.info("Instalando Forge para Minecraft {}...", minecraftVersion);
        
        // Detectar versión de Forge compatible
        String forgeVersion = detectCompatibleForgeVersion(minecraftVersion);
        if (forgeVersion == null) {
            throw new Exception("No se encontró una versión de Forge compatible para Minecraft " + minecraftVersion);
        }
        
        logger.info("Versión de Forge detectada: {}", forgeVersion);
        
        // Intentar instalación con reintentos
        int maxAttempts = Config.MAX_RETRY_ATTEMPTS;
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (attempt > 1) {
                    logger.info("Reintentando instalación de Forge (intento {}/{})...", attempt, maxAttempts);
                    if (callback != null) {
                        callback.accept(String.format("Reintentando instalación (intento %d/%d)...", attempt, maxAttempts));
                    }
                    
                    // Limpiar archivos temporales entre reintentos
                    cleanTemporaryFiles(minecraftDir);
                    
                    // Esperar un momento antes de reintentar
                    Thread.sleep(1000);
                }
                
                installForgeVersion(forgeVersion, minecraftVersion, minecraftDir, callback);
                
                logger.info("Forge instalado exitosamente");
                return;
                
            } catch (Exception e) {
                lastException = e;
                logger.warn("Error en intento {} de instalación de Forge: {}", attempt, e.getMessage());
                
                if (attempt < maxAttempts) {
                    // Limpiar archivos temporales antes del siguiente intento
                    cleanTemporaryFiles(minecraftDir);
                }
            }
        }
        
        // Si llegamos aquí, todos los intentos fallaron
        logger.error("Fallo al instalar Forge después de {} intentos", maxAttempts);
        throw new Exception("No se pudo instalar Forge después de " + maxAttempts + " intentos. " + 
                           (lastException != null ? lastException.getMessage() : ""), lastException);
    }
    
    /**
     * Detecta la versión de Forge compatible
     */
    private String detectCompatibleForgeVersion(String minecraftVersion) {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(Config.DOWNLOAD_TIMEOUT))
                    .build();
            
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(FORGE_VERSION_API))
                    .timeout(java.time.Duration.ofSeconds(Config.DOWNLOAD_TIMEOUT))
                    .GET()
                    .build();
            
            java.net.http.HttpResponse<String> response = client.send(
                    request, 
                    java.net.http.HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                logger.warn("Error al obtener versiones de Forge desde API");
                return null;
            }
            
            JsonObject promotions = JsonParser.parseString(response.body()).getAsJsonObject();
            
            // Buscar versión recomendada para esta versión de Minecraft
            String promotionKey = minecraftVersion + "-recommended";
            if (promotions.has(promotionKey)) {
                return promotions.getAsJsonObject(promotionKey).get("version").getAsString();
            }
            
            // Si no hay recomendada, buscar la última
            promotionKey = minecraftVersion + "-latest";
            if (promotions.has(promotionKey)) {
                return promotions.getAsJsonObject(promotionKey).get("version").getAsString();
            }
            
            return null;
            
        } catch (Exception e) {
            logger.error("Error al detectar versión de Forge", e);
            return null;
        }
    }
    
    /**
     * Instala una versión específica de Forge
     */
    private void installForgeVersion(String forgeVersion, String minecraftVersion, Path minecraftDir, 
                                    Consumer<String> callback) throws Exception {
        // Construir URL del instalador
        String installerUrl = FORGE_MAVEN_BASE + forgeVersion + "/forge-" + forgeVersion + "-installer.jar";
        
        // Crear directorio temporal
        Path tempDir = minecraftDir.resolve("temp");
        FileUtil.createDirectory(tempDir);
        
        // Descargar instalador
        if (callback != null) {
            callback.accept("Descargando instalador de Forge...");
        }
        Path installerPath = tempDir.resolve("forge-installer.jar");
        
        downloadInstaller(installerUrl, installerPath, callback);
        
        // Obtener ruta de Java
        String javaPath = getJavaPath();
        
        // Ejecutar instalador
        if (callback != null) {
            callback.accept("Ejecutando instalador de Forge...");
        }
        executeInstaller(javaPath, installerPath, minecraftDir);
        
        // Limpiar archivos temporales
        cleanTemporaryFiles(minecraftDir);
        
        if (callback != null) {
            callback.accept("Forge instalado correctamente");
        }
    }
    
    /**
     * Descarga el instalador de Forge
     */
    private void downloadInstaller(String url, Path destination, Consumer<String> callback) throws Exception {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(Config.DOWNLOAD_TIMEOUT))
                    .build();
            
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(Config.DOWNLOAD_TIMEOUT))
                    .GET()
                    .build();
            
            java.net.http.HttpResponse<java.io.InputStream> response = client.send(
                    request, 
                    java.net.http.HttpResponse.BodyHandlers.ofInputStream()
            );
            
            if (response.statusCode() != 200) {
                throw new IOException("Error HTTP " + response.statusCode() + " al descargar instalador");
            }
            
            try (java.io.InputStream in = response.body();
                 var out = Files.newOutputStream(destination)) {
                
                byte[] buffer = new byte[Config.CHUNK_SIZE];
                long totalBytesRead = 0;
                long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                int bytesRead;
                
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    
                    if (callback != null && contentLength > 0) {
                        double progress = (double) totalBytesRead / contentLength;
                        callback.accept(String.format("Descargando instalador: %.0f%%", progress * 100));
                    }
                }
            }
            
        } catch (Exception e) {
            // Intentar URL alternativa
            logger.warn("Error al descargar desde URL principal, intentando alternativa: {}", e.getMessage());
            String altUrl = "https://files.minecraftforge.net/maven/net/minecraftforge/forge/" + 
                           url.substring(url.lastIndexOf('/') + 1);
            downloadInstaller(altUrl, destination, callback);
        }
    }
    
    /**
     * Ejecuta el instalador de Forge
     */
    private void executeInstaller(String javaPath, Path installerPath, Path minecraftDir) throws Exception {
        // Asegurar que existe la estructura necesaria
        FileUtil.createDirectory(minecraftDir.resolve("versions"));
        
        // Crear launcher_profiles.json si no existe
        Path launcherProfilesPath = minecraftDir.resolve("launcher_profiles.json");
        if (!Files.exists(launcherProfilesPath)) {
            String launcherProfilesJson = "{\"profiles\":{},\"settings\":{}}";
            FileUtil.writeStringToFile(launcherProfilesPath, launcherProfilesJson);
        }
        
        // Ejecutar instalador
        ProcessBuilder processBuilder = new ProcessBuilder(
                javaPath,
                "-jar",
                installerPath.toString(),
                "--installClient",
                "--target",
                minecraftDir.toString()
        );
        
        processBuilder.directory(minecraftDir.toFile());
        processBuilder.redirectErrorStream(true);
        
        Process process = processBuilder.start();
        
        // Capturar salida
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.debug("Forge installer: {}", line);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception("El instalador de Forge terminó con código de error: " + exitCode);
        }
    }
    
    /**
     * Obtiene la ruta de Java adecuada
     */
    private String getJavaPath() {
        // Usar ConfigService para encontrar el mejor Java disponible
        return com.multiminecraft.launcher.service.ConfigService.getInstance().getDefaultJavaPath();
    }
    
    /**
     * Limpia archivos temporales
     */
    private void cleanTemporaryFiles(Path minecraftDir) {
        try {
            Path tempDir = minecraftDir.resolve("temp");
            if (Files.exists(tempDir)) {
                FileUtil.deleteDirectory(tempDir);
                logger.debug("Archivos temporales limpiados");
            }
        } catch (Exception e) {
            logger.warn("Error al limpiar archivos temporales", e);
        }
    }
}
