package com.multiminecraft.launcher.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.net.URL;

/**
 * Servicio para gestionar las actualizaciones del launcher, modpacks y mods.
 */
public class UpdateService {

    private static final Logger logger = LoggerFactory.getLogger(UpdateService.class);
    private static final String VERSIONS_URL = "https://raw.githubusercontent.com/camilo506/Launcher_Configuracion/main/versiones.json";
    
    // Versión actual del launcher (hardcoded para esta compilación)
    public static final String LAUNCHER_VERSION = "1.0.0";

    private static UpdateService instance;

    private UpdateService() {}

    public static UpdateService getInstance() {
        if (instance == null) {
            instance = new UpdateService();
        }
        return instance;
    }

    /**
     * Resultado de una comprobación de actualizaciones.
     */
    public static class UpdateCheckResult {
        public boolean launcherUpdate;
        public boolean modpackUpdate;
        public boolean modsUpdate;
        public String remoteLauncherVersion;
        public String remoteModpackVersion;
        public String remoteModsVersion;
        public String changelog;
    }

    /**
     * Comprueba si hay actualizaciones disponibles consultando el repositorio de GitHub.
     */
    public UpdateCheckResult checkForUpdates(String installedModpackVersion, String installedModsVersion) {
        UpdateCheckResult result = new UpdateCheckResult();
        try {
            logger.info("Consultando versiones remotas desde: {}", VERSIONS_URL);
            URL url = new URL(VERSIONS_URL);
            try (InputStreamReader reader = new InputStreamReader(url.openStream())) {
                JsonObject json = new Gson().fromJson(reader, JsonObject.class);

                result.remoteLauncherVersion = json.get("launcher_version").getAsString();
                result.remoteModpackVersion = json.get("modpack_version").getAsString();
                result.remoteModsVersion = json.get("mods_version").getAsString();
                
                result.launcherUpdate = isNewer(LAUNCHER_VERSION, result.remoteLauncherVersion);
                result.modpackUpdate = isNewer(installedModpackVersion, result.remoteModpackVersion);
                result.modsUpdate = isNewer(installedModsVersion, result.remoteModsVersion);

                if (json.has("changelog")) {
                    result.changelog = json.get("changelog").toString();
                }

                logger.info("Comprobación finalizada. Launcher: {}, Modpack: {}, Mods: {}", 
                        result.launcherUpdate, result.modpackUpdate, result.modsUpdate);
            }
        } catch (Exception e) {
            logger.error("Error al comprobar actualizaciones", e);
        }
        return result;
    }

    private boolean isNewer(String current, String remote) {
        if (current == null || remote == null) return false;
        String c = current.trim();
        String r = remote.trim();
        if (c.isEmpty() || r.isEmpty()) return false;
        
        // Caso especial para nuevos usuarios (0.0.0)
        if (c.equals("0.0.0")) {
            // Solo avisamos si en el servidor hay algo distinto a la versión inicial 1.0.0
            return !r.equals("1.0.0");
        }
        
        return !c.equals(r);
    }
}
