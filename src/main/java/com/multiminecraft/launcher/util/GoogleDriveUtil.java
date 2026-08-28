package com.multiminecraft.launcher.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilidad para manejar enlaces de Google Drive
 */
public class GoogleDriveUtil {

    private static final String DIRECT_DOWNLOAD_URL = "https://drive.google.com/uc?export=download&id=";
    
    // Patrones comunes de Drive
    private static final Pattern ID_PATTERN_1 = Pattern.compile("/d/([^/?#]+)");
    private static final Pattern ID_PATTERN_2 = Pattern.compile("id=([^&?#]+)");
    private static final Pattern ID_PATTERN_FOLDER = Pattern.compile("/folders/([^/?#]+)");

    /**
     * Intenta extraer el ID de un enlace de Google Drive y convertirlo en enlace de descarga directa.
     * Si no reconoce el formato, devuelve el enlace original.
     */
    public static String getDirectDownloadUrl(String url) {
        if (url == null || url.isEmpty()) return url;
        
        // Si ya es un enlace de descarga directa o no es de Drive, no hacemos nada
        if (url.contains("uc?export=download") || !url.contains("drive.google.com")) {
            return url;
        }

        String fileId = extractId(url);
        if (fileId != null) {
            return DIRECT_DOWNLOAD_URL + fileId;
        }

        return url;
    }

    private static String extractId(String url) {
        Matcher m1 = ID_PATTERN_1.matcher(url);
        if (m1.find()) {
            return m1.group(1);
        }

        Matcher m2 = ID_PATTERN_2.matcher(url);
        if (m2.find()) {
            return m2.group(1);
        }

        Matcher mFolder = ID_PATTERN_FOLDER.matcher(url);
        if (mFolder.find()) {
            return mFolder.group(1);
        }

        return null;
    }
}
