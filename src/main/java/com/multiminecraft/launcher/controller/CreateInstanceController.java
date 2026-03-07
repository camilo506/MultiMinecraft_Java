package com.multiminecraft.launcher.controller;

import com.multiminecraft.launcher.App;
import com.multiminecraft.launcher.model.Instance;
import com.multiminecraft.launcher.model.LoaderType;
import com.multiminecraft.launcher.model.MinecraftVersion;
import com.multiminecraft.launcher.service.InstanceService;
import com.multiminecraft.launcher.service.MojangService;
import com.multiminecraft.launcher.service.ConfigService;
import com.multiminecraft.launcher.util.AlertUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Controlador para crear nuevas instancias
 */
public class CreateInstanceController {
    
    private static final Logger logger = LoggerFactory.getLogger(CreateInstanceController.class);
    
    @FXML private TextField nameField;
    @FXML private TextField playerNameField;
    @FXML private ComboBox<String> versionComboBox;
    @FXML private ComboBox<LoaderType> loaderTypeComboBox;
    @FXML private Slider memorySlider;
    @FXML private Label memoryLabel;
    @FXML private ImageView iconPreview;
    
    private String selectedIconName;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private VBox progressContainer;
    @FXML private Label progressLabel;
    @FXML private Label progressPercentLabel;
    @FXML private ProgressIndicator progressBar;
    @FXML private Button createButton;
    
    // Barra de título personalizada
    @FXML private HBox titleBar;
    @FXML private Button minimizeBtn;
    @FXML private Button closeBtn;
    private double xOffset = 0;
    private double yOffset = 0;
    
    private MojangService mojangService;
    private InstanceService instanceService;
    private List<MinecraftVersion> availableVersions;
    
