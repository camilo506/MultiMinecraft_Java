package com.multiminecraft.launcher.controller;

import com.multiminecraft.launcher.App;
import com.multiminecraft.launcher.model.LauncherConfig;
import com.multiminecraft.launcher.service.ConfigService;
import com.multiminecraft.launcher.service.DownloadService;
import com.multiminecraft.launcher.util.AlertUtil;
import com.multiminecraft.launcher.util.JsonUtil;
import com.multiminecraft.launcher.util.PlatformUtil;
import com.multiminecraft.launcher.service.UpdateService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Controlador de la vista de Configuración del launcher.
 * Permite al usuario editar: nombre del jugador, 
 * memoria RAM, tema e idioma.
 */
public class ConfiguracionController {

    private static final Logger logger = LoggerFactory.getLogger(ConfiguracionController.class);

    @FXML private TextField playerNameField;
    @FXML private ComboBox<String> memoryComboBox;
    @FXML private Label versionLabel;
    @FXML private Label updateStatusLabel;
    @FXML private Button btnUpdateLauncher;
    @FXML private Label websiteLink;
    @FXML private Label statusLabel;

    private static final String[] MEMORY_OPTIONS = {
        "1G", "2G", "3G", "4G", "6G", "8G", "10G", "12G", "16G"
    };

    private final DownloadService downloadService = new DownloadService();

    @FXML
    public void initialize() {
        logger.info("Inicializando vista de Configuración");
        memoryComboBox.setItems(FXCollections.observableArrayList(MEMORY_OPTIONS));
        
        // Listener para actualizar el nombre en el sidebar en tiempo real
        playerNameField.textProperty().addListener((observable, oldValue, newValue) -> {
            PrincipalController principal = PrincipalController.getInstance();
            if (principal != null) {
                principal.setPlayerNameText(newValue);
            }
            // Sincronizar con el objeto de configuración en memoria inmediatamente
            ConfigService.getInstance().getLauncherConfig().setPlayerName(newValue != null ? newValue.trim() : "");
        });

        loadCurrentConfig();
    }

    private void loadCurrentConfig() {
        try {
            ConfigService configService = ConfigService.getInstance();
            LauncherConfig config = configService.getLauncherConfig();

            String playerName = config.getPlayerName();
            if (playerName != null && !playerName.isEmpty()) {
                playerNameField.setText(playerName);
            }

            String memory = config.getDefaultMemory();
            if (memory != null && !memory.isEmpty()) {
                memoryComboBox.setValue(memory);
            } else {
                memoryComboBox.setValue("2G");
            }

            versionLabel.setText(UpdateService.LAUNCHER_VERSION);
            statusLabel.setText("");

            logger.info("Configuración actual cargada en el formulario");
        } catch (Exception e) {
            logger.error("Error al cargar la configuración actual", e);
        }
    }



