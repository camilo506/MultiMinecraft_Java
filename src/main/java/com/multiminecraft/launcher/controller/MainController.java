package com.multiminecraft.launcher.controller;

import com.multiminecraft.launcher.model.Instance;
import com.multiminecraft.launcher.service.ConfigService;
import com.multiminecraft.launcher.service.InstanceService;
import com.multiminecraft.launcher.service.LaunchService;
import com.multiminecraft.launcher.util.AlertUtil;
import com.multiminecraft.launcher.util.PlatformUtil;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Controlador de la ventana principal
 */
public class MainController {
    
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    
    private final InstanceService instanceService;
    private final LaunchService launchService;
    private Instance selectedInstance;
    
    // Variables para arrastrar la ventana
    private double xOffset = 0;
    private double yOffset = 0;
    
    // Barra de título personalizada
    @FXML private HBox titleBar;
    @FXML private Button minimizeBtn;
    @FXML private Button maximizeBtn;
    @FXML private Button closeBtn;
    
    // Componentes del sidebar
    @FXML private VBox selectedInstancePanel;
    @FXML private ImageView selectedInstanceIcon;
    @FXML private Label selectedInstanceName;
    @FXML private Label selectedInstanceVersion;
    @FXML private Button playButton;
    @FXML private Button modifyButton;
    @FXML private Button recursosButton;
    @FXML private Button mapsButton;
    @FXML private Button viewLocationButton;
    @FXML private Button deleteButton;
    @FXML private Button createInstanceButton;
    
    // Componentes del panel derecho
    @FXML private FlowPane instancesGrid;
    
    // Componentes del footer
    // footerLabel eliminado - ahora es texto estático "Monkey Studio"
    @FXML private ProgressBar progressBar;
    @FXML private HBox progressContainer;
    @FXML private Label progressLabel;
    
    public MainController() {
        this.instanceService = new InstanceService();
        this.launchService = new LaunchService();
    }
    
    @FXML
    public void initialize() {
        logger.info("Inicializando ventana principal");
        
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
        
        // Cargar instancias disponibles
        loadInstances();
        
        // Deshabilitar botones de acción inicialmente
        setActionButtonsEnabled(false);
    }
    
    /**
     * Carga todas las instancias disponibles y las muestra en el grid
     */
    private void loadInstances() {
        instancesGrid.getChildren().clear();
        
        List<Instance> instances = instanceService.listInstances();
        logger.info("Cargando {} instancias", instances.size());
        
        for (Instance instance : instances) {
            StackPane instanceCard = createInstanceCard(instance);
            instancesGrid.getChildren().add(instanceCard);
        }
        
        // Agregar tarjeta "Nueva Instancia" al final
        instancesGrid.getChildren().add(createNewInstanceCard());
        
        // Si hay instancias, seleccionar la primera por defecto
        if (!instances.isEmpty() && selectedInstance == null) {
            selectInstance(instances.get(0));
        }
        
        // Actualizar selección visual si ya había una seleccionada
        if (selectedInstance != null) {
            updateInstanceCardsSelection();
        }
    }
    
    /**
     * Crea una tarjeta visual para una instancia (diseño moderno)
     */
    private StackPane createInstanceCard(Instance instance) {
        // Contenedor principal con StackPane para poder superponer el indicador
        StackPane cardWrapper = new StackPane();
        cardWrapper.setPrefWidth(110);
        cardWrapper.setPrefHeight(140);
        
        VBox card = new VBox(5);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(10, 6, 8, 6));
        card.setPrefWidth(110);
        card.setPrefHeight(140);
        card.getStyleClass().add("instance-card");
        
        // Icono de la instancia
        ImageView iconView = new ImageView();
        iconView.setFitWidth(48);
        iconView.setFitHeight(48);
        iconView.setPreserveRatio(true);
        
        // Intentar cargar el icono de la instancia
        try {
            String iconName = instance.getIcon();
            if (iconName != null && !iconName.isEmpty()) {
                Image iconImage = loadInstanceIcon(iconName);
                if (iconImage != null) {
                    iconView.setImage(iconImage);
                } else {
                    createDefaultIconForCard(iconView);
                }
            } else {
                createDefaultIconForCard(iconView);
            }
        } catch (Exception e) {
            logger.warn("No se pudo cargar el icono de la instancia: {}", instance.getName(), e);
            createDefaultIconForCard(iconView);
        }
        
