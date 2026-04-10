package com.multiminecraft.launcher.controller;

import com.multiminecraft.launcher.model.Instance;
import com.multiminecraft.launcher.service.ConfigService;
import com.multiminecraft.launcher.util.PlatformUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Controlador para la vista de recursos (mods, texturas, shaders, etc.)
 */
public class RecursosController {
    
    private static final Logger logger = LoggerFactory.getLogger(RecursosController.class);
    
    @FXML private Label titleLabel;
    @FXML private Button modPackButton;
    @FXML private Button modButton;
    @FXML private Button shadersButton;
    @FXML private Button resourcePacksButton;
    
    @SuppressWarnings("unused")
    private Instance instance;
    
    private ConfigService configService;
    
    /**
     * Inicializa el controlador
     */
    @FXML
    private void initialize() {
        logger.debug("Inicializando vista de recursos");
        configService = ConfigService.getInstance();
    }
    
    /**
     * Establece la instancia para la cual se gestionan los recursos
     */
    public void setInstance(Instance instance) {
        this.instance = instance;
        if (instance != null && titleLabel != null) {
            titleLabel.setText("Incorporar Recursos");
        }
    }
    
    @FXML
    private void onModPackClicked() {
        if (instance == null) {
            logger.warn("No hay instancia seleccionada");
            return;
        }
        
        try {
            logger.debug("Abriendo carpeta .minecraft de instancia: {}", instance.getName());
            Path minecraftDir = configService.getInstanceMinecraftDirectory(instance.getName());
            
            // Crear la carpeta si no existe
            File dir = minecraftDir.toFile();
            if (!dir.exists()) {
                dir.mkdirs();
                logger.info("Carpeta .minecraft creada: {}", minecraftDir);
            }
            
            // Abrir la carpeta en el explorador
            openFolderInExplorer(minecraftDir);
            
        } catch (Exception e) {
            logger.error("Error al abrir carpeta .minecraft", e);
        }
    }
    
    @FXML
    private void onModClicked() {
        if (instance == null) {
            logger.warn("No hay instancia seleccionada");
            return;
        }
        
        try {
            logger.debug("Abriendo carpeta mods de instancia: {}", instance.getName());
            Path minecraftDir = configService.getInstanceMinecraftDirectory(instance.getName());
            Path modsDir = minecraftDir.resolve("mods");
            
            // Crear la carpeta si no existe
            File dir = modsDir.toFile();
            if (!dir.exists()) {
                dir.mkdirs();
                logger.info("Carpeta mods creada: {}", modsDir);
            }
            
            // Abrir la carpeta en el explorador
            openFolderInExplorer(modsDir);
            
        } catch (Exception e) {
            logger.error("Error al abrir carpeta mods", e);
        }
    }
    
    @FXML
    private void onShadersClicked() {
        if (instance == null) {
            logger.warn("No hay instancia seleccionada");
            return;
        }
        
        try {
            logger.debug("Abriendo carpeta shaderpacks de instancia: {}", instance.getName());
            Path minecraftDir = configService.getInstanceMinecraftDirectory(instance.getName());
            Path shaderpacksDir = minecraftDir.resolve("shaderpacks");
            
            // Crear la carpeta si no existe
            File dir = shaderpacksDir.toFile();
            if (!dir.exists()) {
                dir.mkdirs();
                logger.info("Carpeta shaderpacks creada: {}", shaderpacksDir);
            }
            
            // Abrir la carpeta en el explorador
            openFolderInExplorer(shaderpacksDir);
            
        } catch (Exception e) {
            logger.error("Error al abrir carpeta shaderpacks", e);
        }
    }
    
    @FXML
    private void onResourcePacksClicked() {
        if (instance == null) {
            logger.warn("No hay instancia seleccionada");
            return;
        }
        
        try {
            logger.debug("Abriendo carpeta resourcepacks de instancia: {}", instance.getName());
            Path minecraftDir = configService.getInstanceMinecraftDirectory(instance.getName());
            Path resourcepacksDir = minecraftDir.resolve("resourcepacks");
            
            // Crear la carpeta si no existe
            File dir = resourcepacksDir.toFile();
            if (!dir.exists()) {
                dir.mkdirs();
                logger.info("Carpeta resourcepacks creada: {}", resourcepacksDir);
            }
            
            // Abrir la carpeta en el explorador
            openFolderInExplorer(resourcepacksDir);
            
        } catch (Exception e) {
            logger.error("Error al abrir carpeta resourcepacks", e);
        }
    }
    
    /**
     * Abre una carpeta en el explorador de archivos del sistema
     */
    private void openFolderInExplorer(Path folderPath) {
        try {
            File folder = folderPath.toFile();
            if (!folder.exists()) {
                logger.warn("La carpeta no existe: {}", folderPath);
                return;
            }
            
            switch (PlatformUtil.getOS()) {
                case WINDOWS:
                    Runtime.getRuntime().exec("explorer.exe \"" + folder.getAbsolutePath() + "\"");
                    break;
                case MACOS:
                    Runtime.getRuntime().exec(new String[]{"open", folder.getAbsolutePath()});
                    break;
                case LINUX:
                    Runtime.getRuntime().exec(new String[]{"xdg-open", folder.getAbsolutePath()});
                    break;
                default:
                    logger.warn("Sistema operativo no soportado para abrir explorador");
            }
            
            logger.info("Carpeta abierta en explorador: {}", folderPath);
            
        } catch (IOException e) {
            logger.error("Error al abrir carpeta en explorador", e);
        }
    }
    
    @FXML
    private void onClose() {
        logger.debug("Cerrando vista de recursos");
        if (titleLabel != null && titleLabel.getScene() != null) {
            Stage stage = (Stage) titleLabel.getScene().getWindow();
            if (stage != null) {
                stage.close();
            }
        }
    }
}

