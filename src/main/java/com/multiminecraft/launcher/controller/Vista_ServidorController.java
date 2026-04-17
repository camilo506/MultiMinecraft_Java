package com.multiminecraft.launcher.controller;

import javafx.fxml.FXML;
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
}