        // Nombre de la instancia
        Label nameLabel = new Label(instance.getName());
        nameLabel.getStyleClass().add("instance-card-name");
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(100);
        nameLabel.setAlignment(Pos.CENTER);
        
        // Versión de la instancia
        Label versionLabel = new Label(instance.getVersion());
        versionLabel.getStyleClass().add("instance-card-version");
        
        card.getChildren().addAll(iconView, nameLabel, versionLabel);
        
        // Indicador verde (dot) para la tarjeta seleccionada
        Circle onlineDot = new Circle(5);
        onlineDot.setFill(Color.web("#26d9a0"));
        onlineDot.setVisible(false);
        StackPane.setAlignment(onlineDot, Pos.TOP_RIGHT);
        StackPane.setMargin(onlineDot, new Insets(8, 8, 0, 0));
        
        cardWrapper.getChildren().addAll(card, onlineDot);
        cardWrapper.setUserData(instance.getName()); // Para identificar la tarjeta
        
        // Agregar evento de clic para seleccionar la instancia
        cardWrapper.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            selectInstance(instance);
        });
        
        return cardWrapper;
    }
    
    /**
     * Crea la tarjeta "Nueva Instancia" para el grid
     */
    private StackPane createNewInstanceCard() {
        StackPane cardWrapper = new StackPane();
        cardWrapper.setPrefWidth(110);
        cardWrapper.setPrefHeight(140);
        
        VBox card = new VBox(5);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(10, 6, 8, 6));
        card.setPrefWidth(110);
        card.setPrefHeight(140);
        card.getStyleClass().add("new-instance-card");
        
        // Icono "+"
        Label plusLabel = new Label("+");
        plusLabel.getStyleClass().add("new-instance-plus");
        
        // Texto "Nueva Instancia"
        Label textLabel = new Label("Nueva Instancia");
        textLabel.getStyleClass().add("new-instance-label");
        
        card.getChildren().addAll(plusLabel, textLabel);
        cardWrapper.getChildren().add(card);
        
        // Evento de clic
        cardWrapper.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            onCreateInstanceClicked();
        });
        
        return cardWrapper;
    }
    
    /**
     * Selecciona una instancia y actualiza la vista
     */
    private void selectInstance(Instance instance) {
        this.selectedInstance = instance;
        logger.info("Instancia seleccionada: {}", instance.getName());
        
        // Actualizar sidebar con información de la instancia seleccionada
        selectedInstanceName.setText(instance.getName());
        selectedInstanceVersion.setText(instance.getDescription());
        
        // Mostrar el panel de instancia seleccionada
        selectedInstancePanel.setVisible(true);
        selectedInstancePanel.setManaged(true);
        
        // Actualizar icono
        try {
            String iconName = instance.getIcon();
            if (iconName != null && !iconName.isEmpty()) {
                Image iconImage = loadInstanceIcon(iconName);
                if (iconImage != null) {
                    selectedInstanceIcon.setImage(iconImage);
                } else {
                    selectedInstanceIcon.setImage(null);
                }
            } else {
                selectedInstanceIcon.setImage(null);
            }
        } catch (Exception e) {
            logger.warn("No se pudo cargar el icono de la instancia seleccionada", e);
            selectedInstanceIcon.setImage(null);
        }
        
        // Footer ahora es estático (Monkey Studio)
        
        // Habilitar botones de acción
        setActionButtonsEnabled(true);
        
        // Resaltar la tarjeta seleccionada en el grid
        updateInstanceCardsSelection();
    }
    
    /**
     * Actualiza la selección visual de las tarjetas de instancias
     */
    private void updateInstanceCardsSelection() {
        for (var node : instancesGrid.getChildren()) {
            if (node instanceof StackPane) {
                StackPane wrapper = (StackPane) node;
                String instanceName = (String) wrapper.getUserData();
                
                // Buscar la VBox card dentro del StackPane
                VBox card = null;
                Circle dot = null;
                for (var child : wrapper.getChildren()) {
                    if (child instanceof VBox) card = (VBox) child;
                    if (child instanceof Circle) dot = (Circle) child;
                }
                
                if (card != null && instanceName != null) {
                    boolean isSelected = selectedInstance != null && instanceName.equals(selectedInstance.getName());
                    if (isSelected) {
                        if (!card.getStyleClass().contains("instance-card-selected")) {
                            card.getStyleClass().add("instance-card-selected");
                        }
                        if (dot != null) dot.setVisible(true);
                    } else {
                        card.getStyleClass().remove("instance-card-selected");
                        if (dot != null) dot.setVisible(false);
                    }
                }
            }
        }
    }
    
    /**
     * Habilita o deshabilita los botones de acción
     */
    private void setActionButtonsEnabled(boolean enabled) {
        playButton.setDisable(!enabled);
        modifyButton.setDisable(!enabled);
        recursosButton.setDisable(!enabled);
        mapsButton.setDisable(!enabled);
        viewLocationButton.setDisable(!enabled);
        deleteButton.setDisable(!enabled);
    }
    
    // Acciones de la barra de título
    
    @FXML
    private void onMinimizeClicked() {
        Stage stage = (Stage) titleBar.getScene().getWindow();
        stage.setIconified(true);
    }
    
    @FXML
    private void onMaximizeClicked() {
        Stage stage = (Stage) titleBar.getScene().getWindow();
        stage.setMaximized(!stage.isMaximized());
    }
    
    @FXML
    private void onCloseClicked() {
        Stage stage = (Stage) titleBar.getScene().getWindow();
        stage.close();
    }
    
    // Acciones de los botones
    
    @FXML
    private void onPlayClicked() {
        if (selectedInstance == null) return;
        
        logger.info("Iniciando juego para instancia: {}", selectedInstance.getName());
        
        // Deshabilitar botón mientras se lanza
        playButton.setDisable(true);
        playButton.setText("Iniciando...");
        
        // Mostrar y configurar barra de progreso si existe
        if (progressBar != null) {
            progressContainer.setVisible(true);
            progressBar.setProgress(0.01);
            progressBar.setStyle("-fx-accent: #4CAF50; -fx-control-inner-background: #2d2d2d;");
            if (progressLabel != null) progressLabel.setText("1%");
        }
        
        // Lanzar en un hilo separado para no bloquear la UI
        new Thread(() -> {
            try {
                // Pequeño delay para asegurar que la UI se actualice
                Thread.sleep(50);
                
                // Progreso inicial: preparación (0-20%)
                updateProgress(0.1);
                Thread.sleep(100);
                
                updateProgress(0.2);
                Thread.sleep(100);
                
                // Lanzar proceso
                logger.debug("Lanzando proceso de Minecraft...");
                Process process = launchService.launchInstance(selectedInstance);
                
                // Progreso: proceso iniciado (20-40%)
                updateProgress(0.4);
                Thread.sleep(200);
                
                // Monitorear el proceso y avanzar progresivamente
                int maxWaitTime = 30000; // 30 segundos máximo
                long startTime = System.currentTimeMillis();
                double baseProgress = 0.4;
                
                while (process.isAlive() && (System.currentTimeMillis() - startTime) < maxWaitTime) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    // Avanzar progresivamente hasta 95% basado en el tiempo transcurrido
                    double timeProgress = Math.min(0.95, baseProgress + (elapsed / (double)maxWaitTime) * 0.55);
                    
                    updateProgress(timeProgress);
                    
                    Thread.sleep(200);
                    
                    // Si el proceso ya no está vivo, verificar error
                    if (!process.isAlive()) {
                        int exitCode = process.exitValue();
                        if (exitCode != 0) {
                            Platform.runLater(() -> {
                                if (progressContainer != null) progressContainer.setVisible(false);
                                playButton.setDisable(false);
                                playButton.setText("Jugar");
                                showError("Error al iniciar el juego", "Minecraft se cerró con código de error: " + exitCode);
                            });
                            return;
                        }
                        break;
                    }
                }
                
                // Completar la barra cuando el proceso está activo
                updateProgress(1.0);
                
                // Esperar y detectar cuando aparezca la ventana de Minecraft
                logger.debug("Esperando a que aparezca la ventana de Minecraft...");
                int maxWindowWaitTime = 15000; // 15 segundos máximo esperando la ventana
                long windowStartTime = System.currentTimeMillis();
                boolean windowDetected = false;
                
                while (!windowDetected && process.isAlive() && 
                       (System.currentTimeMillis() - windowStartTime) < maxWindowWaitTime) {
                    
                    // Método 1: Verificar si hay una ventana visible en el proceso o sus hijos
                    if (PlatformUtil.hasVisibleWindow(process)) {
                        windowDetected = true;
                        logger.info("Ventana de Minecraft detectada (método 1) - cerrando launcher");
                        break;
                    }
                    
                    // Método 2: Buscar cualquier ventana con "Minecraft" o "Mojang" en el título
                    if (PlatformUtil.findMinecraftWindow()) {
                        windowDetected = true;
                        logger.info("Ventana de Minecraft detectada (método 2) - cerrando launcher");
                        break;
                    }
                    
                    // Esperar un poco antes de verificar nuevamente
                    Thread.sleep(200);
                }
                
                // Si no se detectó la ventana pero el proceso está vivo, esperar un poco más
                if (!windowDetected && process.isAlive()) {
                    logger.debug("Ventana no detectada automáticamente, esperando tiempo adicional...");
                    Thread.sleep(2000);
                }
                
                // Cerrar el launcher cuando la ventana de Minecraft aparezca o después del tiempo de espera
                Platform.runLater(() -> {
                    try {
                        Stage mainStage = (Stage) playButton.getScene().getWindow();
                        if (mainStage != null) {
                            logger.info("Cerrando launcher - Minecraft iniciado");
                            mainStage.close();
                        }
                    } catch (Exception e) {
                        logger.error("Error al cerrar la ventana principal", e);
                    }
                });
                
            } catch (Exception e) {
                logger.error("Error al iniciar el juego", e);
                Platform.runLater(() -> {
                    if (progressContainer != null) {
                        progressContainer.setVisible(false);
                    }
                    playButton.setDisable(false);
                    playButton.setText("Jugar");
                    showError("Error al iniciar el juego", "No se pudo iniciar Minecraft: " + e.getMessage());
                });
            }
        }).start();
    }
    
    /**
     * Actualiza el progreso de la barra de forma segura
     */
    private void updateProgress(double progress) {
        Platform.runLater(() -> {
            if (progressBar != null) {
                // Asegurar que el progreso esté en el rango válido
                double clampedProgress = Math.max(0.0, Math.min(1.0, progress));
                progressBar.setProgress(clampedProgress);
                // Forzar actualización del estilo
                progressBar.setStyle("-fx-accent: #4CAF50; -fx-control-inner-background: #2d2d2d;");
                // Actualizar label de porcentaje
                if (progressLabel != null) {
                    progressLabel.setText((int)(clampedProgress * 100) + "%");
                }
                logger.debug("Progreso actualizado: {}% (valor: {})", (int)(clampedProgress * 100), clampedProgress);
            } else {
                logger.warn("No se puede actualizar el progreso: progressBar es null");
            }
        });
    }
    
    @FXML
    private void onModifyClicked() {
        if (selectedInstance == null) return;
        logger.debug("Modificar instancia: {}", selectedInstance.getName());
        
        try {
            // Cargar la vista de editar instancia
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/EditInstanceView.fxml"));
            Parent editInstanceView = loader.load();
            
            // Obtener el controlador y establecer la instancia a editar
            EditInstanceController controller = loader.getController();
            controller.setInstance(selectedInstance);
            
            // Crear una nueva ventana modal
            Stage editStage = new Stage();
            editStage.setTitle("Modificar Instancia");
            editStage.initModality(Modality.WINDOW_MODAL);
            editStage.initOwner(modifyButton.getScene().getWindow());
            editStage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
            
            Scene scene = new Scene(editInstanceView, 540, 150);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            // Aplicar estilos
            scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource("/css/dark-theme.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource("/css/edit-instance.css").toExternalForm());
            
            editStage.setScene(scene);
            editStage.setMinWidth(500);
            editStage.setMinHeight(420);
            
            // Mostrar la ventana modal y esperar
            editStage.showAndWait();
            
            // Si se guardaron cambios, recargar las instancias
            if (controller.isSaved()) {
                selectedInstance = null;
                loadInstances();
            }
            
        } catch (IOException e) {
            logger.error("Error al abrir ventana de editar instancia", e);
            showError("Error", "No se pudo abrir la ventana de modificación: " + e.getMessage());
        }
    }
    
    @FXML
    private void onRecursosClicked() {
        if (selectedInstance == null) {
            logger.warn("No hay instancia seleccionada");
            return;
        }
        logger.debug("Recursos de instancia: {}", selectedInstance.getName());
        
        try {
            // Cargar la vista de recursos
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/RecursosView.fxml"));
            Parent recursosView = loader.load();
            
            // Obtener el controlador y establecer la instancia
            com.multiminecraft.launcher.controller.RecursosController controller = loader.getController();
            if (controller != null) {
                controller.setInstance(selectedInstance);
            }
            
            // Crear una nueva ventana modal
            Stage recursosStage = new Stage();
            recursosStage.setTitle("Recursos");
            recursosStage.initModality(Modality.WINDOW_MODAL);
            recursosStage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
            
            if (recursosButton != null && recursosButton.getScene() != null) {
                recursosStage.initOwner(recursosButton.getScene().getWindow());
            }
            
            Scene scene = new Scene(recursosView, 400, 150);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            // Aplicar estilos
            scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource("/css/dark-theme.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource("/css/recursos-view.css").toExternalForm());
            
            recursosStage.setScene(scene);
            recursosStage.setMinWidth(350);
            recursosStage.setMinHeight(300);
            
            // Mostrar la ventana modal
            recursosStage.showAndWait();
            
        } catch (Exception e) {
            logger.error("Error al abrir ventana de recursos", e);
            e.printStackTrace();
            showError("Error", "No se pudo abrir la ventana de recursos: " + e.getMessage());
        }
    }
    
    @FXML
    private void onMapsClicked() {
        if (selectedInstance == null) return;
        logger.debug("Abrir mapas de instancia: {}", selectedInstance.getName());
        
        try {
            ConfigService configService = ConfigService.getInstance();
            Path minecraftDir = configService.getInstanceMinecraftDirectory(selectedInstance.getName());
            Path savesDir = minecraftDir.resolve("saves");
            
            // Crear la carpeta si no existe
            File dir = savesDir.toFile();
            if (!dir.exists()) {
                dir.mkdirs();
                logger.info("Carpeta saves creada: {}", savesDir);
            }
            
            // Abrir la carpeta en el explorador
            openFolderInExplorer(savesDir);
            
        } catch (Exception e) {
            logger.error("Error al abrir carpeta de mapas", e);
            showError("Error", "No se pudo abrir la carpeta de mapas: " + e.getMessage());
        }
    }
    
    @FXML
    private void onViewLocationClicked() {
        if (selectedInstance == null) return;
        logger.debug("Ver ubicación de instancia: {}", selectedInstance.getName());
        instanceService.openInstanceFolder(selectedInstance.getName());
    }
    
    @FXML
    private void onDeleteClicked() {
        if (selectedInstance == null) return;
        
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmar eliminación");
        alert.setHeaderText("¿Eliminar instancia?");
        alert.setContentText("¿Estás seguro de que deseas eliminar la instancia \"" + selectedInstance.getName() + "\"?\nEsta acción no se puede deshacer.");
        AlertUtil.styleAlert(alert);
        
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    instanceService.deleteInstance(selectedInstance.getName());
                    logger.info("Instancia eliminada: {}", selectedInstance.getName());
                    selectedInstance = null;
                    setActionButtonsEnabled(false);
                    selectedInstanceName.setText("Selecciona una instancia");
                    selectedInstanceVersion.setText("");
                    // footerLabel eliminado
                    selectedInstancePanel.setVisible(false);
                    selectedInstancePanel.setManaged(false);
                    loadInstances();
                } catch (Exception e) {
                    logger.error("Error al eliminar instancia", e);
                    showError("Error al eliminar", "No se pudo eliminar la instancia: " + e.getMessage());
                }
            }
        });
    }
    
    @FXML
    private void onCreateInstanceClicked() {
        logger.debug("Crear nueva instancia");
        try {
            // Cargar la vista de crear instancia
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CreateInstanceView.fxml"));
            Parent createInstanceView = loader.load();
            
            // Crear una nueva ventana modal
            Stage createInstanceStage = new Stage();
            createInstanceStage.setTitle("Crear Nueva Instancia");
            createInstanceStage.initModality(Modality.WINDOW_MODAL);
            createInstanceStage.initOwner(createInstanceButton.getScene().getWindow());
            createInstanceStage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
            
            Scene scene = new Scene(createInstanceView, 540, 150);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            // Aplicar estilos
            scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource("/css/dark-theme.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource("/css/create-instance.css").toExternalForm());
            
            createInstanceStage.setScene(scene);
            createInstanceStage.setMinWidth(500);
            createInstanceStage.setMinHeight(480);
            
            // Mostrar la ventana modal
            createInstanceStage.showAndWait();
            
            // Recargar instancias después de cerrar la ventana
            loadInstances();
            
        } catch (IOException e) {
            logger.error("Error al abrir ventana de crear instancia", e);
            showError("Error", "No se pudo abrir la ventana de crear instancia: " + e.getMessage());
        }
    }
    

    
    /**
     * Carga un icono de instancia desde la carpeta de recursos o ruta absoluta
     * @param iconName Nombre del archivo del icono
     * @return Image cargada o null si no se encuentra
     */
    private Image loadInstanceIcon(String iconName) {
        try {
            // Primero intentar cargar desde la carpeta de recursos del proyecto
            String resourcePath = "/icons/" + iconName;
            java.io.InputStream resourceStream = getClass().getResourceAsStream(resourcePath);
            if (resourceStream != null) {
                logger.debug("Icono cargado desde recursos: {}", resourcePath);
                return new Image(resourceStream);
            }
            
            // Si no se encuentra en recursos, intentar como ruta absoluta
            File iconFile = new File(iconName);
            if (iconFile.exists()) {
                logger.debug("Icono cargado desde ruta absoluta: {}", iconName);
                return new Image(iconFile.toURI().toString());
            }
            
            logger.debug("No se encontró el icono: {}", iconName);
            return null;
        } catch (Exception e) {
            logger.warn("Error al cargar icono: {}", iconName, e);
            return null;
        }
    }
    
    /**
     * Crea un icono por defecto simple para una tarjeta
     */
    private void createDefaultIconForCard(ImageView iconView) {
        double size = iconView.getFitWidth() > 0 ? iconView.getFitWidth() : 72;
        Canvas canvas = new Canvas(size, size);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        
        // Fondo teal oscuro redondeado
        gc.setFill(Color.web("#1a3a4a"));
        gc.fillRoundRect(0, 0, size, size, 12, 12);
        
        // Patrón de cuadrícula (simulando textura de Minecraft)
        gc.setFill(Color.web("#2a5a6a"));
        double gridSize = size / 4.0;
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                gc.fillRoundRect(4 + i * gridSize, 4 + j * gridSize, gridSize - 4, gridSize - 4, 3, 3);
            }
        }
        
        WritableImage image = new WritableImage((int) size, (int) size);
        canvas.snapshot(null, image);
        iconView.setImage(image);
    }
    
    /**
     * Abre una carpeta en el explorador de archivos del sistema
     */
    private void openFolderInExplorer(java.nio.file.Path folderPath) {
        try {
            File folder = folderPath.toFile();
            if (!folder.exists()) {
                logger.warn("La carpeta no existe: {}", folderPath);
                return;
            }
            
            switch (PlatformUtil.getOS()) {
                case WINDOWS:
                    Runtime.getRuntime().exec("explorer.exe \"" + folder.getAbsolutePath() + "\"");
                    break;
                case MACOS:
                    Runtime.getRuntime().exec(new String[]{"open", folder.getAbsolutePath()});
                    break;
                case LINUX:
                    Runtime.getRuntime().exec(new String[]{"xdg-open", folder.getAbsolutePath()});
                    break;
                default:
                    logger.warn("Sistema operativo no soportado para abrir explorador");
            }
            
            logger.info("Carpeta abierta en explorador: {}", folderPath);
            
        } catch (IOException e) {
            logger.error("Error al abrir carpeta en explorador", e);
        }
    }
    
    /**
     * Muestra un diálogo de error
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        AlertUtil.styleAlert(alert);
        alert.showAndWait();
    }
}
