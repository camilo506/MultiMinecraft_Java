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
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir", "temp"), "modpack_download_" + System.currentTimeMillis());
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
            
            // Establecer fecha de creación y marca de instancia especial
            modpackInstance.setCreatedAt(java.time.LocalDateTime.now());
            modpackInstance.setSpecial(true);
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

            // 8. Reemplazo final de options.txt para garantizar prioridad del modpack
            replaceOptionsAtEnd(modpackRoot, targetMcDir);
            
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
     * Actualiza los mods de una instancia existente desde un ZIP en Google Drive.
     * El ZIP debe contener archivos .jar de mods y opcionalmente un archivo .json
     * con una lista de mods a eliminar de la instancia antes de copiar los nuevos.
     *
     * @param driveUrl URL de compartir de Drive
     * @param instanceName Nombre de la instancia destino
     * @param statusCallback Callback para mensajes de estado
     * @param progressCallback Callback para el porcentaje de progreso (0-1)
     * @throws Exception Si ocurre un error en el proceso
     */
    public void updateModsFromDrive(String driveUrl, String instanceName, 
            Consumer<String> statusCallback, Consumer<Double> progressCallback) throws Exception {
        
        logger.info("Iniciando actualización de mods para instancia '{}' desde Drive: {}", instanceName, driveUrl);
        
        // 1. Convertir enlace a descarga directa
        String directUrl = GoogleDriveUtil.getDirectDownloadUrl(driveUrl);
        
        // 2. Crear carpetas temporales
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir", "temp"), "mods_update_" + System.currentTimeMillis());
        Files.createDirectories(tempDir);
        Path zipPath = tempDir.resolve("mods.zip");
        Path extractDir = tempDir.resolve("extracted");
        
        try {
            // 3. Descargar ZIP (0.0 - 0.5)
            statusCallback.accept("Descargando paquete de mods...");
            downloadService.downloadFile(directUrl, zipPath, p -> progressCallback.accept(p * 0.5));
            
            // 4. Extraer ZIP (0.5 - 0.6)
            statusCallback.accept("Extrayendo mods...");
            progressCallback.accept(0.55);
            ZipUtil.unzip(zipPath, extractDir);
            progressCallback.accept(0.6);
            
            // 5. Obtener la carpeta de mods de la instancia destino
            Path targetModsDir = configService.getInstanceMinecraftDirectory(instanceName).resolve("mods");
            if (!Files.exists(targetModsDir)) {
                Files.createDirectories(targetModsDir);
            }
            
            // 6. Buscar y procesar el archivo JSON de eliminación (0.6 - 0.7)
            statusCallback.accept("Procesando lista de mods...");
            processModRemovalJson(extractDir, targetModsDir);
            progressCallback.accept(0.7);
            
            // 7. Copiar los nuevos mods al directorio de la instancia (0.7 - 1.0)
            statusCallback.accept("Instalando mods nuevos...");
            int modsInstalled = deployNewMods(extractDir, targetModsDir);
            progressCallback.accept(1.0);
            
            statusCallback.accept("Actualización completada: " + modsInstalled + " mods instalados en '" + instanceName + "'.");
            logger.info("Actualización de mods completada para '{}': {} mods instalados", instanceName, modsInstalled);
            
        } catch (Exception e) {
            logger.error("Error al actualizar mods de la instancia '{}'", instanceName, e);
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
     * Busca un archivo .json dentro del directorio extraído y elimina los mods
     * listados en él de la carpeta de mods de la instancia.
     * 
     * Formato esperado del JSON:
     * {
     *   "eliminar": ["modA.jar", "modB.jar"]
     * }
     * 
     * O bien un array simple:
     * ["modA.jar", "modB.jar"]
     */
    @SuppressWarnings("unchecked")
    private void processModRemovalJson(Path extractDir, Path targetModsDir) throws IOException {
        // Buscar archivos .json en el directorio extraído (nivel raíz y subcarpetas)
        Path jsonFile = null;
        try (var stream = Files.walk(extractDir, 3)) {
            jsonFile = stream
                .filter(p -> Files.isRegularFile(p) && p.toString().toLowerCase().endsWith(".json"))
                .findFirst()
                .orElse(null);
        }
        
        if (jsonFile == null) {
            logger.info("No se encontró archivo JSON de eliminación en el ZIP. Se omitirá la eliminación selectiva.");
            return;
        }
        
        logger.info("Archivo JSON de eliminación encontrado: {}", jsonFile.getFileName());
        String jsonContent = Files.readString(jsonFile);
        
        java.util.List<String> modsToRemove = new java.util.ArrayList<>();
        
        try {
            // Intentar como objeto con clave "eliminar"
            Object parsed = JsonUtil.fromJson(jsonContent, Object.class);
            if (parsed instanceof java.util.Map) {
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) parsed;
                Object eliminarObj = map.get("eliminar");
                if (eliminarObj == null) eliminarObj = map.get("remove");
                if (eliminarObj instanceof java.util.List) {
                    for (Object item : (java.util.List<?>) eliminarObj) {
                        modsToRemove.add(item.toString());
                    }
                }
            } else if (parsed instanceof java.util.List) {
                // Array simple
                for (Object item : (java.util.List<?>) parsed) {
                    modsToRemove.add(item.toString());
                }
            }
        } catch (Exception e) {
            logger.warn("Error al parsear el JSON de eliminación: {}", e.getMessage());
            return;
        }
        
        if (modsToRemove.isEmpty()) {
            logger.info("La lista de mods a eliminar está vacía.");
            return;
        }
        
        logger.info("Mods a eliminar: {}", modsToRemove);
        
        // Verificar si se usa la palabra clave especial "todos" para borrar todo
        boolean deleteAll = modsToRemove.stream()
            .anyMatch(s -> s.equalsIgnoreCase("todos") || s.equalsIgnoreCase("all"));
        
        if (deleteAll) {
            logger.info("Palabra clave 'todos' detectada. Eliminando TODOS los mods de la instancia.");
        }
        
        // Eliminar los mods indicados de la carpeta de la instancia
        if (Files.exists(targetModsDir)) {
            try (var modFiles = Files.list(targetModsDir)) {
                modFiles.filter(Files::isRegularFile).forEach(modFile -> {
                    String fileName = modFile.getFileName().toString();
                    
                    boolean shouldRemove;
                    if (deleteAll) {
                        // "todos" → eliminar todos los .jar
                        shouldRemove = fileName.toLowerCase().endsWith(".jar");
                    } else {
                        // Eliminación selectiva por nombre
                        shouldRemove = modsToRemove.stream()
                            .anyMatch(toRemove -> fileName.equalsIgnoreCase(toRemove) 
                                || fileName.toLowerCase().contains(toRemove.toLowerCase()));
                    }
                    
                    if (shouldRemove) {
                        try {
                            Files.delete(modFile);
                            logger.info("Mod eliminado: {}", fileName);
                        } catch (IOException e) {
                            logger.warn("No se pudo eliminar el mod: {}", fileName, e);
                        }
                    }
                });
            }
        }
    }

    /**
     * Copia los archivos .jar de mods desde el directorio extraído a la carpeta de mods destino.
     * Busca recursivamente en el directorio extraído para encontrar los archivos .jar.
     * 
     * @return Cantidad de mods copiados
     */
    private int deployNewMods(Path extractDir, Path targetModsDir) throws IOException {
        // Primero, buscar si hay una carpeta "mods" dentro del ZIP
        Path modsSourceDir = findItem(extractDir, "mods", true);
        
        // Si encontramos una carpeta "mods", usarla como fuente; sino, usar la raíz
        Path sourceDir = (modsSourceDir != null) ? modsSourceDir : extractDir;
        
        int count = 0;
        try (var stream = Files.list(sourceDir)) {
            var jarFiles = stream
                .filter(p -> Files.isRegularFile(p) && p.toString().toLowerCase().endsWith(".jar"))
                .collect(java.util.stream.Collectors.toList());
            
            for (Path jarFile : jarFiles) {
                Path dest = targetModsDir.resolve(jarFile.getFileName());
                Files.copy(jarFile, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                logger.debug("Mod copiado: {}", jarFile.getFileName());
                count++;
            }
        }
        
        return count;
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
    }

    /**
     * Reemplaza al final el options.txt de la instancia con el del modpack.
     */
    private void replaceOptionsAtEnd(Path modpackRoot, Path targetMcDir) throws IOException {
        Path modpackOptions = modpackRoot.resolve("options.txt");
        if (!Files.exists(modpackOptions) || !Files.isRegularFile(modpackOptions)) {
            logger.info("El modpack no contiene options.txt en su raíz real, se omite reemplazo final");
            return;
        }

        Path targetOptions = targetMcDir.resolve("options.txt");
        Files.copy(modpackOptions, targetOptions, StandardCopyOption.REPLACE_EXISTING);
        logger.info("options.txt final reemplazado desde el modpack: {}", modpackOptions);
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
