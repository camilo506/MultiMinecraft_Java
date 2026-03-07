package com.multiminecraft.launcher.util;

import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Utilidad para aplicar los estilos del proyecto a los diálogos Alert de JavaFX
 */
public class AlertUtil {

    private AlertUtil() {}

    /**
     * Aplica los estilos del proyecto al Alert dado.
     * Debe llamarse ANTES de alert.showAndWait().
     */
    public static void styleAlert(Alert alert) {
        DialogPane dialogPane = alert.getDialogPane();

        // Cargar hojas de estilo
        String mainCss = AlertUtil.class.getResource("/css/main.css") != null
                ? AlertUtil.class.getResource("/css/main.css").toExternalForm() : null;
        String darkCss = AlertUtil.class.getResource("/css/dark-theme.css") != null
                ? AlertUtil.class.getResource("/css/dark-theme.css").toExternalForm() : null;
        String alertCss = AlertUtil.class.getResource("/css/alert.css") != null
                ? AlertUtil.class.getResource("/css/alert.css").toExternalForm() : null;

        if (mainCss != null) dialogPane.getStylesheets().add(mainCss);
        if (darkCss != null) dialogPane.getStylesheets().add(darkCss);
        if (alertCss != null) dialogPane.getStylesheets().add(alertCss);

        dialogPane.getStyleClass().add("custom-alert");

        // Configurar stage transparente para bordes redondeados
        alert.initStyle(StageStyle.TRANSPARENT);
        alert.setOnShowing(e -> {
            Stage stage = (Stage) dialogPane.getScene().getWindow();
            if (stage != null && stage.getScene() != null) {
                stage.getScene().setFill(javafx.scene.paint.Color.TRANSPARENT);
            }
        });
    }
}
