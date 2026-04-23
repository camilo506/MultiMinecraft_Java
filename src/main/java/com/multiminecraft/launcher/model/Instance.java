package com.multiminecraft.launcher.model;

import java.time.LocalDateTime;

/**
 * Modelo que representa una instancia de Minecraft
 */
public class Instance {
    private String name;
    private String version;
    private LoaderType loader;
    private String loaderVersion;
    private String javaPath;
    private String memory;
    private String icon;
    private String playerName;
    private LocalDateTime lastPlayed;
    /** Fecha/hora en que se creó la instancia en el launcher (persistida en instance.json) */
    private LocalDateTime createdAt;
    private long totalPlaytime; // en segundos
    private boolean isSpecial; // Marcador para instancias automáticas de la vista Server

    public Instance() {
        this.loader = LoaderType.VANILLA;
        this.memory = "2G";
        this.icon = "default-instance-icon.png";
        this.playerName = "Player";
        this.totalPlaytime = 0;
    }

    public Instance(String name, String version, LoaderType loader) {
        this();
        this.name = name;
        this.version = version;
        this.loader = loader;
    }

    // Getters y Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public LoaderType getLoader() {
        return loader;
    }

    public void setLoader(LoaderType loader) {
        this.loader = loader;
    }

    public String getLoaderVersion() {
        return loaderVersion;
    }

    public void setLoaderVersion(String loaderVersion) {
        this.loaderVersion = loaderVersion;
    }

    public String getJavaPath() {
        return javaPath;
    }

    public void setJavaPath(String javaPath) {
        this.javaPath = javaPath;
    }

    public String getMemory() {
        return memory;
    }

    public void setMemory(String memory) {
        this.memory = memory;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public LocalDateTime getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(LocalDateTime lastPlayed) {
        this.lastPlayed = lastPlayed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public long getTotalPlaytime() {
        return totalPlaytime;
    }

    public void setTotalPlaytime(long totalPlaytime) {
        this.totalPlaytime = totalPlaytime;
    }

    public boolean isSpecial() {
        return isSpecial;
    }

    public void setSpecial(boolean special) {
        isSpecial = special;
    }

    /**
     * Retorna una descripción legible de la instancia
     */
    public String getDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append("Minecraft ").append(version);
        if (loader != LoaderType.VANILLA) {
            desc.append(" ").append(loader.getDisplayName());
            if (loaderVersion != null && !loaderVersion.isEmpty()) {
                desc.append(" ").append(loaderVersion);
            }
        }
        return desc.toString();
    }

    @Override
    public String toString() {
        return name + " (" + getDescription() + ")";
    }
}
