package com.multiminecraft.launcher.controller;


import com.multiminecraft.launcher.model.Instance;
import com.multiminecraft.launcher.service.ConfigService;
import com.multiminecraft.launcher.service.InstanceService;
import com.multiminecraft.launcher.util.AlertUtil;
import com.multiminecraft.launcher.util.PlatformUtil;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Controlador para modificar instancias existentes
 */
public class EditInstanceController {

    private static final Logger logger = LoggerFactory.getLogger(EditInstanceController.class);

    @FXML private TextField nameField;
    @FXML private TextField playerNameField;
    @FXML private Label versionLabel;
    @FXML private Label loaderLabel;
    @FXML private Slider memorySlider;
    @FXML private Label memoryLabel;
    @FXML private ImageView iconPreview;
    @FXML private Button saveButton;

    // Barra de título personalizada
    @FXML private HBox titleBar;
    @FXML private Button minimizeBtn;
    @FXML private Button closeBtn;
    private double xOffset = 0;
    private double yOffset = 0;

    private Instance instance;
    private String originalName;
    private String selectedIconName;
    private InstanceService instanceService;
    private boolean saved = false;

    @FXML
    public void initialize() {
        logger.info("Inicializando vista de editar instancia");

        instanceService = new InstanceService();

        // Configurar arrastre de ventana por la barra de título
        if (titleBar != null) {
            titleBar.setOnMousePressed(event -> {
                Stage stage = (Stage) titleBar.getScene().getWindow();
                xOffset = stage.getX() - event.getScreenX();
                yOffset = stage.getY() - event.getScreenY();
            });
            titleBar.setOnMouseDragged(event -> {
                Stage stage = (Stage) titleBar.getScene().getWindow();
                stage.setX(event.getScreenX() + xOffset);
                stage.setY(event.getScreenY() + yOffset);
            });
        }

        // Configurar slider de memoria
        memorySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int memory = newVal.intValue();
            memoryLabel.setText(memory + " GB");
        });
    }

    /**
     * Establece la instancia a editar y carga los datos actuales
     */
    public void setInstance(Instance instance) {
        this.instance = instance;
        this.originalName = instance.getName();

        // Cargar datos actuales en el formulario
        nameField.setText(instance.getName());
        playerNameField.setText(instance.getPlayerName() != null ? instance.getPlayerName() : "");
        versionLabel.setText("Minecraft " + instance.getVersion());
        loaderLabel.setText(instance.getLoader() != null ? instance.getLoader().getDisplayName() : "Vanilla");

        // Cargar memoria
        String memory = instance.getMemory();
        if (memory != null && memory.endsWith("G")) {
            try {
                int memoryValue = Integer.parseInt(memory.replace("G", ""));
                memorySlider.setValue(memoryValue);
                memoryLabel.setText(memoryValue + " GB");
            } catch (NumberFormatException e) {
                memorySlider.setValue(4);
                memoryLabel.setText("4 GB");
            }
        } else {
            memorySlider.setValue(4);
            memoryLabel.setText("4 GB");
        }

        // Cargar icono actual
        selectedIconName = instance.getIcon();
        loadIconPreview(selectedIconName);

        logger.info("Datos de instancia cargados: {}", instance.getName());
    }

    @FXML
    private void onBrowseIcon() {
        try {
            logger.debug("Abriendo ventana de selección de iconos");

            // Cargar la vista de selección de iconos
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/SelectIconView.fxml"));
            Parent selectIconView = loader.load();

            // Crear una nueva ventana modal
            Stage selectIconStage = new Stage();
            selectIconStage.setTitle("Seleccionar Icono");
            selectIconStage.initModality(Modality.WINDOW_MODAL);

            // Obtener el Stage de la ventana actual
            Stage currentStage = (Stage) iconPreview.getScene().getWindow();
            selectIconStage.initOwner(currentStage);

            Scene scene = new Scene(selectIconView, 700, 500);
            // Aplicar estilos
            scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource("/css/dark-theme.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource("/css/select-icon.css").toExternalForm());

            selectIconStage.setScene(scene);
            selectIconStage.setResizable(false);

            // Mostrar la ventana modal y esperar
            selectIconStage.showAndWait();

            // Obtener el icono seleccionado
            Object selectedIcon = selectIconStage.getUserData();
            if (selectedIcon != null && selectedIcon instanceof String) {
                selectedIconName = (String) selectedIcon;
                loadIconPreview(selectedIconName);
                logger.info("Icono seleccionado: {}", selectedIconName);
            }

        } catch (Exception e) {
            logger.error("Error al abrir ventana de selección de iconos", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("No se pudo abrir la ventana de selección de iconos");
            alert.setContentText(e.getMessage());
            AlertUtil.styleAlert(alert);
            alert.showAndWait();
        }
    }

    @FXML
    private void onSave() {
        // Validar nombre
        String newName = nameField.getText().trim();
        if (newName.isEmpty()) {
            showError("El nombre de la instancia no puede estar vacío");
            return;
        }

        // Si el nombre cambió, verificar que no exista otra instancia con ese nombre
        boolean nameChanged = !newName.equals(originalName);
        if (nameChanged && instanceService.instanceExists(newName)) {
            showError("Ya existe una instancia con el nombre \"" + newName + "\"");
            return;
        }

        try {
            // Actualizar datos de la instancia
            String playerName = playerNameField.getText() != null ? playerNameField.getText().trim() : "";
            if (!playerName.isEmpty()) {
                instance.setPlayerName(playerName);
            }

            instance.setMemory(((int) memorySlider.getValue()) + "G");

            // Actualizar icono si cambió
            if (selectedIconName != null && !selectedIconName.isEmpty()) {
                instance.setIcon(selectedIconName);
            }

            if (nameChanged) {
                // Si el nombre cambió, renombrar el directorio de la instancia
                renameInstanceDirectory(originalName, newName);
                instance.setName(newName);
            }

            // Guardar configuración actualizada
            ConfigService.getInstance().saveInstanceConfig(instance);

            logger.info("Instancia modificada exitosamente: {} -> {}", originalName, newName);
            saved = true;

            // Mostrar mensaje de éxito
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Instancia modificada");
            alert.setHeaderText(null);
            alert.setContentText("La instancia \"" + newName + "\" ha sido modificada exitosamente");
            AlertUtil.styleAlert(alert);
            alert.showAndWait();

            // Cerrar la ventana
            Stage stage = (Stage) saveButton.getScene().getWindow();
            stage.close();

        } catch (Exception e) {
            logger.error("Error al guardar cambios de la instancia", e);
            showError("No se pudieron guardar los cambios: " + e.getMessage());
        }
    }

    /**
     * Renombra el directorio de una instancia
     */
    private void renameInstanceDirectory(String oldName, String newName) throws IOException {
        Path instancesDir = PlatformUtil.getInstancesDirectory();
        Path oldDir = instancesDir.resolve(oldName);
        Path newDir = instancesDir.resolve(newName);

        if (!Files.exists(oldDir)) {
            throw new IOException("El directorio de la instancia no existe: " + oldDir);
        }

        if (Files.exists(newDir)) {
            throw new IOException("Ya existe un directorio con el nuevo nombre: " + newDir);
        }

        Files.move(oldDir, newDir, StandardCopyOption.ATOMIC_MOVE);
        logger.info("Directorio de instancia renombrado: {} -> {}", oldDir, newDir);
    }

    @FXML
    private void onCancel() {
        Stage stage = (Stage) saveButton.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void onMinimizeClicked() {
        Stage stage = (Stage) titleBar.getScene().getWindow();
        stage.setIconified(true);
    }

    @FXML
    private void onCloseClicked() {
        Stage stage = (Stage) titleBar.getScene().getWindow();
        stage.close();
    }

    /**
     * Indica si se guardaron cambios
     */
    public boolean isSaved() {
        return saved;
    }

    /**
     * Muestra un error
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Validación");
        alert.setHeaderText(null);
        alert.setContentText(message);
        AlertUtil.styleAlert(alert);
        alert.showAndWait();
    }

    /**
     * Carga un icono en la vista previa
     */
    private void loadIconPreview(String iconName) {
        if (iconName == null || iconName.isEmpty()) {
            iconPreview.setImage(null);
            return;
        }

        try {
            // Primero intentar desde recursos
            String resourcePath = "/icons/" + iconName;
            java.io.InputStream iconStream = getClass().getResourceAsStream(resourcePath);
            if (iconStream != null) {
                iconPreview.setImage(new Image(iconStream));
                return;
            }

            // Si no se encuentra, intentar como ruta absoluta
            File iconFile = new File(iconName);
            if (iconFile.exists()) {
                iconPreview.setImage(new Image(iconFile.toURI().toString()));
                return;
            }

            // Si no se encuentra nada, dejar vacío
            iconPreview.setImage(null);
        } catch (Exception e) {
            logger.warn("No se pudo cargar el icono: {}", iconName, e);
            iconPreview.setImage(null);
        }
    }
}
