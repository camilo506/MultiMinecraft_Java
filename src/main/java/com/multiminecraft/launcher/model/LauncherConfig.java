package com.multiminecraft.launcher.model;

/**
 * Modelo de configuración global del launcher
 */
public class LauncherConfig {
    private String defaultJavaPath;
    private String defaultMemory;
    private String theme;
    private String language;
    private String instancesPath;
    private String playerName;
    private String installedModpackVersion;
    private String installedModsVersion = "0.0.0";
    private String lastAccessKey = "";
    private String skinPath = "";

    public LauncherConfig() {
        // Valores por defecto
        this.defaultJavaPath = "";
        this.defaultMemory = "2G";
        this.theme = "dark";
        this.language = "es";
        this.instancesPath = "Instancias";
        this.playerName = "";
        this.installedModpackVersion = "0.0.0";
        this.installedModsVersion = "0.0.0";
        this.skinPath = "";
    }

    // Getters y Setters
    public String getDefaultJavaPath() {
        return defaultJavaPath;
    }

    public void setDefaultJavaPath(String defaultJavaPath) {
        this.defaultJavaPath = defaultJavaPath;
    }

    public String getDefaultMemory() {
        return defaultMemory;
    }

    public void setDefaultMemory(String defaultMemory) {
        this.defaultMemory = defaultMemory;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getInstancesPath() {
        return instancesPath;
    }

    public void setInstancesPath(String instancesPath) {
        this.instancesPath = instancesPath;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public String getInstalledModpackVersion() {
        return installedModpackVersion;
    }

    public void setInstalledModpackVersion(String installedModpackVersion) {
        this.installedModpackVersion = installedModpackVersion;
    }

    public String getInstalledModsVersion() {
        return installedModsVersion;
    }

    public void setInstalledModsVersion(String installedModsVersion) {
        this.installedModsVersion = installedModsVersion;
    }

    public String getLastAccessKey() {
        return lastAccessKey;
    }

    public void setLastAccessKey(String lastAccessKey) {
        this.lastAccessKey = lastAccessKey;
    }

    public String getSkinPath() {
        return skinPath != null ? skinPath : "";
    }

    public void setSkinPath(String skinPath) {
        this.skinPath = skinPath;
    }
}
