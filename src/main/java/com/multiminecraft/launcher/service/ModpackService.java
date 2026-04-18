package com.multiminecraft.launcher.service;

import com.multiminecraft.launcher.model.Instance;
import com.multiminecraft.launcher.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.function.Consumer;

/**
 * Servicio para gestionar la instalación de Modpacks
 */
public class ModpackService {

    private static final Logger logger = LoggerFactory.getLogger(ModpackService.class);

    private final DownloadService downloadService;
    private final InstanceService instanceService;
    private final ConfigService configService;

    public ModpackService() {
        this.downloadService = new DownloadService();
        this.instanceService = new InstanceService();
        this.configService = ConfigService.getInstance();
    }

    /**
     * Instala un modpack desde una URL de Google Drive
     * @param driveUrl URL de compartir de Drive
     * @param statusCallback Callback para mensajes de estado
     * @param progressCallback Callback para el porcentaje de progreso (0-1)
     * @throws Exception Si ocurre un error en el proceso
     */
    public void installModpackFromDrive(String driveUrl, Consumer<String> statusCallback, Consumer<Double> progressCallback) throws Exception {
        logger.info("Iniciando instalación de modpack desde Drive: {}", driveUrl);
        
        // 1. Convertir enlace a descarga directa
        String directUrl = GoogleDriveUtil.getDirectDownloadUrl(driveUrl);
        
        // 2. Crear carpetas temporales
        Path tempDir = Paths.get(System.getProperty("java.io.tmpcase", "temp"), "modpack_download_" + System.currentTimeMillis());
        Files.createDirectories(tempDir);
        Path zipPath = tempDir.resolve("modpack.zip");
        Path extractDir = tempDir.resolve("extracted");
        
        try {
            // 3. Descargar ZIP (0.0 - 0.4)
            statusCallback.accept("Descargando Modpack...");
            downloadService.downloadFile(directUrl, zipPath, p -> progressCallback.accept(p * 0.4));
            
            // 4. Extraer ZIP (0.4 - 0.5)
            statusCallback.accept("Extrayendo archivos...");
            progressCallback.accept(0.45);
            ZipUtil.unzip(zipPath, extractDir);
            progressCallback.accept(0.5);
            
            // 5. Encontrar la carpeta real que contiene los recursos (manejando carpetas anidadas en el ZIP)
            Path modpackRoot = findModpackRoot(extractDir);
            
            if (modpackRoot == null) {
                // Si no se encuentra instancia.json, verificamos si hay un ZIP anidado (común en algunas compresiones de Drive)
                Path nestedZip = findNestedZip(extractDir);
                if (nestedZip != null) {
                    statusCallback.accept("Extrayendo paquete interno...");
                    Path nestedExtractDir = extractDir.resolve("inner_contents");
                    ZipUtil.unzip(nestedZip, nestedExtractDir);
                    modpackRoot = findModpackRoot(nestedExtractDir);
                }
            }

            if (modpackRoot == null) {
                throw new IOException("El modpack no contiene el archivo instancia.json (verificado en raíz, subcarpetas y paquetes internos)");
            }
            
            Path jsonPath = modpackRoot.resolve("instancia.json");
            statusCallback.accept("Leyendo configuración del modpack...");
            String jsonContent = Files.readString(jsonPath);
            Instance modpackInstance = JsonUtil.fromJson(jsonContent, Instance.class);
            
            if (modpackInstance.getName() == null) {
                throw new IOException("El archivo instancia.json no tiene un nombre válido");
            }

            // Usar el nombre de jugador configurado globalmente en el launcher
            String globalPlayerName = configService.getLauncherConfig().getPlayerName();
            if (globalPlayerName != null && !globalPlayerName.trim().isEmpty()) {
                modpackInstance.setPlayerName(globalPlayerName);
                logger.info("Asignando nombre de jugador '{}' a la nueva instancia", globalPlayerName);
            }
            
            // Establecer fecha de creación
            modpackInstance.setCreatedAt(java.time.LocalDateTime.now());
            statusCallback.accept("Instalando base de Minecraft y complementos...");
            instanceService.createInstance(modpackInstance, 
                status -> statusCallback.accept("Base: " + status),
                p -> progressCallback.accept(0.5 + (p * 0.4))
            );
            
            // 7. Desplegar archivos del modpack (0.9 - 1.0)
            statusCallback.accept("Implementando archivos del modpack...");
            Path targetMcDir = configService.getInstanceMinecraftDirectory(modpackInstance.getName());
            
            // Usamos extractDir como base para buscar todos los archivos necesarios
            deployModpackFiles(extractDir, targetMcDir);
            
            progressCallback.accept(1.0);
            statusCallback.accept("Modpack '" + modpackInstance.getName() + "' instalado con éxito.");
            logger.info("Modpack instalado exitosamente en {}", targetMcDir);
            
        } catch (Exception e) {
            logger.error("Error en la instalación del modpack", e);
            throw e;
        } finally {
            // 8. Limpieza
            try {
                FileUtil.deleteDirectory(tempDir);
            } catch (IOException e) {
                logger.warn("No se pudo limpiar el directorio temporal: {}", tempDir);
            }
        }
    }

