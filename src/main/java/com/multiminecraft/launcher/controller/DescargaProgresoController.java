package com.multiminecraft.launcher.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Controlador de la vista modal de progreso de descarga.
 *
 * <p>Uso desde otro controlador:</p>
 * <pre>
 *   DescargaProgresoController ctrl = DescargaProgresoController.show(ownerStage);
 *   ctrl.setProgress(0.48, "Descargando librerías...", "Fetching core deps...");
 *   ctrl.setSpeed(12.4);     // MB/s
 *   ctrl.setTimeRemaining(42); // segundos
 *   ctrl.close();
 * </pre>
 */
public class DescargaProgresoController {

    private static final Logger logger = LoggerFactory.getLogger(DescargaProgresoController.class);

    // ── FXML ──────────────────────────────────────────────────────────────
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label percentLabel;
    @FXML private Label statusTitleLabel;
    @FXML private Label statusDetailLabel;
    @FXML private Label speedLabel;
    @FXML private Label timeLabel;
    @FXML private Button cancelButton;

    // ── Estado interno ─────────────────────────────────────────────────────
    private volatile boolean cancelled = false;
    private Runnable onCancelCallback;
    private Stage ownStage;

    // Ventana deslizante para calcular velocidad promedio de descarga
    private final Deque<long[]> byteSamples = new ArrayDeque<>();

