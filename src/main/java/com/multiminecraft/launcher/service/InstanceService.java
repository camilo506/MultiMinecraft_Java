package com.multiminecraft.launcher.service;

import com.multiminecraft.launcher.model.Instance;
import com.multiminecraft.launcher.model.LoaderType;
import com.multiminecraft.launcher.util.FileUtil;
import com.multiminecraft.launcher.util.PlatformUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Servicio para gestionar instancias de Minecraft
 */
public class InstanceService {

    private static final Logger logger = LoggerFactory.getLogger(InstanceService.class);

    private final ConfigService configService;
    private final MojangService mojangService;

    public InstanceService() {
        this.configService = ConfigService.getInstance();
        this.mojangService = new MojangService();
    }

    /**
     * Crea una nueva instancia de Minecraft
     */
    public void createInstance(Instance instance, Consumer<String> statusCallback) throws Exception {
        createInstance(instance, statusCallback, null);
    }

    /**
     * Crea una nueva instancia de Minecraft con callback de progreso numérico
     */
    public void createInstance(Instance instance, Consumer<String> statusCallback, Consumer<Double> progressCallback) throws Exception {
        logger.info("Creando instancia: {}", instance.getName());

        // Validar nombre
        if (instance.getName() == null || instance.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de la instancia no puede estar vacío");
        }

        // Verificar que no exista ya
        if (instanceExists(instance.getName())) {
            throw new IllegalArgumentException("Ya existe una instancia con ese nombre");
        }

        Path instanceDir = configService.getInstanceDirectory(instance.getName());
        Path minecraftDir = configService.getInstanceMinecraftDirectory(instance.getName());

        boolean hasLoader = instance.getLoader() != null && instance.getLoader() != LoaderType.VANILLA;

        try {
            // Crear estructura de directorios
            if (statusCallback != null)
                statusCallback.accept("Creando estructura de directorios...");
            if (progressCallback != null)
                progressCallback.accept(0.0);
            createInstanceDirectories(minecraftDir);

            // Descargar Minecraft Vanilla (0% - 80% del progreso total)
            if (statusCallback != null)
                statusCallback.accept("Descargando Minecraft " + instance.getVersion() + "...");
            mojangService.downloadVersion(instance.getVersion(), minecraftDir, statusCallback, progress -> {
                if (progressCallback != null) {
                    double scaled = hasLoader ? progress * 0.7 : progress * 0.9;
                    progressCallback.accept(scaled);
                }
            });

            // Instalar loader si no es Vanilla
            LoaderType loader = instance.getLoader();
            logger.debug("Loader de instancia: {} (comparado con VANILLA: {})", loader, loader != LoaderType.VANILLA);

            if (hasLoader) {
                if (statusCallback != null) {
                    statusCallback.accept("Instalando " + loader.getDisplayName() + "...");
                }
                if (progressCallback != null)
                    progressCallback.accept(0.7);
                logger.info("Instalando loader: {}", loader);
                installLoader(instance, minecraftDir, statusCallback);
                if (progressCallback != null)
                    progressCallback.accept(0.9);
            } else {
                logger.info("Instancia Vanilla, no se instala loader");
            }

            // Guardar configuración de la instancia
            if (statusCallback != null)
                statusCallback.accept("Guardando configuración...");
            if (progressCallback != null)
                progressCallback.accept(0.95);
            configService.saveInstanceConfig(instance);

            // Copiar ícono por defecto si no tiene uno personalizado
            if (instance.getIcon().equals("default-instance-icon.png")) {
                try {
                    String defaultIconName = "art-Crafting_Table.png";
                    String resourcePath = "/icons/" + defaultIconName;
                    java.io.InputStream iconStream = getClass().getResourceAsStream(resourcePath);
                    
                    if (iconStream != null) {
                        Path iconDir = instanceDir.resolve("icons");
                        FileUtil.createDirectory(iconDir);
                        Path iconFile = iconDir.resolve(defaultIconName);
                        
                        // Copiar el ícono desde resources a la carpeta de la instancia
                        Files.copy(iconStream, iconFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        iconStream.close();
                        
                        // Actualizar el nombre del ícono en la instancia
                        instance.setIcon(defaultIconName);
                        configService.saveInstanceConfig(instance);
                        
                        logger.info("Ícono por defecto copiado: {}", iconFile);
                    } else {
                        logger.warn("No se encontró el ícono por defecto en resources: {}", resourcePath);
                    }
                } catch (Exception e) {
                    logger.warn("Error al copiar ícono por defecto, continuando sin él", e);
                }
            }

            logger.info("Instancia creada exitosamente: {}", instance.getName());
            if (progressCallback != null)
                progressCallback.accept(1.0);

        } catch (Exception e) {
            // Si algo falla, limpiar
            logger.error("Error al crear instancia, limpiando...", e);
            try {
                FileUtil.deleteDirectory(instanceDir);
            } catch (IOException cleanupError) {
                logger.error("Error al limpiar instancia fallida", cleanupError);
            }
            throw e;
        }
    }

    /**
     * Crea la estructura de directorios de una instancia
     */
    private void createInstanceDirectories(Path minecraftDir) throws IOException {
        FileUtil.createDirectory(minecraftDir);
        FileUtil.createDirectory(minecraftDir.resolve("versions"));
        FileUtil.createDirectory(minecraftDir.resolve("libraries"));
        FileUtil.createDirectory(minecraftDir.resolve("assets"));
        FileUtil.createDirectory(minecraftDir.resolve("mods"));
        FileUtil.createDirectory(minecraftDir.resolve("resourcepacks"));
        FileUtil.createDirectory(minecraftDir.resolve("shaderpacks"));
        FileUtil.createDirectory(minecraftDir.resolve("saves"));
        FileUtil.createDirectory(minecraftDir.resolve("screenshots"));
        FileUtil.createDirectory(minecraftDir.resolve("logs"));
    }

    /**
     * Instala un mod loader (Forge o Fabric)
     */
    private void installLoader(Instance instance, Path minecraftDir, Consumer<String> statusCallback) throws Exception {
        switch (instance.getLoader()) {
            case FORGE:
                ForgeService forgeService = new ForgeService();
                // installForge ahora maneja toda la verificación y descarga de librerías internamente
                forgeService.installForge(instance.getVersion(), minecraftDir, statusCallback);
                logger.info("Forge instalado y verificado para instancia: {}", instance.getName());
                break;

            case FABRIC:
                FabricService fabricService = new FabricService();
                fabricService.installFabric(instance.getVersion(), minecraftDir, statusCallback);
                break;

            default:
                // Vanilla, no hacer nada
                break;
        }
    }

    /**
     * Lista todas las instancias disponibles
     */
    public List<Instance> listInstances() {
        List<Instance> instances = new ArrayList<>();
        Path instancesDir = PlatformUtil.getInstancesDirectory();

        if (!Files.exists(instancesDir)) {
            return instances;
        }

        try (Stream<Path> paths = Files.list(instancesDir)) {
            paths.filter(Files::isDirectory)
                    .forEach(instanceDir -> {
                        try {
                            String instanceName = instanceDir.getFileName().toString();
                            Instance instance = configService.loadInstanceConfig(instanceName);
                            instances.add(instance);
                        } catch (IOException e) {
                            logger.warn("No se pudo cargar instancia: {}", instanceDir, e);
                        }
                    });
        } catch (IOException e) {
            logger.error("Error al listar instancias", e);
        }

        return instances;
    }

    /**
     * Elimina una instancia
     */
    public void deleteInstance(String instanceName) throws IOException {
        logger.info("Eliminando instancia: {}", instanceName);

        Path instanceDir = configService.getInstanceDirectory(instanceName);

        if (!Files.exists(instanceDir)) {
            throw new IOException("La instancia no existe: " + instanceName);
        }

        FileUtil.deleteDirectory(instanceDir);
        logger.info("Instancia eliminada: {}", instanceName);
    }

    /**
     * Actualiza la configuración de una instancia
     */
    public void updateInstance(Instance instance) throws IOException {
        logger.info("Actualizando instancia: {}", instance.getName());
        configService.saveInstanceConfig(instance);
    }

    /**
     * Abre la carpeta de una instancia en el explorador
     */
    public void openInstanceFolder(String instanceName) {
        Path instanceDir = configService.getInstanceDirectory(instanceName);
        FileUtil.openInFileExplorer(instanceDir);
    }

    /**
     * Verifica si existe una instancia con el nombre dado
     */
    public boolean instanceExists(String instanceName) {
        Path instanceDir = configService.getInstanceDirectory(instanceName);
        return Files.exists(instanceDir);
    }

    /**
     * Obtiene una instancia por nombre
     */
    public Instance getInstance(String instanceName) throws IOException {
        return configService.loadInstanceConfig(instanceName);
    }
}
