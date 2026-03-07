package com.multiminecraft.launcher.model;

import java.util.List;

/**
 * Modelo que representa la información de una versión de Minecraft
 */
public class MinecraftVersion {
    private String id;
    private String type; // release, snapshot, old_beta, old_alpha
    private String url;
    private String releaseTime;
    
    // Información adicional del JSON de versión
    private String mainClass;
    private String assetIndex;
    private List<Library> libraries;
    private String clientUrl;
    private String clientSha1;

    public MinecraftVersion() {
    }

    public MinecraftVersion(String id, String type, String url) {
        this.id = id;
        this.type = type;
        this.url = url;
    }

    // Getters y Setters
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getReleaseTime() {
        return releaseTime;
    }

    public void setReleaseTime(String releaseTime) {
        this.releaseTime = releaseTime;
    }

    public String getMainClass() {
        return mainClass;
    }

    public void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }

    public String getAssetIndex() {
        return assetIndex;
    }

    public void setAssetIndex(String assetIndex) {
        this.assetIndex = assetIndex;
    }

    public List<Library> getLibraries() {
        return libraries;
    }

    public void setLibraries(List<Library> libraries) {
        this.libraries = libraries;
    }

    public String getClientUrl() {
        return clientUrl;
    }

    public void setClientUrl(String clientUrl) {
        this.clientUrl = clientUrl;
    }

    public String getClientSha1() {
        return clientSha1;
    }

    public void setClientSha1(String clientSha1) {
        this.clientSha1 = clientSha1;
    }

    /**
     * Verifica si es una versión release
     */
    public boolean isRelease() {
        return "release".equalsIgnoreCase(type);
    }

    /**
     * Verifica si es un snapshot
     */
    public boolean isSnapshot() {
        return "snapshot".equalsIgnoreCase(type);
    }

    @Override
    public String toString() {
        return id + " (" + type + ")";
    }

    /**
     * Clase interna para representar una librería
     */
    public static class Library {
        private String name;
        private String url;
        private String sha1;
        private String path;

        public Library() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getSha1() {
            return sha1;
        }

        public void setSha1(String sha1) {
            this.sha1 = sha1;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }
}
