package com.multiminecraft.launcher.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.multiminecraft.launcher.config.Config;
import com.multiminecraft.launcher.model.MinecraftVersion;
import com.multiminecraft.launcher.util.FileUtil;
import com.multiminecraft.launcher.util.JsonUtil;
import com.multiminecraft.launcher.util.PlatformUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Gestor de versiones de Minecraft con cache inteligente
 * Implementa cache de 24 horas, fallback y versiones hardcodeadas
 */
public class VersionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(VersionManager.class);
    private static final String CACHE_FILE = "versions_cache.json";
    private static final String CACHE_INFO_FILE = "versions_cache_info.json";
    
    private final Path cacheDir;
    private final Path cacheFile;
    private final Path cacheInfoFile;
    
    public VersionManager() {
        this.cacheDir = PlatformUtil.getLauncherDirectory().resolve("cache");
        this.cacheFile = cacheDir.resolve(CACHE_FILE);
        this.cacheInfoFile = cacheDir.resolve(CACHE_INFO_FILE);
        
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            logger.error("Error al crear directorio de cache", e);
        }
    }
    
    /**
     * Obtiene todas las versiones disponibles, organizadas por tipo
     * @param forceUpdate Si es true, fuerza la actualización del cache
     * @return Mapa con versiones organizadas por tipo
     */
    public Map<String, List<VersionInfo>> getAllVersions(boolean forceUpdate) {
        Map<String, List<VersionInfo>> versionsByType = new HashMap<>();
        
        try {
            // Verificar cache
            CacheInfo cacheInfo = getCacheInfo();
            boolean shouldUpdate = forceUpdate || 
                                 cacheInfo == null || 
                                 isCacheExpired(cacheInfo);
            
            List<VersionInfo> allVersions;
            
            if (shouldUpdate) {
                logger.info("Actualizando cache de versiones...");
                allVersions = fetchVersionsFromAPI();
                
                if (allVersions != null && !allVersions.isEmpty()) {
                    saveCache(allVersions);
                } else {
                    // Si falla la API, intentar usar cache aunque esté expirado
                    logger.warn("Fallo al obtener versiones de la API, intentando usar cache...");
                    allVersions = loadFromCache();
                    
                    if (allVersions == null || allVersions.isEmpty()) {
                        // Si no hay cache, usar versiones de fallback
                        logger.warn("No hay cache disponible, usando versiones de fallback");
                        allVersions = getFallbackVersions();
                    }
                }
            } else {
                logger.info("Usando versiones desde cache");
                allVersions = loadFromCache();
                
                if (allVersions == null || allVersions.isEmpty()) {
                    allVersions = getFallbackVersions();
                }
            }
            
            // Organizar por tipo
            versionsByType = allVersions.stream()
                    .collect(Collectors.groupingBy(VersionInfo::getType));
            
            logger.info("Versiones cargadas: {} totales", allVersions.size());
            for (Map.Entry<String, List<VersionInfo>> entry : versionsByType.entrySet()) {
                logger.debug("  {}: {} versiones", entry.getKey(), entry.getValue().size());
            }
            
        } catch (Exception e) {
            logger.error("Error al obtener versiones", e);
            // En caso de error, usar fallback
            List<VersionInfo> fallback = getFallbackVersions();
            versionsByType = fallback.stream()
                    .collect(Collectors.groupingBy(VersionInfo::getType));
        }
        
        return versionsByType;
    }
    
    /**
     * Obtiene versiones de un tipo específico
     */
    public List<String> getVersionsByType(String type) {
        Map<String, List<VersionInfo>> allVersions = getAllVersions(false);
        List<VersionInfo> versions = allVersions.getOrDefault(type, Collections.emptyList());
        return versions.stream()
                .map(VersionInfo::getId)
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());
    }
    
    /**
     * Obtiene la versión más reciente de un tipo
     */
    public String getLatestVersion(String type) {
        List<String> versions = getVersionsByType(type);
        return versions.isEmpty() ? null : versions.get(0);
    }
    
    /**
     * Busca versiones que coincidan con una consulta
     */
    public List<VersionInfo> searchVersions(String query) {
        Map<String, List<VersionInfo>> allVersions = getAllVersions(false);
        String lowerQuery = query.toLowerCase();
        
        return allVersions.values().stream()
                .flatMap(List::stream)
                .filter(v -> v.getId().toLowerCase().contains(lowerQuery))
                .sorted((v1, v2) -> v2.getId().compareTo(v1.getId()))
                .collect(Collectors.toList());
    }
    
    /**
     * Obtiene información detallada de una versión
     */
    public VersionInfo getVersionInfo(String versionId) {
        Map<String, List<VersionInfo>> allVersions = getAllVersions(false);
        
        return allVersions.values().stream()
                .flatMap(List::stream)
                .filter(v -> v.getId().equals(versionId))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Actualiza el cache de versiones
     */
    public boolean updateCache() {
        try {
            List<VersionInfo> versions = fetchVersionsFromAPI();
            if (versions != null && !versions.isEmpty()) {
                saveCache(versions);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error al actualizar cache", e);
            return false;
        }
    }
    
    /**
     * Obtiene información del cache
     */
    public CacheInfo getCacheInfo() {
        try {
            if (Files.exists(cacheInfoFile)) {
                String json = FileUtil.readFileAsString(cacheInfoFile);
                return JsonUtil.fromJson(json, CacheInfo.class);
            }
        } catch (Exception e) {
            logger.warn("Error al leer información del cache", e);
        }
        return null;
    }
    
    /**
     * Obtiene versiones desde la API de Mojang
     */
    private List<VersionInfo> fetchVersionsFromAPI() {
        try {
            logger.info("Obteniendo versiones desde API de Mojang...");
            
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(Config.DOWNLOAD_TIMEOUT))
                    .build();
            
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(Config.VERSION_MANIFEST_URL))
                    .timeout(java.time.Duration.ofSeconds(Config.DOWNLOAD_TIMEOUT))
                    .GET()
                    .build();
            
            java.net.http.HttpResponse<String> response = client.send(
                    request, 
                    java.net.http.HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                logger.error("Error HTTP {} al obtener versiones", response.statusCode());
                return null;
            }
            
            String json = response.body();
            JsonObject manifest = JsonParser.parseString(json).getAsJsonObject();
            JsonArray versions = manifest.getAsJsonArray("versions");
            
            List<VersionInfo> versionList = new ArrayList<>();
            
            for (JsonElement versionElement : versions) {
                JsonObject versionObj = versionElement.getAsJsonObject();
                
                String id = versionObj.get("id").getAsString();
                String type = versionObj.get("type").getAsString();
                String url = versionObj.get("url").getAsString();
                String releaseTime = versionObj.get("releaseTime").getAsString();
                
                VersionInfo versionInfo = new VersionInfo();
                versionInfo.setId(id);
                versionInfo.setType(type);
                versionInfo.setUrl(url);
                versionInfo.setReleaseTime(releaseTime);
                
                versionList.add(versionInfo);
            }
            
            logger.info("Se obtuvieron {} versiones desde la API", versionList.size());
            return versionList;
            
        } catch (Exception e) {
            logger.error("Error al obtener versiones desde la API", e);
            return null;
        }
    }
    
    /**
     * Guarda versiones en cache
     */
    private void saveCache(List<VersionInfo> versions) {
        try {
            String json = JsonUtil.toJson(versions);
            FileUtil.writeStringToFile(cacheFile, json);
            
            CacheInfo cacheInfo = new CacheInfo();
            cacheInfo.setTimestamp(System.currentTimeMillis());
            cacheInfo.setVersionCount(versions.size());
            
            String cacheInfoJson = JsonUtil.toJson(cacheInfo);
            FileUtil.writeStringToFile(cacheInfoFile, cacheInfoJson);
            
            logger.info("Cache de versiones guardado: {} versiones", versions.size());
        } catch (Exception e) {
            logger.error("Error al guardar cache", e);
        }
    }
    
    /**
     * Carga versiones desde cache
     */
    private List<VersionInfo> loadFromCache() {
        try {
            if (!Files.exists(cacheFile)) {
                return null;
            }
            
            String json = FileUtil.readFileAsString(cacheFile);
            List<VersionInfo> versions = JsonUtil.fromJsonList(json, VersionInfo.class);
            
            logger.info("Versiones cargadas desde cache: {}", versions.size());
            return versions;
            
        } catch (Exception e) {
            logger.error("Error al cargar cache", e);
            return null;
        }
    }
    
    /**
     * Verifica si el cache ha expirado
     */
    private boolean isCacheExpired(CacheInfo cacheInfo) {
        long now = System.currentTimeMillis();
        long cacheAge = now - cacheInfo.getTimestamp();
        return cacheAge > (Config.VERSION_CACHE_DURATION * 1000L);
    }
    
    /**
     * Obtiene versiones de fallback hardcodeadas
     */
    private List<VersionInfo> getFallbackVersions() {
        logger.info("Usando versiones de fallback hardcodeadas");
        
        List<VersionInfo> fallback = new ArrayList<>();
        
        // Versiones release recientes
        String[] releaseVersions = {
            "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20",
            "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
            "1.18.2", "1.18.1", "1.18",
            "1.17.1", "1.17",
            "1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1", "1.16"
        };
        
        for (String version : releaseVersions) {
            VersionInfo info = new VersionInfo();
            info.setId(version);
            info.setType("release");
            info.setReleaseTime("2020-01-01T00:00:00+00:00");
            info.setUrl("https://piston-meta.mojang.com/v1/packages/..."); // URL genérica
            fallback.add(info);
        }
        
        // Algunos snapshots
        String[] snapshotVersions = {
            "24w03a", "24w02a", "23w51a", "23w50a"
        };
        
        for (String version : snapshotVersions) {
            VersionInfo info = new VersionInfo();
            info.setId(version);
            info.setType("snapshot");
            info.setReleaseTime("2024-01-01T00:00:00+00:00");
            info.setUrl("https://piston-meta.mojang.com/v1/packages/...");
            fallback.add(info);
        }
        
        return fallback;
    }
    
    /**
     * Clase para representar información de versión
     */
    public static class VersionInfo {
        private String id;
        private String type; // "release", "snapshot", "old_beta", "old_alpha"
        private String releaseTime;
        private String url;
        
        public String getId() {
            return id;
        }
        
        public void setId(String id) {
            this.id = id;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public String getReleaseTime() {
            return releaseTime;
        }
        
        public void setReleaseTime(String releaseTime) {
            this.releaseTime = releaseTime;
        }
        
        public String getUrl() {
            return url;
        }
        
        public void setUrl(String url) {
            this.url = url;
        }
    }
    
    /**
     * Clase para información del cache
     */
    public static class CacheInfo {
        private long timestamp;
        private int versionCount;
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
        
        public int getVersionCount() {
            return versionCount;
        }
        
        public void setVersionCount(int versionCount) {
            this.versionCount = versionCount;
        }
    }
}