    // ── Inicialización ─────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        progressIndicator.setProgress(0.0);
        speedLabel.setText("0.0 MB/s");
        timeLabel.setText("Calculando...");
    }

    /** Referencia al Stage propio (se establece desde el código que abre la ventana). */
    public void setStage(Stage stage) {
        this.ownStage = stage;
    }

    /** Callback que se invoca cuando el usuario pulsa "Cancel Download". */
    public void setOnCancelCallback(Runnable callback) {
        this.onCancelCallback = callback;
    }

    /** Devuelve la referencia al ProgressIndicator (lectura de progreso actual). */
    public double getCurrentProgress() {
        return progressIndicator != null ? progressIndicator.getProgress() : 0.0;
    }

    /**
     * Actualiza el progreso y los textos de estado.
     *
     * @param progress valor entre 0.0 y 1.0
     * @param title    texto principal (ej: "Descargando librerías...")
     * @param detail   descripción secundaria
     */
    public void setProgress(double progress, String title, String detail) {
        Platform.runLater(() -> {
            double clamped = Math.max(0.0, Math.min(1.0, progress));
            progressIndicator.setProgress(clamped);
            int percent = (int) Math.round(clamped * 100);
            percentLabel.setText(String.valueOf(percent));

            if (title != null && !title.isBlank()) {
                statusTitleLabel.setText(title);
            }
            if (detail != null && !detail.isBlank()) {
                statusDetailLabel.setText(detail);
            }
        });
    }

    /**
     * Actualiza solo el porcentaje sin cambiar los textos.
     */
    public void setProgress(double progress) {
        setProgress(progress, null, null);
    }

    /**
     * Establece la velocidad de descarga actual.
     *
     * @param mbPerSecond velocidad en MB/s (use 0 para mostrar "—")
     */
    public void setSpeed(double mbPerSecond) {
        Platform.runLater(() -> {
            if (mbPerSecond <= 0) {
                speedLabel.setText("— MB/s");
            } else {
                speedLabel.setText(String.format("%.1f MB/s", mbPerSecond));
            }
        });
    }

    /**
     * Establece el tiempo restante estimado.
     *
     * @param seconds segundos restantes (use -1 para "Calculando...")
     */
    public void setTimeRemaining(long seconds) {
        Platform.runLater(() -> {
            if (seconds < 0) {
                timeLabel.setText("Calculando...");
            } else if (seconds == 0) {
                timeLabel.setText("Completando...");
            } else if (seconds < 60) {
                timeLabel.setText("~" + seconds + " Segundos");
            } else {
                long mins = seconds / 60;
                long secs = seconds % 60;
                timeLabel.setText(String.format("~%dm %02ds", mins, secs));
            }
        });
    }

    /**
     * Actualiza velocidad y tiempo restante a partir de bytes descargados.
     * Llama este método periódicamente para que el cálculo sea automático.
     *
     * @param downloaded bytes descargados hasta ahora
     * @param total      bytes totales a descargar
     */
    public void updateDownloadStats(long downloaded, long total) {
        long now = System.currentTimeMillis();

        // Agregar muestra
        byteSamples.addLast(new long[]{now, downloaded});
        // Mantener ventana de ~3 segundos
        while (byteSamples.size() > 1) {
            long[] oldest = byteSamples.peekFirst();
            if (now - oldest[0] > 3000) {
                byteSamples.pollFirst();
            } else {
                break;
            }
        }

        if (byteSamples.size() >= 2) {
            long[] oldest = byteSamples.peekFirst();
            long[] newest = byteSamples.peekLast();
            long deltaTime = newest[0] - oldest[0];
            long deltaBytes = newest[1] - oldest[1];

            if (deltaTime > 0) {
                double mbps = (deltaBytes / 1_048_576.0) / (deltaTime / 1000.0);
                setSpeed(mbps);

                long remaining = total - downloaded;
                if (mbps > 0) {
                    long remainingSeconds = (long) (remaining / (mbps * 1_048_576.0));
                    setTimeRemaining(remainingSeconds);
                }
            }
        }
    }

    /**
     * Cierra la ventana de progreso desde otro hilo.
     */
    public void close() {
        Platform.runLater(() -> {
            if (ownStage != null) {
                ownStage.close();
            }
        });
    }

    /**
     * Deshabilita el botón de cancelar (cuando la descarga ya no puede cancelarse).
     */
    public void disableCancel() {
        Platform.runLater(() -> cancelButton.setDisable(true));
    }

    /** Indica si el usuario solicitó cancelar. */
    public boolean isCancelled() {
        return cancelled;
    }

    // ── Acciones FXML ──────────────────────────────────────────────────────

    @FXML
    private void onCancelClicked() {
        cancelled = true;
        cancelButton.setDisable(true);
        cancelButton.setText("Cancelando...");
        logger.info("Usuario solicitó cancelar la descarga");

        if (onCancelCallback != null) {
            new Thread(onCancelCallback).start();
        }
    }

    // ── Factory: abrir la ventana ──────────────────────────────────────────

    /**
     * Crea y muestra la ventana de progreso de descarga.
     * Retorna el controlador para que puedas actualizar el progreso.
     *
     * @param owner Stage padre (puede ser null)
     * @return controlador listo para recibir actualizaciones
     */
    public static DescargaProgresoController show(javafx.stage.Stage owner) {
        Logger log = LoggerFactory.getLogger(DescargaProgresoController.class);
        log.info("Iniciando apertura de la ventana de progreso...");
        
        try {
            java.net.URL fxmlUrl =
                DescargaProgresoController.class.getResource("/fxml/DescargaProgreso.fxml");
            if (fxmlUrl == null) {
                log.error("No se encontró /fxml/DescargaProgreso.fxml en el classpath");
                return null;
            }

            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(fxmlUrl);
            javafx.scene.Parent root = loader.load();

            DescargaProgresoController controller = loader.getController();
            if (controller == null) {
                log.error("No se pudo obtener el controlador del FXMLLoader");
                return null;
            }

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.initStyle(javafx.stage.StageStyle.TRANSPARENT);

            // Intentar con WINDOW_MODAL si el owner existe, si no APPLICATION_MODAL
            if (owner != null && owner.isShowing()) {
                stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
                stage.initOwner(owner);
            } else {
                stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            }

            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);

            // Cargar CSS
            try {
                java.net.URL darkCss = DescargaProgresoController.class.getResource("/css/dark-theme.css");
                if (darkCss != null) scene.getStylesheets().add(darkCss.toExternalForm());
                
                java.net.URL progCss = DescargaProgresoController.class.getResource("/css/descarga-progreso.css");
                if (progCss != null) scene.getStylesheets().add(progCss.toExternalForm());
            } catch (Exception e) {
                log.warn("Error al cargar hojas de estilo: {}", e.getMessage());
            }

            stage.setScene(scene);
            controller.setStage(stage);
            
            // Forzar estar siempre encima
            stage.setAlwaysOnTop(true);

            // Posicionar ANTES de mostrar si es posible (reduce parpadeo)
            if (owner != null && owner.isShowing()) {
                double x = owner.getX() + (owner.getWidth() - 400) / 2.0;
                double y = owner.getY() + (owner.getHeight() - 460) / 2.0;
                stage.setX(x);
                stage.setY(y);
            } else {
                stage.centerOnScreen();
            }

            // Mostrar
            stage.show();
            stage.toFront();
            
            log.info("Ventana de progreso mostrada correctamente");
            return controller;

        } catch (Exception e) {
            log.error("Error crítico al abrir DescargaProgreso: {}", e.getMessage(), e);
            e.printStackTrace();
            return null;
        }
    }
}
