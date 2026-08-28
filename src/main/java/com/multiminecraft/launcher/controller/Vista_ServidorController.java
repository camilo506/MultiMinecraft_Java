package com.multiminecraft.launcher.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import com.multiminecraft.launcher.App;
import com.multiminecraft.launcher.model.Instance;
import com.multiminecraft.launcher.service.ModpackService;
import com.multiminecraft.launcher.service.InstanceService;
import com.multiminecraft.launcher.util.AlertUtil;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.DialogPane;
import com.multiminecraft.launcher.util.JsonUtil;
import com.multiminecraft.launcher.service.DownloadService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controlador para la vista Vista_Servidor.
 * Se muestra dentro del área central del launcher principal.
 */
public class Vista_ServidorController {
    
    private static final Logger logger = LoggerFactory.getLogger(Vista_ServidorController.class);

    // URL del archivo de configuración remota en GitHub
    private static final String REMOTE_CONFIG_URL = "https://raw.githubusercontent.com/camilo506/Launcher_Configuracion/main/Direccion-Descargas.json";

    @FXML private Button btnInstallExiliados;
    @FXML private Button btnInstallMods;

    private final ModpackService modpackService = new ModpackService();
    private final DownloadService downloadService = new DownloadService();
    private final InstanceService instanceService = new InstanceService();
    
    // Enlaces por defecto (Fallbacks)
    private String exiliadosUrl = "https://drive.google.com/file/d/1BuBWyig6oVjQ7f5dVXM2EEkqLnc1Ymuv/view?usp=sharing";
    private String modsUrl = "https://drive.google.com/file/d/YOUR_MODS_FILE_ID/view?usp=sharing";
    private String launcherDownloadUrl = "";

    @FXML
    public void initialize() {
        logger.info("Inicializando Vista_ServidorController");
        loadRemoteConfig();
    }

    /**
     * Carga los enlaces actualizados desde GitHub de forma asíncrona
     */
    private void loadRemoteConfig() {
        new Thread(() -> {
            try {
                logger.info("Cargando configuración remota de servidores desde GitHub...");
                String json = downloadService.downloadString(REMOTE_CONFIG_URL);
                if (json != null && !json.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> config = JsonUtil.fromJson(json, Map.class);
                    
                    if (config.containsKey("exiliados_url")) {
                        this.exiliadosUrl = config.get("exiliados_url");
                        logger.info("URL de Exiliados actualizada remotamente.");
                    }
                    if (config.containsKey("mods_url")) {
                        this.modsUrl = config.get("mods_url");
                        logger.info("URL de Pack de Mods actualizada remotamente.");
                    }
                    if (config.containsKey("MultiMinecraft_url")) {
                        this.launcherDownloadUrl = config.get("MultiMinecraft_url");
                        logger.info("URL del Launcher actualizada remotamente.");
                    }
                }
            } catch (Exception e) {
                logger.warn("No se pudo cargar la configuración remota (usando fallbacks locales): {}", e.getMessage());
            }
        }, "RemoteConfigLoader").start();
    }

    @FXML
    private void onInstallExiliados() {
        startModpackInstallation("Exiliados ModPack", exiliadosUrl);
    }

