package com.multiminecraft.launcher.model;

/**
 * Enum que representa los tipos de loaders disponibles
 */
public enum LoaderType {
    VANILLA("Vanilla"),
    FORGE("Forge"),
    FABRIC("Fabric");

    private final String displayName;

    LoaderType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
