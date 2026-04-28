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
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Controlador para crear nuevas instancias
 */
public class CrearEditarController {
    
    private static final Logger logger = LoggerFactory.getLogger(CrearEditarController.class);
    
    @FXML private TextField nameField;
    @FXML private TextField playerNameField;
    @FXML private ComboBox<String> versionComboBox;
    @FXML private ComboBox<LoaderType> loaderTypeComboBox;
    @FXML private Slider memorySlider;
    @FXML private Label titleLabel;
    @FXML private Label memoryLabel;
    @FXML private ImageView iconPreview;
    @FXML private Label iconPlaceholder;
    
    // Skin
    @FXML private ImageView skinPreview;
    @FXML private Label skinPlaceholder;
    @FXML private Label skinNameLabel;
    @FXML private Button btnRemoveSkin;
    private String selectedSkinPath;
    
    private String selectedIconName;
    // Campos del progreso inline (en desuso, se usa DescargaProgresoController)
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
    
    // Modo edición
    private boolean isEditMode = false;
    private Instance currentInstance;
    private boolean embeddedInMainView = false;

    public void setEmbeddedInMainView(boolean embeddedInMainView) {
        this.embeddedInMainView = embeddedInMainView;
    }
    
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
            memoryLabel.setText(memory + "GB");
        });
        
        // Inicializar icono por defecto
        loadDefaultIcon();
        
        // Precargar nombre de jugador desde la configuración global
        String globalPlayerName = ConfigService.getInstance().getLauncherConfig().getPlayerName();
        if (globalPlayerName != null && !globalPlayerName.isEmpty()) {
            playerNameField.setText(globalPlayerName);
        }
        
        // Precargar memoria desde la configuración global
        String defaultMemory = ConfigService.getInstance().getLauncherConfig().getDefaultMemory();
        if (defaultMemory != null && !isEditMode) {
            try {
                String memStr = defaultMemory.replaceAll("[^0-9]", "");
                double memValue = Double.parseDouble(memStr);
                memorySlider.setValue(memValue);
                memoryLabel.setText(((int)memValue) + "GB");
                logger.info("Memoria por defecto cargada: {}GB", (int)memValue);
            } catch (Exception e) {
                logger.warn("No se pudo cargar la memoria por defecto: {}", defaultMemory);
            }
        }
        
        // Cargar versiones
        loadVersions();
    }

    /**
     * Configura el controlador para editar una instancia existente
     */
    public void setInstanceToEdit(Instance instance) {
        this.isEditMode = true;
        this.currentInstance = instance;
        
        Platform.runLater(() -> {
            if (titleLabel != null) titleLabel.setText("Editar Instancia");
            nameField.setText(instance.getName());
            nameField.setDisable(true); // No permitir cambiar el nombre (id del directorio)
            playerNameField.setText(instance.getPlayerName());
            
            if (instance.getMemory() != null) {
                try {
                    String memStr = instance.getMemory().replaceAll("[^0-9]", "");
                    memorySlider.setValue(Double.parseDouble(memStr));
                } catch (Exception e) {
                    memorySlider.setValue(2);
                }
            }
            
            loaderTypeComboBox.setValue(instance.getLoader());
            versionComboBox.setValue(instance.getVersion());
            
            // Cargar skin de la instancia
            selectedSkinPath = instance.getSkinPath();
            if (selectedSkinPath != null && !selectedSkinPath.isEmpty()) {
                loadSkinPreview(selectedSkinPath);
            } else {
                clearSkinPreview();
            }
            
            selectedIconName = instance.getIcon();
            loadIconPreview(selectedIconName);
            
            createButton.setText("Guardar");
        });
    }
    
    /**
     * Carga las versiones de Minecraft disponibles
     */
    private void loadVersions() {
        versionComboBox.setDisable(true);
        
        new Thread(() -> {
            try {
                availableVersions = mojangService.getReleaseVersions();
                
                Platform.runLater(() -> {
                    List<String> versionIds = availableVersions.stream()
                            .map(MinecraftVersion::getId)
                            .toList();
                    ObservableList<String> items = FXCollections.observableArrayList(versionIds);

                    // Al editar, mostrar la versión de Minecraft con la que se creó la instancia (no la última release)
                    if (isEditMode && currentInstance != null && currentInstance.getVersion() != null
                            && !currentInstance.getVersion().isBlank()) {
                        String saved = currentInstance.getVersion().trim();
                        if (!items.contains(saved)) {
                            items.add(0, saved);
                        }
                        versionComboBox.setItems(items);
                        versionComboBox.setValue(saved);
                    } else {
                        versionComboBox.setItems(items);
                        if (!versionIds.isEmpty()) {
                            versionComboBox.setValue(versionIds.get(0));
                        }
                    }

                    versionComboBox.setDisable(false);
                    
                    logger.info("Cargadas {} versiones de Minecraft", versionIds.size());
                });
                
            } catch (Exception e) {
                logger.error("Error al cargar versiones", e);
                
                Platform.runLater(() -> {
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Icono.fxml"));
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
        System.out.println("[DEBUG] Boton Crear presionado...");
        
        // Validar campos
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            showError("El nombre de la instancia no puede estar vacio");
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
        }
        
        // Si no es edición, verificar si ya existe
        if (!isEditMode && instanceService.instanceExists(name)) {
            showError("Ya existe una instancia con ese nombre");
            return;
        }
        
        // Crear o actualizar instancia
        Instance instance = isEditMode ? currentInstance : new Instance(name, version, loaderType);
        if (!isEditMode) {
            instance.setName(name);
        } else {
            String prevVersion = instance.getVersion();
            LoaderType prevLoader = instance.getLoader();
            instance.setVersion(version);
            instance.setLoader(loaderType);
            if (!Objects.equals(prevVersion, version) || prevLoader != loaderType) {
                instance.setLoaderVersion(null);
            }
        }

        instance.setMemory(((int) memorySlider.getValue()) + "G");
        
        String playerName = playerNameField.getText() != null ? playerNameField.getText().trim() : "";
        if (!playerName.isEmpty()) {
            instance.setPlayerName(playerName);
        }
        
        if (selectedIconName != null && !selectedIconName.isEmpty()) {
            instance.setIcon(selectedIconName);
        }

        // Guardar skin de la instancia
        instance.setSkinPath(selectedSkinPath);
        
        // Deshabilitar formulario y abrir modal
        setFormEnabled(false);
        
        System.out.println("[DEBUG] Intentando llamar a DescargaProgresoController.show()...");
        DescargaProgresoController progresoCtrl = DescargaProgresoController.show(App.getPrimaryStage());
        System.out.println("[DEBUG] PROGRESO_CTRL: " + (progresoCtrl != null ? "CREADO" : "FALLÓ (NULL)"));

        // En modo modal se cierra la ventana; en modo embebido se mantiene la vista.
        if (!embeddedInMainView) {
            Stage currentStage = (Stage) createButton.getScene().getWindow();
            if (currentStage != null) {
                currentStage.close();
            }
        }

        // Callback de cancelación
        final boolean[] cancelado = {false};
        if (progresoCtrl != null) {
            progresoCtrl.setOnCancelCallback(() -> {
                cancelado[0] = true;
                logger.info("Descarga cancelada por el usuario");
            });
        }


        // Variables para cálculo de velocidad
        final long[] lastProgressTime = {System.currentTimeMillis()};
        final double[] lastProgressValue = {0.0};
        final long ESTIMATED_TOTAL_BYTES = 80 * 1024 * 1024L; // ~80 MB estimado

        // Crear en thread separado
        new Thread(() -> {
            try {
                if (isEditMode) {
                    instanceService.updateInstance(instance);
                    if (progresoCtrl != null) {
                        progresoCtrl.setProgress(1.0, "¡Guardado!", "La instancia ha sido actualizada correctamente.");
                        Thread.sleep(800);
                        progresoCtrl.close();
                    }
                } else {
                    instanceService.createInstance(instance, status -> {
                        if (progresoCtrl != null) {
                            // Mapear el mensaje de estado al detalle apropiado
                            String detail = getDetailForStatus(status);
                            Platform.runLater(() -> {
                                progresoCtrl.setProgress(
                                    progresoCtrl.getCurrentProgress(),
                                    status, detail);
                            });
                        }
                    }, progress -> {
                        if (cancelado[0]) return;
                        if (progresoCtrl != null) {
                            long now = System.currentTimeMillis();
                            long deltaMs = now - lastProgressTime[0];
                            double deltaProgress = progress - lastProgressValue[0];

                            // Calcular velocidad y tiempo restante
                            if (deltaMs > 500 && deltaProgress > 0) {
                                long deltaBytes = (long) (deltaProgress * ESTIMATED_TOTAL_BYTES);
                                double mbps = (deltaBytes / 1_048_576.0) / (deltaMs / 1000.0);
                                double remaining = 1.0 - progress;
                                long remainingSeconds = mbps > 0
                                    ? (long) ((remaining * ESTIMATED_TOTAL_BYTES / 1_048_576.0) / mbps)
                                    : -1;

                                progresoCtrl.setSpeed(mbps);
                                progresoCtrl.setTimeRemaining(remainingSeconds);

                                lastProgressTime[0] = now;
                                lastProgressValue[0] = progress;
                            }

                            progresoCtrl.setProgress(progress);
                        }
                    });
                }

                Platform.runLater(() -> {
                    if (progresoCtrl != null) {
                        progresoCtrl.setProgress(1.0, "¡Instancia lista!", "La instancia se ha creado correctamente.");
                        progresoCtrl.setSpeed(0);
                        progresoCtrl.setTimeRemaining(0);
                        progresoCtrl.disableCancel();
                    }

                    // Refrescar el grid de la ventana principal
                    if (PrincipalController.getInstance() != null) {
                        PrincipalController.getInstance().refreshInstances();
                        if (embeddedInMainView) {
                            PrincipalController.getInstance().restoreMainContent();
                        }
                    }

                    // Pequeña pausa para que el usuario vea el 100%
                    new Thread(() -> {
                        try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
                        Platform.runLater(() -> {
                            if (progresoCtrl != null) progresoCtrl.close();

                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setTitle(isEditMode ? "Instancia actualizada" : "Instancia creada");
                            alert.setHeaderText(null);
                            alert.setContentText("La instancia \"" + name + "\" ha sido "
                                + (isEditMode ? "actualizada" : "creada") + " exitosamente");
                            AlertUtil.styleAlert(alert);
                            alert.showAndWait();
                        });
                    }).start();

                });
            } catch (Exception e) {
                System.out.println("[ERROR CRITICO] Error en el hilo de descarga: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    if (progresoCtrl != null) progresoCtrl.close();
                    setFormEnabled(true);

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
        // Si está embebida, regresar al contenido principal.
        if (embeddedInMainView) {
            if (PrincipalController.getInstance() != null) {
                PrincipalController.getInstance().restoreMainContent();
            }
            return;
        }

        // Cerrar la ventana modal
        Stage stage = (Stage) createButton.getScene().getWindow();
        if (stage != null) {
            stage.close();
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
     * Devuelve un texto de detalle legible para cada mensaje de estado del servicio.
     */
    private String getDetailForStatus(String status) {
        if (status == null) return "";
        if (status.contains("directorios")) return "Preparando la estructura de carpetas de la instancia.";
        if (status.contains("Descargando Minecraft")) return "Obteniendo el archivo JAR principal y sus dependencias del servidor de Mojang.";
        if (status.contains("client.jar")) return "Descargando el cliente de Minecraft...";
        if (status.contains("librer")) return "Obteniendo librerías del núcleo y binarios nativos necesarios para la ejecución.";
        if (status.contains("assets")) return "Descargando texturas, sonidos e índices de recursos del juego.";
        if (status.contains("Fabric")) return "Instalando el mod loader Fabric para esta versión.";
        if (status.contains("Forge")) return "Instalando Forge y verificando las librerías necesarias.";
        if (status.contains("configuraci")) return "Guardando los ajustes de la instancia en disco.";
        if (status.contains("Guardado") || status.contains("lista") || status.contains("exitosa"))
            return "La instància se ha configurado correctamente y ya está lista para usar.";
        return status;
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
                if (iconPlaceholder != null) iconPlaceholder.setVisible(false);
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
                if (iconPlaceholder != null) iconPlaceholder.setVisible(false);
            } else {
                // Si no se encuentra, intentar como ruta absoluta
                java.io.File iconFile = new java.io.File(iconName);
                if (iconFile.exists()) {
                    iconPreview.setImage(new Image(iconFile.toURI().toString()));
                    if (iconPlaceholder != null) iconPlaceholder.setVisible(false);
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
     * Muestra una previsualizacion simple cuando no hay icono disponible.
     */
    private void createDefaultIconPreview() {
        if (iconPreview != null) {
            iconPreview.setImage(null);
        }
        if (iconPlaceholder != null) {
            iconPlaceholder.setText("🖼");
            iconPlaceholder.setVisible(true);
        }
        selectedIconName = null;
    }
    
    /**
     * Selecciona una skin (.png) desde el explorador de archivos
     */
    @FXML
    private void onBrowseSkin() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Seleccionar Skin (.png)");
        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("Imágenes PNG", "*.png")
        );

        java.io.File file = fileChooser.showOpenDialog(nameField.getScene().getWindow());
        if (file != null) {
            try {
                // Igual que en Configuración: copiar a una ruta gestionada por el launcher.
                String playerName = playerNameField.getText() != null ? playerNameField.getText().trim() : "";
                if (playerName.isEmpty()) {
                    playerName = nameField.getText() != null ? nameField.getText().trim() : "";
                }
                if (playerName.isEmpty()) {
                    playerName = "Player";
                }

                String instanceName = nameField.getText() != null ? nameField.getText().trim() : "";
                String fileHint = (instanceName.isEmpty() ? "instance" : instanceName) + "_" + playerName + "_skin";
                java.nio.file.Path destPath = ConfigService.getInstance()
                        .copySkinToStorage(file.toPath(), fileHint);

                selectedSkinPath = destPath.toAbsolutePath().toString();
                loadSkinPreview(selectedSkinPath);
                logger.info("Skin seleccionada y copiada para la instancia: {}", selectedSkinPath);
            } catch (Exception e) {
                logger.error("No se pudo copiar la skin seleccionada", e);
                showError("No se pudo guardar la skin seleccionada");
            }
        }
    }

    @FXML
    private void onRemoveSkin() {
        selectedSkinPath = "";
        clearSkinPreview();
        logger.info("Skin de la instancia eliminada");
    }

    private void loadSkinPreview(String path) {
        try {
            java.io.File file = new java.io.File(path);
            if (file.exists()) {
                Image image = new Image(file.toURI().toString());
                skinPreview.setImage(buildSkinFrontPreview(image));
                skinPreview.setViewport(null);
                skinPreview.setSmooth(false);
                skinPreview.setPreserveRatio(false);
                skinPlaceholder.setVisible(false);
                skinNameLabel.setText(file.getName());
                btnRemoveSkin.setDisable(false);
            } else {
                clearSkinPreview();
            }
        } catch (Exception e) {
            logger.warn("No se pudo cargar previsualización de skin: {}", path);
            clearSkinPreview();
        }
    }

    private void clearSkinPreview() {
        skinPreview.setImage(null);
        skinPlaceholder.setVisible(true);
        skinNameLabel.setText("Usar skin global del launcher");
        btnRemoveSkin.setDisable(true);
    }

    private Image buildSkinFrontPreview(Image skinTexture) {
        if (skinTexture == null) {
            return null;
        }

        int texH = (int) Math.round(skinTexture.getHeight());
        PixelReader pr = skinTexture.getPixelReader();
        if (pr == null || texH <= 0) {
            return skinTexture;
        }

        final int scale = 4;
        final int w = 16 * scale;
        final int h = 32 * scale;
        WritableImage result = new WritableImage(w, h);
        PixelWriter pw = result.getPixelWriter();
        boolean modernSkin = texH >= 64;

        // Ensamblado en formato delgado (Alex): brazos de 3px
        copyPart(pr, pw, 8, 8, 8, 8, 4, 0, scale, false);    // cabeza
        copyPart(pr, pw, 20, 20, 8, 12, 4, 8, scale, false); // torso
        copyPart(pr, pw, 44, 20, 3, 12, 1, 8, scale, false); // brazo der (delgado)
        if (modernSkin) {
            copyPart(pr, pw, 36, 52, 3, 12, 12, 8, scale, false); // brazo izq (delgado)
        } else {
            copyPart(pr, pw, 44, 20, 3, 12, 12, 8, scale, true);
        }
        copyPart(pr, pw, 4, 20, 4, 12, 4, 20, scale, false); // pierna der
        if (modernSkin) {
            copyPart(pr, pw, 20, 52, 4, 12, 8, 20, scale, false); // pierna izq
        } else {
            copyPart(pr, pw, 4, 20, 4, 12, 8, 20, scale, true);
        }

        return result;
    }

    private void copyPart(PixelReader pr, PixelWriter pw,
                          int srcX, int srcY, int srcW, int srcH,
                          int dstX, int dstY, int scale, boolean mirror) {
        for (int sy = 0; sy < srcH; sy++) {
            for (int sx = 0; sx < srcW; sx++) {
                int argb = pr.getArgb(srcX + sx, srcY + sy);
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        int destCol = mirror
                                ? (dstX + srcW - 1 - sx) * scale + dx
                                : (dstX + sx) * scale + dx;
                        int destRow = (dstY + sy) * scale + dy;
                        pw.setArgb(destCol, destRow, argb);
                    }
                }
            }
        }
    }
}
