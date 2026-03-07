package com.multiminecraft.launcher.service;

import com.multiminecraft.launcher.model.Instance;
import com.multiminecraft.launcher.model.LauncherConfig;
import com.multiminecraft.launcher.util.FileUtil;
import com.multiminecraft.launcher.util.JsonUtil;
import com.multiminecraft.launcher.util.PlatformUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Servicio para gestionar la configuración del launcher y de las instancias
 */
public class ConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigService.class);
    private static final String LAUNCHER_CONFIG_FILE = "launcher.json";
    private static final String INSTANCE_CONFIG_FILE = "instance.json";

    private static ConfigService instance;
    private LauncherConfig launcherConfig;

    private ConfigService() {
        loadLauncherConfig();
    }

    /**
     * Obtiene la instancia singleton del servicio
     */
    public static synchronized ConfigService getInstance() {
        if (instance == null) {
            instance = new ConfigService();
        }
        return instance;
    }

    /**
     * Carga la configuración del launcher
     */
    private void loadLauncherConfig() {
        Path configFile = PlatformUtil.getLauncherDirectory().resolve(LAUNCHER_CONFIG_FILE);

        try {
            if (Files.exists(configFile)) {
                String json = FileUtil.readFileAsString(configFile);
                launcherConfig = JsonUtil.fromJson(json, LauncherConfig.class);
                logger.info("Configuración del launcher cargada desde: {}", configFile);
            } else {
                // Crear configuración por defecto
                launcherConfig = new LauncherConfig();
                saveLauncherConfig();
                logger.info("Configuración por defecto creada en: {}", configFile);
            }
        } catch (IOException e) {
            logger.error("Error al cargar configuración del launcher", e);
            launcherConfig = new LauncherConfig();
        }
    }

    /**
     * Guarda la configuración del launcher
     */
    public void saveLauncherConfig() {
        Path configFile = PlatformUtil.getLauncherDirectory().resolve(LAUNCHER_CONFIG_FILE);

        try {
            String json = JsonUtil.toJson(launcherConfig);
            FileUtil.writeStringToFile(configFile, json);
            logger.info("Configuración del launcher guardada");
        } catch (IOException e) {
            logger.error("Error al guardar configuración del launcher", e);
        }
    }

    /**
     * Obtiene la configuración del launcher
     */
    public LauncherConfig getLauncherConfig() {
        return launcherConfig;
    }

    /**
     * Carga la configuración de una instancia
     */
    public Instance loadInstanceConfig(String instanceName) throws IOException {
        Path instanceDir = getInstanceDirectory(instanceName);
        Path configFile = instanceDir.resolve(INSTANCE_CONFIG_FILE);

        if (!Files.exists(configFile)) {
            throw new IOException("No se encontró el archivo de configuración de la instancia: " + instanceName);
        }

        String json = FileUtil.readFileAsString(configFile);
        Instance instance = JsonUtil.fromJson(json, Instance.class);
        logger.debug("Configuración de instancia cargada: {}", instanceName);

        return instance;
    }

    /**
     * Guarda la configuración de una instancia
     */
    public void saveInstanceConfig(Instance instance) throws IOException {
        Path instanceDir = getInstanceDirectory(instance.getName());
        Path configFile = instanceDir.resolve(INSTANCE_CONFIG_FILE);

        String json = JsonUtil.toJson(instance);
        FileUtil.writeStringToFile(configFile, json);
        logger.info("Configuración de instancia guardada: {}", instance.getName());
    }

    /**
     * Obtiene el directorio de una instancia
     */
    public Path getInstanceDirectory(String instanceName) {
        return PlatformUtil.getInstancesDirectory().resolve(instanceName);
    }

    /**
     * Obtiene el directorio .minecraft de una instancia
     */
    public Path getInstanceMinecraftDirectory(String instanceName) {
        return getInstanceDirectory(instanceName);
    }

    /**
     * Obtiene la ruta de Java configurada (de la instancia o global)
     */
    public String getJavaPath(Instance instance) {
        // Si la instancia tiene un Java personalizado, usarlo
        if (instance.getJavaPath() != null && !instance.getJavaPath().isEmpty()) {
            return instance.getJavaPath();
        }

        // Auto-detectar Java adecuado según la versión de Minecraft
        // Minecraft 1.20.5+ requiere Java 21, 1.17+ requiere Java 17
        String minecraftVersion = instance.getVersion();
        if (minecraftVersion != null) {
            int required = PlatformUtil.getRequiredJavaVersion(minecraftVersion);
            int current = PlatformUtil.getCurrentJavaVersion();
            
            // Si el Java del sistema no cumple, buscar una instalación adecuada
            if (current < required) {
                String autoDetected = PlatformUtil.findJavaInstallation(required);
                if (autoDetected != null) {
                    logger.info("Auto-detectado Java {} para Minecraft {} (se requiere Java >= {})", 
                               autoDetected, minecraftVersion, required);
                    return autoDetected;
                }
                logger.warn("Minecraft {} requiere Java {} pero solo se encontró Java {}. " +
                           "El juego puede no iniciar correctamente.", minecraftVersion, required, current);
            }
        }

        if (launcherConfig.getDefaultJavaPath() != null && !launcherConfig.getDefaultJavaPath().isEmpty()) {
            return launcherConfig.getDefaultJavaPath();
        }

        return PlatformUtil.getSystemJavaPath();
    }

    /**
     * Obtiene la memoria configurada (de la instancia o global)
     */
    public String getMemory(Instance instance) {
        if (instance.getMemory() != null && !instance.getMemory().isEmpty()) {
            return instance.getMemory();
        }

        return launcherConfig.getDefaultMemory();
    }

    /**
     * Obtiene la ruta de Java por defecto
     */
    public String getDefaultJavaPath() {
        if (launcherConfig.getDefaultJavaPath() != null && !launcherConfig.getDefaultJavaPath().isEmpty()) {
            return launcherConfig.getDefaultJavaPath();
        }
        return PlatformUtil.getSystemJavaPath();
    }

    /**
     * Obtiene la ruta de Java adecuada para una versión específica de Minecraft.
     * Auto-detecta Java 21 si es necesario para versiones modernas.
     *
     * @param minecraftVersion Versión de Minecraft (ej: "1.21.11", "1.20.1")
     * @return Ruta al ejecutable de Java
     */
    public String getJavaPathForVersion(String minecraftVersion) {
        int required = PlatformUtil.getRequiredJavaVersion(minecraftVersion);
        int current = PlatformUtil.getCurrentJavaVersion();
        
        if (current < required) {
            String autoDetected = PlatformUtil.findJavaInstallation(required);
            if (autoDetected != null) {
                return autoDetected;
            }
        }
        return getDefaultJavaPath();
    }
}