    @FXML
    public void initialize() {
        logger.info("Inicializando vista de crear instancia");
        
        mojangService = new MojangService();
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
        
        // Configurar loader types
        loaderTypeComboBox.setItems(FXCollections.observableArrayList(LoaderType.values()));
        loaderTypeComboBox.setValue(LoaderType.VANILLA);
        
        // Configurar slider de memoria
        memorySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int memory = newVal.intValue();
            memoryLabel.setText(memory + " GB");
        });
        
        // Inicializar icono por defecto
        loadDefaultIcon();
        
        // Precargar nombre de jugador desde la configuración global
        String globalPlayerName = ConfigService.getInstance().getLauncherConfig().getPlayerName();
        if (globalPlayerName != null && !globalPlayerName.isEmpty()) {
            playerNameField.setText(globalPlayerName);
        }
        
        // Cargar versiones
        loadVersions();
    }
    
    /**
     * Carga las versiones de Minecraft disponibles
     */
    private void loadVersions() {
        loadingIndicator.setVisible(true);
        versionComboBox.setDisable(true);
        
        new Thread(() -> {
            try {
                availableVersions = mojangService.getReleaseVersions();
                
                Platform.runLater(() -> {
                    List<String> versionIds = availableVersions.stream()
                            .map(MinecraftVersion::getId)
                            .toList();
                    
                    versionComboBox.setItems(FXCollections.observableArrayList(versionIds));
                    
                    if (!versionIds.isEmpty()) {
                        versionComboBox.setValue(versionIds.get(0)); // Seleccionar la más reciente
                    }
                    
                    loadingIndicator.setVisible(false);
                    versionComboBox.setDisable(false);
                    
                    logger.info("Cargadas {} versiones de Minecraft", versionIds.size());
                });
                
            } catch (Exception e) {
                logger.error("Error al cargar versiones", e);
                
                Platform.runLater(() -> {
                    loadingIndicator.setVisible(false);
                    versionComboBox.setDisable(false);
                    
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText("No se pudieron cargar las versiones");
                    alert.setContentText("Verifica tu conexión a internet e intenta nuevamente.");
                    AlertUtil.styleAlert(alert);
                    alert.showAndWait();
                });
            }
        }).start();
    }
    
    @FXML
    private void onRefreshVersions() {
        logger.debug("Refrescando versiones");
        loadVersions();
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
            selectIconStage.initOwner(currentStage != null ? currentStage : App.getPrimaryStage());
            
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
    private void onCreate() {
        // Validar campos
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            showError("El nombre de la instancia no puede estar vacío");
            return;
        }
        
        String version = versionComboBox.getValue();
        if (version == null) {
            showError("Debes seleccionar una versión de Minecraft");
            return;
        }
        
        LoaderType loaderType = loaderTypeComboBox.getValue();
        if (loaderType == null) {
            loaderType = LoaderType.VANILLA;
            logger.warn("LoaderType es null, usando VANILLA por defecto");
        }
        
        logger.info("Creando instancia - Nombre: {}, Versión: {}, Loader: {}", name, version, loaderType);
        
        // Verificar si ya existe
        if (instanceService.instanceExists(name)) {
            showError("Ya existe una instancia con ese nombre");
            return;
        }
        
        // Crear instancia
        Instance instance = new Instance(name, version, loaderType);
        logger.debug("Instancia creada con loader: {}", instance.getLoader());
        instance.setMemory(((int) memorySlider.getValue()) + "G");
        
        // Establecer nombre del jugador
        String playerName = playerNameField.getText() != null ? playerNameField.getText().trim() : "";
        if (!playerName.isEmpty()) {
            instance.setPlayerName(playerName);
        }
        
        // Establecer icono si se seleccionó uno
        if (selectedIconName != null && !selectedIconName.isEmpty()) {
            instance.setIcon(selectedIconName);
        }
        
        // Deshabilitar formulario
        setFormEnabled(false);
        progressContainer.setVisible(true);
        progressContainer.setManaged(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        progressPercentLabel.setText("0%");
        
        // Crear en thread separado
        new Thread(() -> {
            try {
                instanceService.createInstance(instance, status -> {
                    Platform.runLater(() -> progressLabel.setText(status));
                }, progress -> {
                    Platform.runLater(() -> {
                        int percent = (int) Math.round(progress * 100);
                        progressPercentLabel.setText(percent + "%");
                    });
                });
                
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Instancia creada");
                    alert.setHeaderText(null);
                    alert.setContentText("La instancia \"" + name + "\" ha sido creada exitosamente");
                    AlertUtil.styleAlert(alert);
                    alert.showAndWait();
                    
                    // Cerrar la ventana modal
                    Stage stage = (Stage) createButton.getScene().getWindow();
                    if (stage != null) {
                        stage.close();
                    } else {
                        // Si no es una ventana modal, volver a la vista de instancias
                        try {
                            App.setRoot("InstancesView");
                        } catch (Exception e) {
                            logger.error("Error al cambiar vista", e);
                        }
                    }
                });
                
            } catch (Exception e) {
                logger.error("Error al crear instancia", e);
                
                Platform.runLater(() -> {
                    setFormEnabled(true);
                    progressContainer.setVisible(false);
                    progressContainer.setManaged(false);
                    
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText("No se pudo crear la instancia");
                    alert.setContentText(e.getMessage());
                    AlertUtil.styleAlert(alert);
                    alert.showAndWait();
                });
            }
        }).start();
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
    
    @FXML
    private void onCancel() {
        // Cerrar la ventana modal
        Stage stage = (Stage) createButton.getScene().getWindow();
        if (stage != null) {
            stage.close();
        } else {
            // Si no es una ventana modal, volver a la vista de instancias
            try {
                App.setRoot("InstancesView");
            } catch (Exception e) {
                logger.error("Error al cambiar vista", e);
            }
        }
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
     * Habilita/deshabilita el formulario
     */
    private void setFormEnabled(boolean enabled) {
        nameField.setDisable(!enabled);
        versionComboBox.setDisable(!enabled);
        loaderTypeComboBox.setDisable(!enabled);
        memorySlider.setDisable(!enabled);
        createButton.setDisable(!enabled);
    }
    
    /**
     * Carga un icono por defecto en la vista previa
     */
    private void loadDefaultIcon() {
        try {
            // Intentar cargar un icono por defecto
            String defaultIcon = "art-Crafting_Table.png";
            String resourcePath = "/icons/" + defaultIcon;
            java.io.InputStream iconStream = getClass().getResourceAsStream(resourcePath);
            if (iconStream != null) {
                Image defaultImage = new Image(iconStream);
                iconPreview.setImage(defaultImage);
                selectedIconName = defaultIcon;
            } else {
                // Si no hay icono por defecto, crear uno simple
                createDefaultIconPreview();
            }
        } catch (Exception e) {
            logger.warn("No se pudo cargar el icono por defecto", e);
            createDefaultIconPreview();
        }
    }
    
    /**
     * Carga un icono en la vista previa
     */
    private void loadIconPreview(String iconName) {
        try {
            String resourcePath = "/icons/" + iconName;
            java.io.InputStream iconStream = getClass().getResourceAsStream(resourcePath);
            if (iconStream != null) {
                Image iconImage = new Image(iconStream);
                iconPreview.setImage(iconImage);
            } else {
                // Si no se encuentra, intentar como ruta absoluta
                java.io.File iconFile = new java.io.File(iconName);
                if (iconFile.exists()) {
                    iconPreview.setImage(new Image(iconFile.toURI().toString()));
                } else {
                    loadDefaultIcon();
                }
            }
        } catch (Exception e) {
            logger.warn("No se pudo cargar el icono: {}", iconName, e);
            loadDefaultIcon();
        }
    }
    
    /**
     * Crea un icono por defecto simple para la vista previa
     */
    private void createDefaultIconPreview() {
        // Usar el método del MainController para crear un icono por defecto
        // Por ahora, simplemente dejamos el ImageView vacío o con un placeholder
        // En una implementación más completa, podrías crear un Canvas y generar una imagen
        iconPreview.setImage(null);
    }
}
