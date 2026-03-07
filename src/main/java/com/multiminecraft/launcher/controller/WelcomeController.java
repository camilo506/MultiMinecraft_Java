package com.multiminecraft.launcher.controller;

import com.multiminecraft.launcher.App;
import com.multiminecraft.launcher.service.ConfigService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WelcomeController {

    private static final Logger logger = LoggerFactory.getLogger(WelcomeController.class);

    @FXML private TextField playerNameField;
    @FXML private Label errorLabel;
    @FXML private Button continueButton;

    @FXML
    public void initialize() {
        // Permitir continuar con Enter
        playerNameField.setOnAction(e -> onContinue());
    }

    @FXML
    private void onContinue() {
        String name = playerNameField.getText() != null ? playerNameField.getText().trim() : "";

        if (name.isEmpty()) {
            showError("Debes ingresar un nombre de jugador");
            return;
        }

        if (name.length() < 3 || name.length() > 16) {
            showError("El nombre debe tener entre 3 y 16 caracteres");
            return;
        }

        if (!name.matches("[a-zA-Z0-9_]+")) {
            showError("Solo letras, números y guion bajo (_)");
            return;
        }

        // Guardar nombre en config global
        ConfigService configService = ConfigService.getInstance();
        configService.getLauncherConfig().setPlayerName(name);
        configService.saveLauncherConfig();
        logger.info("Nombre de jugador guardado: {}", name);

        // Navegar a la ventana principal
        try {
            App.setRoot("MainWindow");
        } catch (Exception e) {
            logger.error("Error al cargar ventana principal", e);
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}