    @FXML
    private void onSaveClicked() {
        try {
            ConfigService configService = ConfigService.getInstance();
            LauncherConfig config = configService.getLauncherConfig();

            String playerName = playerNameField.getText();
            if (playerName == null || playerName.trim().isEmpty()) {
                statusLabel.setText("⚠ El nombre no puede estar vacío");
                statusLabel.getStyleClass().setAll("config-status-error");
                return;
            }
            playerName = playerName.trim();
            config.setPlayerName(playerName);
            logger.info("Nombre de jugador actualizado a: {}", playerName);

            String selectedMemory = memoryComboBox.getValue();
            if (selectedMemory != null) {
                config.setDefaultMemory(selectedMemory);
            }

            // Cambio de tema deshabilitado temporalmente: se fuerza modo oscuro.
            config.setTheme("dark");
            App.applyTheme("dark");

            configService.saveLauncherConfig();

            PrincipalController principal = PrincipalController.getInstance();
            if (principal != null) {
                principal.refreshPlayerInfo();
            }

            statusLabel.setText("✓ Configuración guardada");
            statusLabel.getStyleClass().setAll("config-status-saved");
            logger.info("Configuración guardada exitosamente");

            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    javafx.application.Platform.runLater(() -> statusLabel.setText(""));
                } catch (InterruptedException ignored) {}
            }).start();

        } catch (Exception e) {
            logger.error("Error al guardar la configuración", e);
            statusLabel.setText("✗ Error al guardar");
            statusLabel.getStyleClass().setAll("config-status-error");
        }
    }

    @FXML
    private void onResetClicked() {
        boolean confirmed = AlertUtil.showConfirmation("Restablecer Configuración",
            "¿Deseas restablecer toda la configuración a los valores por defecto?\n\n" +
            "Esta acción no se puede deshacer.");
        if (confirmed) {
            playerNameField.setText("");
            memoryComboBox.setValue("2G");
            statusLabel.setText("⟳ Valores restablecidos (sin guardar)");
            statusLabel.getStyleClass().setAll("config-status-saved");
        }
    }

    @FXML
    private void onWebsiteLinkClicked() {
        PrincipalController principal = PrincipalController.getInstance();
        String url = principal != null ? principal.getSupportWebsiteUrl() : null;
        if (url == null || url.isBlank()) {
            AlertUtil.showWarning("Enlace no disponible", "No se pudo cargar la URL de la página web desde GitHub.");
            return;
        }
        App.openWebPage(url);
    }

    @FXML
    private void onUpdateLauncherClicked() {
        logger.info("Iniciando búsqueda de actualizaciones reales...");
        btnUpdateLauncher.setDisable(true);
        btnUpdateLauncher.setText("Buscando...");
        updateStatusLabel.setText("Conectando con el servidor...");

        new Thread(() -> {
            try {
                LauncherConfig config = ConfigService.getInstance().getLauncherConfig();
                UpdateService.UpdateCheckResult result = UpdateService.getInstance().checkForUpdates(
                        config.getInstalledModpackVersion(), 
                        config.getInstalledModsVersion()
                );

                if (result.launcherUpdate) {
                    Platform.runLater(() -> {
                        updateStatusLabel.setText("Nueva versión encontrada (" + result.remoteLauncherVersion + "), descargando...");
                        updateStatusLabel.setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold; -fx-font-size: 13px;");
                        btnUpdateLauncher.setText("Descargando...");
                    });

                    String url = resolveLauncherDownloadUrl();
                    if (url == null || url.isBlank()) {
                        throw new IllegalStateException("No se encontró URL de descarga del launcher");
                    }

                    String installerName = buildInstallerFileName(url);
                    Path installerPath = PlatformUtil.getLauncherDirectory().resolve("updates").resolve(installerName);
                    downloadService.downloadFile(url, installerPath);
                    launchInstaller(installerPath);

                    Platform.runLater(() -> {
                        updateStatusLabel.setText("Instalador ejecutado. Cerrando launcher...");
                        updateStatusLabel.setStyle("-fx-text-fill: #26d9a0; -fx-font-weight: bold; -fx-font-size: 13px;");
                        var stage = (javafx.stage.Stage) btnUpdateLauncher.getScene().getWindow();
                        if (stage != null) {
                            stage.close();
                        }
                    });
                } else {
                    Platform.runLater(() -> {
                        updateStatusLabel.setText("Launcher al día (v" + UpdateService.LAUNCHER_VERSION + ")");
                        updateStatusLabel.setStyle("-fx-text-fill: #26d9a0; -fx-font-weight: bold; -fx-font-size: 13px;");
                        AlertUtil.showInfo("Actualización", "Tu launcher está actualizado.");
                        btnUpdateLauncher.setDisable(false);
                        btnUpdateLauncher.setText("Buscar Actualización");
                    });
                }
            } catch (Exception e) {
                logger.error("Error en el botón de actualización", e);
                Platform.runLater(() -> {
                    updateStatusLabel.setText("Error al buscar actualizaciones");
                    updateStatusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 13px;");
                    btnUpdateLauncher.setDisable(false);
                    btnUpdateLauncher.setText("Reintentar");
                });
            }
        }).start();
    }

    private String resolveLauncherDownloadUrl() {
        try {
            String url = "https://raw.githubusercontent.com/camilo506/Launcher_Configuracion/main/Direccion-Descargas.json";
            String json = downloadService.downloadString(url);
            @SuppressWarnings("unchecked")
            Map<String, String> map = JsonUtil.fromJson(json, Map.class);
            return map.get("MultiMinecraft_url");
        } catch (Exception e) {
            logger.warn("No se pudo resolver URL de descarga del launcher", e);
            return null;
        }
    }

    private static String buildInstallerFileName(String url) {
        String cleanUrl = url;
        int queryIndex = cleanUrl.indexOf('?');
        if (queryIndex >= 0) {
            cleanUrl = cleanUrl.substring(0, queryIndex);
        }
        String fileName = Paths.get(cleanUrl).getFileName() != null
                ? Paths.get(cleanUrl).getFileName().toString()
                : "";
        return fileName.isBlank() ? "MultiMinecraft_Update.exe" : fileName;
    }

    private void launchInstaller(Path installerPath) throws Exception {
        String absolute = installerPath.toAbsolutePath().toString();
        switch (PlatformUtil.getOS()) {
            case WINDOWS -> new ProcessBuilder("cmd", "/c", "start", "", "\"" + absolute + "\"").start();
            case MACOS -> new ProcessBuilder("open", absolute).start();
            case LINUX -> new ProcessBuilder("xdg-open", absolute).start();
            default -> throw new IllegalStateException("Sistema operativo no soportado para auto-instalación");
        }
    }

    /**
     * Cierra la vista de configuración y restaura el contenido principal.
     */
    @FXML
    private void onClose() {
        logger.info("Cerrando vista de configuración acoplada");
        PrincipalController principal = PrincipalController.getInstance();
        if (principal != null) {
            principal.restoreMainContent();
        }
    }

}