    @FXML
    private void onInstallMods() {
        // Verificar que la URL esté configurada
        if (modsUrl.contains("YOUR_")) {
            AlertUtil.showWarning("Configuración requerida", 
                "El enlace de descarga para 'Pack de Mods' no ha sido configurado todavía.");
            return;
        }

        // Obtener las instancias existentes
        List<Instance> instances = instanceService.listInstances();
        
        if (instances.isEmpty()) {
            AlertUtil.showWarning("Sin instancias", 
                "No hay instancias disponibles. Primero crea una instancia o instala el modpack Exiliados.");
            return;
        }

        // Crear lista de nombres para el selector
        List<String> instanceNames = instances.stream()
            .map(Instance::getName)
            .collect(Collectors.toList());

        // Mostrar diálogo de selección de instancia
        ChoiceDialog<String> dialog = new ChoiceDialog<>(instanceNames.get(0), instanceNames);
        dialog.setTitle("Seleccionar Instancia");
        dialog.setHeaderText("Actualizar Mods del Servidor");
        dialog.setContentText("Selecciona la instancia donde se instalarán los mods:");
        
        // Aplicar estilo al diálogo
        styleChoiceDialog(dialog);

        Optional<String> result = dialog.showAndWait();
        
        if (result.isEmpty()) {
            return; // El usuario canceló
        }

        String selectedInstance = result.get();

        // Confirmar la acción
        boolean confirmed = AlertUtil.showConfirmation("Confirmar actualización",
            "Se actualizarán los mods de la instancia '" + selectedInstance + "'.\n\n" +
            "• Los mods indicados para eliminación serán removidos.\n" +
            "• Los mods nuevos se agregarán a la carpeta.\n\n" +
            "¿Deseas continuar?");

        if (!confirmed) {
            return;
        }

        // Iniciar la actualización de mods
        startModsUpdate(selectedInstance, modsUrl);
    }

    /**
     * Aplica los estilos del proyecto al ChoiceDialog
     */
    private void styleChoiceDialog(ChoiceDialog<String> dialog) {
        dialog.initStyle(StageStyle.TRANSPARENT);
        DialogPane dialogPane = dialog.getDialogPane();
        
        String mainCss = getClass().getResource("/css/main.css") != null
                ? getClass().getResource("/css/main.css").toExternalForm() : null;
        String themeCss = App.getActiveThemeCssExternalForm(getClass());
        String alertCss = getClass().getResource("/css/alert.css") != null
                ? getClass().getResource("/css/alert.css").toExternalForm() : null;

        if (mainCss != null) dialogPane.getStylesheets().add(mainCss);
        if (themeCss != null) dialogPane.getStylesheets().add(themeCss);
        if (alertCss != null) dialogPane.getStylesheets().add(alertCss);

        dialogPane.getStyleClass().add("custom-alert");

        dialog.setOnShowing(e -> {
            Stage stage = (Stage) dialogPane.getScene().getWindow();
            if (stage != null && stage.getScene() != null) {
                stage.getScene().setFill(javafx.scene.paint.Color.TRANSPARENT);
            }
        });
    }

    /**
     * Inicia la actualización de mods en una instancia existente
     */
    private void startModsUpdate(String instanceName, String driveUrl) {
        Stage ownerStage = (Stage) btnInstallMods.getScene().getWindow();
        DescargaProgresoController progressCtrl = DescargaProgresoController.show(ownerStage);
        
        if (progressCtrl == null) {
            AlertUtil.showError("Error", "No se pudo iniciar la ventana de progreso.");
            return;
        }

        new Thread(() -> {
            try {
                modpackService.updateModsFromDrive(driveUrl, instanceName,
                    status -> progressCtrl.setProgress(progressCtrl.getCurrentProgress(), "Mods: " + instanceName, status),
                    progress -> progressCtrl.setProgress(progress)
                );
                
                Platform.runLater(() -> {
                    progressCtrl.close();
                    AlertUtil.showInfo("Actualización Exitosa", 
                        "Los mods se han actualizado correctamente en la instancia '" + instanceName + "'.");
                });
            } catch (Exception e) {
                logger.error("Error al actualizar mods de la instancia: {}", instanceName, e);
                Platform.runLater(() -> {
                    progressCtrl.close();
                    AlertUtil.showError("Error de Actualización", 
                        "No se pudieron actualizar los mods: " + e.getMessage());
                });
            }
        }).start();
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
            String themeCss = App.getActiveThemeCssExternalForm(getClass());
            if (themeCss != null) {
                scene.getStylesheets().add(themeCss);
            }
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
