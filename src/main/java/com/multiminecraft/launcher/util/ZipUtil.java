package com.multiminecraft.launcher.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utilidades para el manejo de archivos ZIP
 */
public class ZipUtil {

    private static final Logger logger = LoggerFactory.getLogger(ZipUtil.class);

    /**
     * Extrae un archivo ZIP en el directorio de destino
     * @param zipFilePath Ruta del archivo ZIP
     * @param destDirectory Directorio donde se extraerá
     * @throws IOException Si ocurre un error de E/S
     */
    public static void unzip(Path zipFilePath, Path destDirectory) throws IOException {
        logger.info("Extrayendo ZIP: {} en {}", zipFilePath, destDirectory);
        
        if (!Files.exists(destDirectory)) {
            Files.createDirectories(destDirectory);
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath.toFile()))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                Path newPath = destDirectory.resolve(zipEntry.getName()).normalize();
                
                // Seguridad: prevenir ataques de "Zip Slip"
                if (!newPath.startsWith(destDirectory)) {
                    throw new IOException("Entrada ZIP fuera del directorio de destino: " + zipEntry.getName());
                }

                if (zipEntry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    // Asegurar que el directorio padre existe
                    if (newPath.getParent() != null && !Files.exists(newPath.getParent())) {
                        Files.createDirectories(newPath.getParent());
                    }
                    
                    // Escribir archivo
                    try (OutputStream os = Files.newOutputStream(newPath)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
                zipEntry = zis.getNextEntry();
            }
            logger.info("Extracción finalizada exitosamente");
        }
    }
}
