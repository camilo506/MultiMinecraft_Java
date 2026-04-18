package com.multiminecraft.launcher.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import com.multiminecraft.launcher.App;
import com.multiminecraft.launcher.service.ModpackService;
import com.multiminecraft.launcher.util.AlertUtil;
import javafx.application.Platform;
import javafx.scene.control.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controlador para la vista Vista_Servidor.
 * Se muestra dentro del área central del launcher principal.
 */
public class Vista_ServidorController {
    
    private static final Logger logger = LoggerFactory.getLogger(Vista_ServidorController.class);

    @FXML private Button btnInstallExiliados;
    @FXML private Button btnInstallMods;

    private final ModpackService modpackService = new ModpackService();

    @FXML
    public void initialize() {
        logger.info("Inicializando Vista_ServidorController");
    }

    @FXML
    private void onInstallExiliados() {
        // Enlace de Drive real para Exiliados ModPack (Archivo ZIP Público)
        String driveUrl = "https://drive.google.com/file/d/1BuBWyig6oVjQ7f5dVXM2EEkqLnc1Ymuv/view?usp=sharing";
        startModpackInstallation("Exiliados ModPack", driveUrl);
    }

    @FXML
    private void onInstallMods() {
        // Marcador de posición para la URL de Drive de Mods
        String driveUrl = "https://drive.google.com/file/d/YOUR_MODS_FILE_ID/view?usp=sharing";
        startModpackInstallation("Pack de Mods", driveUrl);
    }

    private void startModpackInstallation(String packName, String driveUrl) {
        if (driveUrl.contains("YOUR_")) {
            AlertUtil.showWarning("Configuración requerida", 
                "El enlace de descarga para '" + packName + "' no ha sido configurado todavía.");
            return;
        }

        Stage ownerStage = (Stage) btnInstallExiliados.getScene().getWindow();
        DescargaProgresoController progressCtrl = DescargaProgresoController.show(ownerStage);
        
        if (progressCtrl == null) {
            AlertUtil.showError("Error", "No se pudo iniciar la ventana de progreso.");
            return;
        }

        new Thread(() -> {
            try {
                modpackService.installModpackFromDrive(driveUrl, 
                    status -> progressCtrl.setProgress(progressCtrl.getCurrentProgress(), packName, status),
                    progress -> progressCtrl.setProgress(progress)
                );
                
                Platform.runLater(() -> {
                    progressCtrl.close();
                    
                    // Refrescar la lista de instancias en el controlador principal
                    PrincipalController principal = PrincipalController.getInstance();
                    if (principal != null) {
                        principal.refreshInstances();
                    }
                    
                    AlertUtil.showInfo("Instalación Exitosa", 
                        "El modpack '" + packName + "' se ha instalado correctamente. Ya puedes verlo en tu lista de instancias.");
                    
                    // Cerrar automáticamente la vista de servidor para volver a la biblioteca
                    onClose();
                });
            } catch (Exception e) {
                logger.error("Error al instalar modpack: {}", packName, e);
                Platform.runLater(() -> {
                    progressCtrl.close();
                    AlertUtil.showError("Error de Instalación", 
                        "No se pudo instalar el modpack: " + e.getMessage());
                });
            }
        }).start();
    }


    /**
     * Cierra la vista del servidor y restaura el contenido principal del launcher.
     */
    @FXML
    private void onClose() {
        logger.info("Cerrando Vista_Servidor, restaurando contenido principal");
        PrincipalController principal = PrincipalController.getInstance();
        if (principal != null) {
            principal.restoreMainContent();
        }
    }

    /**
     * Abre la vista de Configuración en una ventana modal.
     */
    @FXML
    private void onConfigClicked() {
        logger.info("Abriendo Configuración desde Vista_Servidor");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Configuracion.fxml"));
            Parent configView = loader.load();

            Stage configStage = new Stage();
            configStage.setTitle("Configuración");
            configStage.initModality(Modality.APPLICATION_MODAL);
            configStage.initOwner(App.getPrimaryStage());
            configStage.initStyle(StageStyle.TRANSPARENT);

            Scene scene = new Scene(configView, 750, 580);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource("/css/dark-theme.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource("/css/configuracion.css").toExternalForm());

            configStage.setScene(scene);
            configStage.setResizable(false);
            configStage.showAndWait();

        } catch (Exception e) {
            logger.error("Error al abrir Configuración", e);
            AlertUtil.showError("Error", "No se pudo abrir la configuración: " + e.getMessage());
        }
    }
}