    /**
     * Busca recursivamente la carpeta que contiene el archivo instancia.json
     */
    private Path findModpackRoot(Path startPath) throws IOException {
        try (var stream = Files.walk(startPath, 5)) {
            return stream
                .filter(p -> p.getFileName().toString().equalsIgnoreCase("instancia.json"))
                .map(Path::getParent)
                .findFirst()
                .orElse(null);
        }
    }

    /**
     * Busca un archivo o carpeta específica dentro de un directorio (recursivo)
     */
    private Path findItem(Path startPath, String name, boolean isDirectory) throws IOException {
        try (var stream = Files.walk(startPath, 5)) {
            return stream
                .filter(p -> p.getFileName().toString().equalsIgnoreCase(name) && 
                            Files.isDirectory(p) == isDirectory)
                .findFirst()
                .orElse(null);
        }
    }

    /**
     * Busca un archivo ZIP real dentro de una carpeta
     */
    private Path findNestedZip(Path startPath) throws IOException {
        try (var stream = Files.walk(startPath, 3)) {
            return stream
                .filter(p -> Files.isRegularFile(p) && p.toString().toLowerCase().endsWith(".zip"))
                .findFirst()
                .orElse(null);
        }
    }

    /**
     * Mueve las carpetas esenciales del modpack extraído a la carpeta de la instancia
     */
    private void deployModpackFiles(Path source, Path target) throws IOException {
        String[] essentialFolders = {"mods", "config", "resourcepacks", "shaderpacks"};
        String[] essentialFiles = {"options.txt"};
        
        for (String folder : essentialFolders) {
            Path srcPath = findItem(source, folder, true);
            if (srcPath != null) {
                logger.info("Encontrada carpeta: {} -> {}", folder, srcPath);
                Path destPath = target.resolve(folder);
                if (Files.exists(destPath)) {
                    FileUtil.deleteDirectory(destPath);
                }
                Files.createDirectories(destPath);
                copyFolder(srcPath, destPath);
            }
        }
        
        for (String file : essentialFiles) {
            Path srcPath = findItem(source, file, false);
            if (srcPath != null) {
                logger.info("Encontrado archivo: {} -> {}", file, srcPath);
                Files.copy(srcPath, target.resolve(file), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void copyFolder(Path source, Path target) throws IOException {
        try (var stream = Files.walk(source)) {
            stream.forEach(p -> {
                try {
                    Path dest = target.resolve(source.relativize(p));
                    if (Files.isDirectory(p)) {
                        if (!Files.exists(dest)) Files.createDirectories(dest);
                    } else {
                        Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) throw (IOException) e.getCause();
            throw e;
        }
    }
}
