package com.multiminecraft.launcher.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Utilidades para operaciones con archivos
 */
public class FileUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);
    
    /**
     * Crea un directorio si no existe
     */
    public static void createDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
            logger.debug("Directorio creado: {}", directory);
        }
    }
    
    /**
     * Elimina un directorio y todo su contenido
     */
    public static void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (Stream<Path> walk = Files.walk(directory)) {
                walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            logger.error("Error al eliminar: {}", path, e);
                        }
                    });
            }
            logger.info("Directorio eliminado: {}", directory);
        }
    }
    
    /**
     * Copia un archivo
     */
    public static void copyFile(Path source, Path destination) throws IOException {
        createDirectory(destination.getParent());
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        logger.debug("Archivo copiado: {} -> {}", source, destination);
    }
    
    /**
     * Lee el contenido de un archivo como String
     */
    public static String readFileAsString(Path file) throws IOException {
        return Files.readString(file);
    }
    
    /**
     * Escribe contenido a un archivo
     */
    public static void writeStringToFile(Path file, String content) throws IOException {
        createDirectory(file.getParent());
        Files.writeString(file, content);
        logger.debug("Archivo escrito: {}", file);
    }
    
    /**
     * Calcula el hash SHA-1 de un archivo
     */
    public static String calculateSHA1(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        
        try (InputStream fis = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        
        byte[] hashBytes = digest.digest();
        StringBuilder hexString = new StringBuilder();
        
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        
        return hexString.toString();
    }
    
    /**
     * Verifica si un archivo existe y tiene el hash correcto
     */
    public static boolean verifyFileHash(Path file, String expectedSha1) {
        if (!Files.exists(file)) {
            return false;
        }
        
        try {
            String actualSha1 = calculateSHA1(file);
            return actualSha1.equalsIgnoreCase(expectedSha1);
        } catch (Exception e) {
            logger.error("Error al verificar hash del archivo: {}", file, e);
            return false;
        }
    }
    
    /**
     * Obtiene el tamaño de un archivo en bytes
     */
    public static long getFileSize(Path file) throws IOException {
        return Files.size(file);
    }
    
    /**
     * Verifica si un directorio está vacío
     */
    public static boolean isDirectoryEmpty(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        
        try (Stream<Path> entries = Files.list(directory)) {
            return !entries.findFirst().isPresent();
        }
    }
    
    /**
     * Abre el explorador de archivos en la ruta especificada
     */
    public static void openInFileExplorer(Path path) {
        try {
            File file = path.toFile();
            if (!file.exists()) {
                logger.warn("La ruta no existe: {}", path);
                return;
            }
            
            switch (PlatformUtil.getOS()) {
                case WINDOWS:
                    // Abrir la carpeta directamente
                    Runtime.getRuntime().exec("explorer.exe \"" + file.getAbsolutePath() + "\"");
                    break;
                case MACOS:
                    Runtime.getRuntime().exec(new String[]{"open", "-R", file.getAbsolutePath()});
                    break;
                case LINUX:
                    // Intentar con xdg-open
                    Runtime.getRuntime().exec(new String[]{"xdg-open", file.getAbsolutePath()});
                    break;
                default:
                    logger.warn("Sistema operativo no soportado para abrir explorador");
            }
        } catch (IOException e) {
            logger.error("Error al abrir explorador de archivos", e);
        }
    }
}
