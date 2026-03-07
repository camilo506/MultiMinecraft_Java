package com.multiminecraft.launcher.manager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.multiminecraft.launcher.util.FileUtil;
import com.multiminecraft.launcher.util.JsonUtil;
import com.multiminecraft.launcher.util.PlatformUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Gestor de configuración del launcher
 * Maneja config.json y settingsMM2.json según especificaciones
 */
public class ConfigManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static final String CONFIG_FILE = "config.json";
    private static final String SETTINGS_FILE = "settingsMM2.json";
    
    private final Path launcherDir;
    private final Path configFile;
    private final Path settingsFile;
    
    private LauncherConfig config;
    private UserSettings settings;
    
    public ConfigManager() {
        this.launcherDir = PlatformUtil.getLauncherDirectory();
        this.configFile = launcherDir.resolve(CONFIG_FILE);
        this.settingsFile = launcherDir.resolve(SETTINGS_FILE);
        
        loadConfig();
        loadSettings();
    }
    
    /**
     * Carga la configuración del launcher
     */
    private void loadConfig() {
        try {
            if (Files.exists(configFile)) {
                String json = FileUtil.readFileAsString(configFile);
                config = JsonUtil.fromJson(json, LauncherConfig.class);
                logger.info("Configuración cargada desde: {}", configFile);
            } else {
                // Crear configuración por defecto
                config = createDefaultConfig();
                saveConfig();
                logger.info("Configuración por defecto creada");
            }
        } catch (Exception e) {
            logger.error("Error al cargar configuración", e);
            config = createDefaultConfig();
        }
    }
    
    /**
     * Guarda la configuración del launcher
     */
    public void saveConfig() {
        try {
            String json = JsonUtil.toJson(config);
            FileUtil.writeStringToFile(configFile, json);
            logger.debug("Configuración guardada");
        } catch (Exception e) {
            logger.error("Error al guardar configuración", e);
        }
    }
    
    /**
     * Carga la configuración de usuario
     */
    private void loadSettings() {
        try {
            if (Files.exists(settingsFile)) {
                String json = FileUtil.readFileAsString(settingsFile);
                settings = JsonUtil.fromJson(json, UserSettings.class);
                logger.info("Configuración de usuario cargada");
            } else {
                // Crear configuración por defecto
                settings = createDefaultSettings();
                saveSettings();
                logger.info("Configuración de usuario por defecto creada");
            }
        } catch (Exception e) {
            logger.error("Error al cargar configuración de usuario", e);
            settings = createDefaultSettings();
        }
    }
    
    /**
     * Guarda la configuración de usuario
     */
    public void saveSettings() {
        try {
            String json = JsonUtil.toJson(settings);
            FileUtil.writeStringToFile(settingsFile, json);
            logger.debug("Configuración de usuario guardada");
        } catch (Exception e) {
            logger.error("Error al guardar configuración de usuario", e);
        }
    }
    
    /**
     * Crea configuración por defecto
     */
    private LauncherConfig createDefaultConfig() {
        LauncherConfig defaultConfig = new LauncherConfig();
        defaultConfig.setInstallPath(launcherDir.toString());
        defaultConfig.setInstalledVersion("1.0.0");
        defaultConfig.setInstallDate(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        defaultConfig.setDesktopShortcut(true);
        defaultConfig.setRamRecommended("4");
        defaultConfig.setRamOptimal("8");
        defaultConfig.setRamMaximum("16");
        return defaultConfig;
    }
    
    /**
     * Crea configuración de usuario por defecto
     */
    private UserSettings createDefaultSettings() {
        return new UserSettings();
    }
    
    /**
     * Obtiene la configuración del launcher
     */
    public LauncherConfig getConfig() {
        return config;
    }
    
    /**
     * Obtiene la configuración de usuario
     */
    public UserSettings getSettings() {
        return settings;
    }
    
    /**
     * Obtiene el directorio de una instancia
     */
    public Path getInstanceDirectory(String instanceName) {
        return launcherDir.resolve("Instancias").resolve(instanceName);
    }
    
    /**
     * Obtiene el directorio .minecraft de una instancia
     */
    public Path getInstanceMinecraftDirectory(String instanceName) {
        return getInstanceDirectory(instanceName).resolve(".minecraft");
    }
    
    /**
     * Clase para configuración del launcher (config.json)
     */
    public static class LauncherConfig {
        private String installPath;
        private String installedVersion;
        private String installDate;
        private boolean desktopShortcut;
        private String ramRecommended;
        private String ramOptimal;
        private String ramMaximum;
        
        public String getInstallPath() {
            return installPath;
        }
        
        public void setInstallPath(String installPath) {
            this.installPath = installPath;
        }
        
        public String getInstalledVersion() {
            return installedVersion;
        }
        
        public void setInstalledVersion(String installedVersion) {
            this.installedVersion = installedVersion;
        }
        
        public String getInstallDate() {
            return installDate;
        }
        
        public void setInstallDate(String installDate) {
            this.installDate = installDate;
        }
        
        public boolean isDesktopShortcut() {
            return desktopShortcut;
        }
        
        public void setDesktopShortcut(boolean desktopShortcut) {
            this.desktopShortcut = desktopShortcut;
        }
        
        public String getRamRecommended() {
            return ramRecommended;
        }
        
        public void setRamRecommended(String ramRecommended) {
            this.ramRecommended = ramRecommended;
        }
        
        public String getRamOptimal() {
            return ramOptimal;
        }
        
        public void setRamOptimal(String ramOptimal) {
            this.ramOptimal = ramOptimal;
        }
        
        public String getRamMaximum() {
            return ramMaximum;
        }
        
        public void setRamMaximum(String ramMaximum) {
            this.ramMaximum = ramMaximum;
        }
    }
    
    /**
     * Clase para configuración de usuario (settingsMM2.json)
     */
    public static class UserSettings {
        // Agregar campos según sea necesario
        // Por ahora, clase vacía que puede ser expandida
    }
}
