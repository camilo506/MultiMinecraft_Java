package com.multiminecraft.launcher.config;

/**
 * Configuración de optimización del launcher
 * Contiene todas las constantes de configuración según las especificaciones
 */
public class Config {
    // Optimización de descargas
    public static final int MAX_WORKERS = 4;
    public static final int CHUNK_SIZE = 131072; // 128KB — chunks más grandes = menos syscalls
    public static final int BUFFER_SIZE = 2 * 1024 * 1024; // 2MB
    public static final int MAX_CONCURRENT_DOWNLOADS = 20; // Más hilos para archivos pequeños (librerías, assets)
    public static final int MAX_CONCURRENT_INSTALLS = 2;
    public static final int LIBRARY_DOWNLOAD_THREADS = 16; // Hilos específicos para librerías
    
    // Cache
    public static final int CACHE_DURATION = 5; // segundos
    public static final int VERSION_CACHE_DURATION = 24 * 3600; // 24 horas en segundos
    
    // Timeouts
    public static final int DOWNLOAD_TIMEOUT = 30; // segundos
    public static final int INSTALL_TIMEOUT = 300; // segundos
    public static final int STARTUP_TIMEOUT = 60; // segundos
    
    // Ventana
    public static final int WINDOW_WIDTH = 900;
    public static final int WINDOW_HEIGHT = 600;
    
    // URLs de API
    public static final String VERSION_MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    
    // Reintentos
    public static final int MAX_RETRY_ATTEMPTS = 3;
    
    // Limpieza de logs
    public static final int LOG_RETENTION_DAYS = 7;
}
