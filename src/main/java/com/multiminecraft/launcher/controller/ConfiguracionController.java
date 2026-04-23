package com.multiminecraft.launcher.controller;

import com.multiminecraft.launcher.App;
import com.multiminecraft.launcher.model.LauncherConfig;
import com.multiminecraft.launcher.service.ConfigService;
import com.multiminecraft.launcher.util.AlertUtil;
import com.multiminecraft.launcher.service.UpdateService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controlador de la vista de Configuración del launcher.
 * Permite al usuario editar: nombre del jugador, 
 * memoria RAM, tema e idioma.
 */
public class ConfiguracionController {

    private static final Logger logger = LoggerFactory.getLogger(ConfiguracionController.class);

    @FXML private TextField playerNameField;
    @FXML private ComboBox<String> memoryComboBox;
    @FXML private ComboBox<String> themeComboBox;
    @FXML private ComboBox<String> languageComboBox;
    @FXML private Label versionLabel;
    @FXML private Label updateStatusLabel;
    @FXML private Button btnUpdateLauncher;
    @FXML private Label websiteLink;
    @FXML private Label statusLabel;

    private static final String[] MEMORY_OPTIONS = {
        "1G", "2G", "3G", "4G", "6G", "8G", "10G", "12G", "16G"
    };

    private static final String[] THEME_OPTIONS_DISPLAY = { "Oscuro", "Claro" };
    private static final String[] THEME_VALUES = { "dark", "light" };

    private static final String[] LANGUAGE_OPTIONS_DISPLAY = { "Español", "English" };
    private static final String[] LANGUAGE_VALUES = { "es", "en" };

    @FXML
    public void initialize() {
        logger.info("Inicializando vista de Configuración");
        memoryComboBox.setItems(FXCollections.observableArrayList(MEMORY_OPTIONS));
        themeComboBox.setItems(FXCollections.observableArrayList(THEME_OPTIONS_DISPLAY));
        languageComboBox.setItems(FXCollections.observableArrayList(LANGUAGE_OPTIONS_DISPLAY));
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

            String theme = config.getTheme();
            int themeIndex = findIndex(THEME_VALUES, theme != null ? theme : "dark");
            themeComboBox.getSelectionModel().select(themeIndex >= 0 ? themeIndex : 0);

            String language = config.getLanguage();
            int langIndex = findIndex(LANGUAGE_VALUES, language != null ? language : "es");
            languageComboBox.getSelectionModel().select(langIndex >= 0 ? langIndex : 0);

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

            String playerName = playerNameField.getText().trim();
            if (playerName.isEmpty()) {
                statusLabel.setText("⚠ El nombre no puede estar vacío");
                statusLabel.getStyleClass().setAll("config-status-error");
                return;
            }

            config.setPlayerName(playerName);

            String selectedMemory = memoryComboBox.getValue();
            if (selectedMemory != null) {
                config.setDefaultMemory(selectedMemory);
            }

            int themeIndex = themeComboBox.getSelectionModel().getSelectedIndex();
            if (themeIndex >= 0 && themeIndex < THEME_VALUES.length) {
                String themeValue = THEME_VALUES[themeIndex];
                config.setTheme(themeValue);
                App.applyTheme(themeValue);
            }

            int langIndex = languageComboBox.getSelectionModel().getSelectedIndex();
            if (langIndex >= 0 && langIndex < LANGUAGE_VALUES.length) {
                config.setLanguage(LANGUAGE_VALUES[langIndex]);
            }

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
            themeComboBox.getSelectionModel().select(0);
            languageComboBox.getSelectionModel().select(0);
            statusLabel.setText("⟳ Valores restablecidos (sin guardar)");
            statusLabel.getStyleClass().setAll("config-status-saved");
        }
    }

    @FXML
    private void onWebsiteLinkClicked() {
        App.openWebPage("https://monkeystudio.netlify.app/");
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

                javafx.application.Platform.runLater(() -> {
                    btnUpdateLauncher.setDisable(false);
                    btnUpdateLauncher.setText("Buscar Actualización");

                    if (result.launcherUpdate) {
                        updateStatusLabel.setText("¡Nueva versión disponible! (" + result.remoteLauncherVersion + ")");
                        updateStatusLabel.setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold; -fx-font-size: 13px;");
                        AlertUtil.showInfo("Actualización Disponible", 
                            "Hay una nueva versión del launcher disponible (" + result.remoteLauncherVersion + ").\n" +
                            "Por favor, descárgala del sitio oficial.");
                    } else {
                        updateStatusLabel.setText("Launcher al día (v" + UpdateService.LAUNCHER_VERSION + ")");
                        updateStatusLabel.setStyle("-fx-text-fill: #26d9a0; -fx-font-weight: bold; -fx-font-size: 13px;");
                        AlertUtil.showInfo("Actualización", "Tu launcher está actualizado.");
                    }
                });
            } catch (Exception e) {
                logger.error("Error en el botón de actualización", e);
                javafx.application.Platform.runLater(() -> {
                    updateStatusLabel.setText("Error al buscar actualizaciones");
                    updateStatusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 13px;");
                    btnUpdateLauncher.setDisable(false);
                    btnUpdateLauncher.setText("Reintentar");
                });
            }
        }).start();
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

    private int findIndex(String[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(value)) return i;
        }
        return -1;
    }
}
