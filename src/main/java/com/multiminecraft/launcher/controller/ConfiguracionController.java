package com.multiminecraft.launcher.controller;

import com.multiminecraft.launcher.App;
import com.multiminecraft.launcher.model.LauncherConfig;
import com.multiminecraft.launcher.service.ConfigService;
import com.multiminecraft.launcher.util.AlertUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Controlador de la vista de Configuración del launcher.
 * Permite al usuario editar: nombre del jugador, ruta de Java,
 * memoria RAM, directorio de instancias, tema e idioma.
 */
public class ConfiguracionController {

    private static final Logger logger = LoggerFactory.getLogger(ConfiguracionController.class);

    @FXML private TextField playerNameField;
    @FXML private TextField javaPathField;
    @FXML private ComboBox<String> memoryComboBox;
    @FXML private TextField instancesPathField;
    @FXML private ComboBox<String> themeComboBox;
    @FXML private ComboBox<String> languageComboBox;
    @FXML private Label versionLabel;
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

            String javaPath = config.getDefaultJavaPath();
            if (javaPath != null && !javaPath.isEmpty()) {
                javaPathField.setText(javaPath);
            }

            String memory = config.getDefaultMemory();
            if (memory != null && !memory.isEmpty()) {
                memoryComboBox.setValue(memory);
            } else {
                memoryComboBox.setValue("2G");
            }

            String instancesPath = config.getInstancesPath();
            if (instancesPath != null && !instancesPath.isEmpty()) {
                instancesPathField.setText(instancesPath);
            }

            String theme = config.getTheme();
            int themeIndex = findIndex(THEME_VALUES, theme != null ? theme : "dark");
            themeComboBox.getSelectionModel().select(themeIndex >= 0 ? themeIndex : 0);

            String language = config.getLanguage();
            int langIndex = findIndex(LANGUAGE_VALUES, language != null ? language : "es");
            languageComboBox.getSelectionModel().select(langIndex >= 0 ? langIndex : 0);

            versionLabel.setText("1.0.0");
            statusLabel.setText("");

            logger.info("Configuración actual cargada en el formulario");
        } catch (Exception e) {
            logger.error("Error al cargar la configuración actual", e);
        }
    }

    @FXML
    private void onBrowseJavaClicked() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar ejecutable de Java");
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Ejecutable Java", "java.exe", "javaw.exe"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*")
            );
        }
        String currentPath = javaPathField.getText();
        if (currentPath != null && !currentPath.isEmpty()) {
            File currentFile = new File(currentPath);
            if (currentFile.getParentFile() != null && currentFile.getParentFile().exists()) {
                fileChooser.setInitialDirectory(currentFile.getParentFile());
            }
        }
        Stage stage = (Stage) javaPathField.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
            javaPathField.setText(selectedFile.getAbsolutePath());
        }
    }

    @FXML
    private void onBrowseInstancesClicked() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Seleccionar directorio de instancias");
        String currentPath = instancesPathField.getText();
        if (currentPath != null && !currentPath.isEmpty()) {
            File currentDir = new File(currentPath);
            if (currentDir.exists() && currentDir.isDirectory()) {
                directoryChooser.setInitialDirectory(currentDir);
            }
        }
        Stage stage = (Stage) instancesPathField.getScene().getWindow();
        File selectedDir = directoryChooser.showDialog(stage);
        if (selectedDir != null) {
            instancesPathField.setText(selectedDir.getAbsolutePath());
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
            config.setDefaultJavaPath(javaPathField.getText().trim());

            String selectedMemory = memoryComboBox.getValue();
            if (selectedMemory != null) {
                config.setDefaultMemory(selectedMemory);
            }

            String instancesPath = instancesPathField.getText().trim();
            if (!instancesPath.isEmpty()) {
                config.setInstancesPath(instancesPath);
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
            javaPathField.setText("");
            memoryComboBox.setValue("2G");
            instancesPathField.setText("Instancias");
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

    private int findIndex(String[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(value)) return i;
        }
        return -1;
    }
}
