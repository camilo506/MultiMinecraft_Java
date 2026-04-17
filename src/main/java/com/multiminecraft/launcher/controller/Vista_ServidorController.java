package com.multiminecraft.launcher.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import com.multiminecraft.launcher.App;
import com.multiminecraft.launcher.util.AlertUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controlador para la vista Vista_Servidor.
 * Se muestra dentro del área central del launcher principal.
 */
public class Vista_ServidorController {
    
    private static final Logger logger = LoggerFactory.getLogger(Vista_ServidorController.class);

    @FXML
    public void initialize() {
        logger.info("Inicializando Vista_ServidorController");
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
