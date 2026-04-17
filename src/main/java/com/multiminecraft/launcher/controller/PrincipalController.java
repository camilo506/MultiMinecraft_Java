package com.multiminecraft.launcher.controller;

import com.multiminecraft.launcher.App;
import com.multiminecraft.launcher.model.Instance;
import com.multiminecraft.launcher.model.LoaderType;
import com.multiminecraft.launcher.service.ConfigService;
import com.multiminecraft.launcher.service.InstanceService;
import com.multiminecraft.launcher.service.LaunchService;
import com.multiminecraft.launcher.util.AlertUtil;
import com.multiminecraft.launcher.util.PlatformUtil;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.BorderPane;
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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Controlador de la ventana principal - Diseño MultiMinecraft
 */
public class PrincipalController {

    private static final Logger logger = LoggerFactory.getLogger(PrincipalController.class);

    /**
     * URL de la imagen del banner en GitHub.
     * Cambiar esta imagen en el repositorio actualiza el banner de todos los
     * launchers.
     * Si no hay internet, se usa la imagen local (Banner.png) como fallback.
     */
    private static final String REMOTE_BANNER_URL = "https://raw.githubusercontent.com/camilo506/MinecraftBanner/main/banner-remote.png";

    private final InstanceService instanceService;
    private final LaunchService launchService;
    private Instance selectedInstance;

    // Referencia estática para refrescar desde otros controladores
    private static PrincipalController instance;

    public static PrincipalController getInstance() {
        return instance;
    }

    // Variables para arrastrar la ventana
    private double xOffset = 0;
    private double yOffset = 0;

    // Barra de título personalizada
    @FXML
    private BorderPane titleBar;
    @FXML
    private Button minimizeBtn;
    @FXML
    private Button maximizeBtn;
    @FXML
    private Button closeBtn;

    // Sidebar - Navegación
    @FXML
    private Button navModpacksButton;
    @FXML
    private Button navResourcePacksButton;
    @FXML
    private Button navMapsButton;
    @FXML
    private Button navConfigButton;

    // Sidebar - Jugador
    @FXML
    private ImageView playerAvatar;
    @FXML
    private Label playerNameLabel;
    @FXML
    private Button createInstanceButton;

    // Sidebar - Instancia Seleccionada
    @FXML
    private VBox selectedInstanceCard;
    @FXML
    private ImageView selectedInstanceSidebarIcon;
    @FXML
    private Label selectedInstanceSidebarName;
    @FXML
    private Label selectedInstanceSidebarVersion;
    @FXML
    private Button sidebarPlayButton;

    // Panel principal - Hero Banner
    @FXML
    private StackPane heroBanner;
    @FXML
    private ImageView heroBannerImage;
    @FXML
    private Label lastPlayedLabel;
    @FXML
    private Label heroSubtitle;
    @FXML
    private Label heroLoaderLabel;
    @FXML
    private Label heroTitle;
    @FXML
    private Label heroBadge;

    // Panel principal - Instancias
    @FXML
    private FlowPane instancesGrid;

    // Footer / Progress
    @FXML
    private ProgressBar progressBar;
    @FXML
    private HBox progressContainer;
    @FXML
    private Label progressLabel;

    @FXML
    private ScrollPane instancesScrollPane;

    // Contenedores de vista central
    @FXML
    private StackPane centerStack;
    @FXML
    private HBox mainContentArea;

    // Nodo de la vista servidor cargada (para poder removerla)
    private javafx.scene.Node vistaServidorNode;

    // Tamaño de tarjetas
    private static final double CARD_WIDTH = 110;
    private static final double CARD_HEIGHT = 125; // Aumentar un poco el alto para evitar cortes verticales

    public PrincipalController() {
        this.instanceService = new InstanceService();
        this.launchService = new LaunchService();
    }

    @FXML
    public void initialize() {
        logger.info("Inicializando ventana principal - Diseño MultiMinecraft");
        instance = this;

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

        // Configurar el banner hero con efecto "Cover" (Center-Crop)
        if (heroBannerImage != null && heroBanner != null) {
            // Escuchar cambios de tamaño en el contenedor del banner
            heroBanner.widthProperty().addListener((obs, oldVal, newVal) -> updateBannerViewport());
            heroBanner.heightProperty().addListener((obs, oldVal, newVal) -> updateBannerViewport());

            // También cuando la imagen termine de cargar
            heroBannerImage.imageProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null)
                    updateBannerViewport();
            });

            // Llamada inicial
            Platform.runLater(this::updateBannerViewport);
        }

        // Configurar nombre del jugador
        setupPlayerInfo();

        // Cargar instancias disponibles
        loadInstances();

        // Actualizar info del banner con la instancia más reciente por defecto
        updateBannerInfo(null);

        // Intentar cargar banner remoto desde GitHub (fallback: imagen local)
        loadRemoteBanner();
    }

    /**
     * Ajusta el viewport de la imagen del banner para lograr un efecto "Cover"
     * (Center-Crop).
     * Esto asegura que la imagen siempre llene el espacio sin estirarse y bien
     * centrada.
     */
    private void updateBannerViewport() {
        Image img = heroBannerImage.getImage();
        if (img == null || heroBanner == null)
            return;

        double viewWidth = heroBanner.getWidth();
        double viewHeight = heroBanner.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0)
            return;

        double imgWidth = img.getWidth();
        double imgHeight = img.getHeight();

        double viewAspect = viewWidth / viewHeight;
        double imgAspect = imgWidth / imgHeight;

        double x, y, w, h;

        if (imgAspect > viewAspect) {
            // La imagen es más ancha que la vista
            h = imgHeight;
            w = imgHeight * viewAspect;
            x = (imgWidth - w) / 2;
            y = 0;
        } else {
            // La imagen es más alta que la vista
            w = imgWidth;
            h = imgWidth / viewAspect;
            x = 0;
            y = (imgHeight - h) / 2;
        }

        heroBannerImage.setViewport(new Rectangle2D(x, y, w, h));
        heroBannerImage.setFitWidth(viewWidth);
        heroBannerImage.setFitHeight(viewHeight);
    }

    /**
     * Intenta cargar la imagen del banner desde GitHub en un hilo de fondo.
     * Si la descarga falla (sin internet, URL inválida, etc.), se mantiene
     * la imagen local por defecto sin afectar la experiencia del usuario.
     */
    private void loadRemoteBanner() {
        new Thread(() -> {
            try {
                logger.info("Intentando cargar banner remoto desde: {}", REMOTE_BANNER_URL);
                Image remoteImage = new Image(REMOTE_BANNER_URL, true); // backgroundLoading=true

                // Esperar a que termine la carga (con timeout de 10 segundos)
                long startTime = System.currentTimeMillis();
                while (remoteImage.getProgress() < 1.0 && !remoteImage.isError()
                        && (System.currentTimeMillis() - startTime) < 10_000) {
                    Thread.sleep(100);
                }

                if (!remoteImage.isError() && remoteImage.getWidth() > 0) {
                    Platform.runLater(() -> {
                        heroBannerImage.setImage(remoteImage);
                        logger.info("Banner remoto cargado exitosamente ({}x{})",
                                (int) remoteImage.getWidth(), (int) remoteImage.getHeight());
                    });
                } else {
                    logger.info("No se pudo cargar el banner remoto, usando imagen local");
                }
            } catch (Exception e) {
                logger.debug("Banner remoto no disponible (sin internet o URL inválida): {}", e.getMessage());
            }
        }, "BannerRemoteLoader").start();
    }

    /**
     * Configura la información del jugador en el sidebar
     */
    private void setupPlayerInfo() {
        try {
            ConfigService configService = ConfigService.getInstance();
            String playerName = configService.getLauncherConfig().getPlayerName();
            if (playerName != null && !playerName.trim().isEmpty()) {
                playerNameLabel.setText(playerName);
            } else {
                playerNameLabel.setText("Jugador");
            }
        } catch (Exception e) {
            logger.warn("No se pudo cargar el nombre del jugador", e);
            playerNameLabel.setText("Jugador");
        }

        // Intentar crear un avatar por defecto
        if (playerAvatar != null) {
            try {
                // Intentar cargar mini-steve como avatar
                Image avatar = new Image(getClass().getResourceAsStream("/icons/mini-steve.png"));
                playerAvatar.setImage(avatar);
            } catch (Exception e) {
                logger.debug("No se pudo cargar avatar, usando default");
                createDefaultAvatar();
            }
        }
    }

    /**
     * Crea un avatar por defecto
     */
    private void createDefaultAvatar() {
        Canvas canvas = new Canvas(36, 36);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#1a3a4a"));
        gc.fillOval(0, 0, 36, 36);
        gc.setFill(Color.web("#26d9a0"));
        gc.fillText("?", 14, 24);
        WritableImage image = new WritableImage(36, 36);
        canvas.snapshot(null, image);
        playerAvatar.setImage(image);
    }

    /**
     * Actualiza la información del banner hero.
     * Si instance es null, busca la más reciente.
     */
    private void updateBannerInfo(Instance instance) {
        Instance toDisplay = instance;
        List<Instance> instances = instanceService.listInstances();

        if (instances.isEmpty() && toDisplay == null) {
            heroTitle.setText("Biblioteca");
            heroBadge.setText("MULTIMINECRAFT");
            heroBadge.setStyle("");
            lastPlayedLabel.setText("Sin instancias aún");
            heroSubtitle.setText("Crea tu primera instancia para comenzar a jugar.");
            if (heroLoaderLabel != null) {
                heroLoaderLabel.setText("");
                heroLoaderLabel.setStyle("");
            }
            return;
        }

        // Si no se pasó instancia, buscar la última jugada
        if (toDisplay == null) {
            for (Instance inst : instances) {
                if (inst.getLastPlayed() != null) {
                    if (toDisplay == null || inst.getLastPlayed().isAfter(toDisplay.getLastPlayed())) {
                        toDisplay = inst;
                    }
                }
            }
            // Si nadie ha jugado, mostrar la primera de la lista
            if (toDisplay == null && !instances.isEmpty()) {
                toDisplay = instances.get(0);
            }
        }

        if (toDisplay != null) {
            heroTitle.setText(toDisplay.getName());

            boolean isSelected = selectedInstance != null
                    && toDisplay.getName().equals(selectedInstance.getName());
            if (isSelected) {
                heroBadge.setText("Instancias");
                applyHeroLoaderBadgeStyle(heroBadge, toDisplay.getLoader());
            } else {
                heroBadge.setText("ÚLTIMA");
                heroBadge.setStyle("");
            }

            if (toDisplay.getLastPlayed() != null) {
                lastPlayedLabel.setText("Jugaste por última vez " + formatRelativeTime(toDisplay.getLastPlayed()));
            } else {
                lastPlayedLabel.setText("¡Listo para jugar!");
            }

            heroSubtitle.setText("Minecraft " + toDisplay.getVersion());
            if (heroLoaderLabel != null) {
                heroLoaderLabel.setText("· " + toDisplay.getLoader().getDisplayName());
                applyHeroLoaderTypeStyle(heroLoaderLabel, toDisplay.getLoader());
            }
        }
    }

    /**
     * Formatea un LocalDateTime como tiempo relativo (ej: "hace 2 horas")
     */
    private String formatRelativeTime(LocalDateTime dateTime) {
        if (dateTime == null)
            return "";

        LocalDateTime now = LocalDateTime.now();
        long minutes = ChronoUnit.MINUTES.between(dateTime, now);
        long hours = ChronoUnit.HOURS.between(dateTime, now);
        long days = ChronoUnit.DAYS.between(dateTime, now);
        long weeks = days / 7;

        if (minutes < 1)
            return "hace un momento";
        if (minutes < 60)
            return "hace " + minutes + " min";
        if (hours < 24)
            return "hace " + hours + (hours == 1 ? " hora" : " horas");
        if (days < 7)
            return "hace " + days + (days == 1 ? " día" : " días");
        if (weeks < 4)
            return "hace " + weeks + (weeks == 1 ? " semana" : " semanas");
        return "hace más de un mes";
    }

    /**
     * Refresca la lista de instancias desde el disco - Uso público
     */
    public void refreshInstances() {
        Platform.runLater(this::loadInstances);
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

        // Misma instancia seleccionada pero con datos recargados desde disco (p. ej.
        // tras editar)
        if (selectedInstance != null) {
            String selName = selectedInstance.getName();
            instances.stream()
                    .filter(i -> selName.equals(i.getName()))
                    .findFirst()
                    .ifPresent(this::selectInstance);
        }

        // Si hay instancias, seleccionar la primera por defecto
        if (!instances.isEmpty() && selectedInstance == null) {
            selectInstance(instances.get(0));
        }

        // Actualizar selección visual si ya había una seleccionada o mostrar estado por
        // defecto
        if (selectedInstance != null) {
            updateInstanceCardsSelection();
        } else {
            updateSidebarCard(null);
        }
    }

    /**
     * Crea una tarjeta visual moderna para una instancia
     */
    private StackPane createInstanceCard(Instance instance) {
        StackPane cardWrapper = new StackPane();
        cardWrapper.setPrefWidth(CARD_WIDTH);
        cardWrapper.setPrefHeight(CARD_HEIGHT);

        VBox card = new VBox(0);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(CARD_WIDTH);
        card.setPrefHeight(CARD_HEIGHT);
        card.getStyleClass().add("instance-card");

        // Contenedor de imagen con fondo oscuro y bordes redondeados
        StackPane imageContainer = new StackPane();
        imageContainer.getStyleClass().add("instance-card-image-container");
        imageContainer.setPrefHeight(55);
        imageContainer.setMaxHeight(55);
        imageContainer.setMinHeight(55);
        imageContainer.setPadding(new Insets(6));
        imageContainer.setAlignment(Pos.CENTER);

        ImageView iconView = new ImageView();
        iconView.setFitWidth(38);
        iconView.setFitHeight(38);
        iconView.setPreserveRatio(true);

        // Cargar icono de la instancia
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

        imageContainer.getChildren().add(iconView);

        // Sección de información debajo de la imagen
        VBox infoBox = new VBox(2);
        infoBox.setPadding(new Insets(5, 8, 5, 8));
        infoBox.setAlignment(Pos.CENTER);

        // Nombre de la instancia
        Label nameLabel = new Label(instance.getName());
        nameLabel.getStyleClass().add("instance-card-name");
        nameLabel.setWrapText(true);
        nameLabel.setAlignment(Pos.CENTER);
        nameLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        nameLabel.setMaxWidth(CARD_WIDTH - 12);

        // Fila con versión badge y última vez
        HBox metaRow = new HBox(6);
        metaRow.setAlignment(Pos.CENTER);

        String combinedInfo = instance.getVersion() + "-" + instance.getLoader().getDisplayName();
        Label combinedBadge = new Label(combinedInfo);
        combinedBadge.getStyleClass().add("instance-card-version-badge");
        combinedBadge.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        applyLoaderBadgeStyle(combinedBadge, instance.getLoader());

        metaRow.getChildren().add(combinedBadge);

        infoBox.getChildren().addAll(nameLabel, metaRow);

        card.getChildren().addAll(imageContainer, infoBox);

        // Indicador verde para la tarjeta seleccionada
        Circle onlineDot = new Circle(5);
        onlineDot.setFill(Color.web("#26d9a0"));
        onlineDot.setVisible(false);
        StackPane.setAlignment(onlineDot, Pos.TOP_RIGHT);
        StackPane.setMargin(onlineDot, new Insets(8, 8, 0, 0));

        cardWrapper.getChildren().addAll(card, onlineDot);
        cardWrapper.setUserData(instance.getName());

        // Evento de clic izquierdo para seleccionar + doble clic para jugar
        cardWrapper.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                selectInstance(instance);
                if (e.getClickCount() == 2) {
                    onPlayClicked();
                }
            }
        });

        // Menú contextual (clic derecho)
        ContextMenu contextMenu = createInstanceContextMenu(instance);
        cardWrapper.setOnContextMenuRequested(e -> {
            contextMenu.show(cardWrapper, e.getScreenX(), e.getScreenY());
            e.consume();
        });

        return cardWrapper;
    }

    /**
     * Badge verde del banner al seleccionar: borde/texto según Forge (verde),
     * Fabric (azul), Vanilla (amarillo)
     */
    private static void applyHeroLoaderBadgeStyle(Label badge, LoaderType loader) {
        if (loader == null) {
            loader = LoaderType.VANILLA;
        }
        String base = "-fx-font-size: 10px; -fx-font-weight: bold; -fx-border-width: 1; -fx-border-radius: 3; "
                + "-fx-background-radius: 3; -fx-padding: 3 8;";
        switch (loader) {
            case FORGE -> badge.setStyle(base
                    + " -fx-text-fill: #22c55e; -fx-border-color: #22c55e; -fx-background-color: rgba(34, 197, 94, 0.1);");
            case FABRIC -> badge.setStyle(base
                    + " -fx-text-fill: #3b82f6; -fx-border-color: #3b82f6; -fx-background-color: rgba(59, 130, 246, 0.1);");
            case VANILLA -> badge.setStyle(base
                    + " -fx-text-fill: #eab308; -fx-border-color: #eab308; -fx-background-color: rgba(234, 179, 8, 0.12);");
        }
    }

    /** Color del nombre del tipo (Forge/Fabric/Vanilla) en la línea de detalle */
    private static void applyHeroLoaderTypeStyle(Label label, LoaderType loader) {
        if (loader == null) {
            loader = LoaderType.VANILLA;
        }
        switch (loader) {
            case FORGE -> label.setStyle("-fx-text-fill: #22c55e; -fx-font-size: 14px; -fx-font-weight: bold;");
            case FABRIC -> label.setStyle("-fx-text-fill: #3b82f6; -fx-font-size: 14px; -fx-font-weight: bold;");
            case VANILLA -> label.setStyle("-fx-text-fill: #eab308; -fx-font-size: 14px; -fx-font-weight: bold;");
        }
    }

    /** Colores del badge de versión: Forge verde, Fabric azul, Vanilla amarillo */
    private static void applyLoaderBadgeStyle(Label badge, LoaderType loader) {
        if (loader == null) {
            loader = LoaderType.VANILLA;
        }
        switch (loader) {
            case FORGE -> badge.setStyle(
                    "-fx-text-fill: #22c55e; -fx-background-color: rgba(34, 197, 94, 0.18);");
            case FABRIC -> badge.setStyle(
                    "-fx-text-fill: #3b82f6; -fx-background-color: rgba(59, 130, 246, 0.18);");
            case VANILLA -> badge.setStyle(
                    "-fx-text-fill: #eab308; -fx-background-color: rgba(234, 179, 8, 0.2);");
        }
    }

    /**
     * Crea el menú contextual para una instancia
     */
    private ContextMenu createInstanceContextMenu(Instance instance) {
        ContextMenu menu = new ContextMenu();

        MenuItem playItem = new MenuItem("▶  Jugar");
        playItem.setOnAction(e -> {
            selectInstance(instance);
            onPlayClicked();
        });

        MenuItem modifyItem = new MenuItem("⚙  Modificar");
        modifyItem.setOnAction(e -> {
            selectInstance(instance);
            onModifyAction();
        });

        MenuItem resourcesItem = new MenuItem("⊕  Recursos");
        resourcesItem.setOnAction(e -> {
            selectInstance(instance);
            onRecursosAction();
        });

        MenuItem mapsItem = new MenuItem("🗺  Mapas");
        mapsItem.setOnAction(e -> {
            selectInstance(instance);
            onMapsAction();
        });

        MenuItem locationItem = new MenuItem("📂  Ver Ubicación");
        locationItem.setOnAction(e -> {
            selectInstance(instance);
            onViewLocationAction();
        });

        SeparatorMenuItem separator = new SeparatorMenuItem();

        MenuItem deleteItem = new MenuItem("🗑  Eliminar");
        deleteItem.setOnAction(e -> {
            selectInstance(instance);
            onDeleteAction();
        });

        menu.getItems().addAll(playItem, new SeparatorMenuItem(), modifyItem, resourcesItem, mapsItem, locationItem,
                separator, deleteItem);

        return menu;
    }

    /**
     * Crea la tarjeta "Nueva Instancia" para el grid
     */
    private StackPane createNewInstanceCard() {
        StackPane cardWrapper = new StackPane();
        cardWrapper.setPrefWidth(CARD_WIDTH);
        cardWrapper.setPrefHeight(CARD_HEIGHT);

        VBox card = new VBox(8);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(10, 6, 8, 6));
        card.setPrefWidth(CARD_WIDTH);
        card.setPrefHeight(CARD_HEIGHT);
        card.getStyleClass().add("new-instance-card");

        Label plusLabel = new Label("+");
        plusLabel.getStyleClass().add("new-instance-plus");

        Label textLabel = new Label("Nueva Instancia");
        textLabel.getStyleClass().add("new-instance-label");

        card.getChildren().addAll(plusLabel, textLabel);
        cardWrapper.getChildren().add(card);

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

        // Actualizar el banner superior
        updateBannerInfo(instance);

        // Actualizar la tarjeta del sidebar
        updateSidebarCard(instance);

        // Resaltar la tarjeta seleccionada en el grid
        updateInstanceCardsSelection();
    }

    /**
     * Actualiza la tarjeta de previsualización en el sidebar
     */
    private void updateSidebarCard(Instance instance) {
        if (instance == null) {
            // Ocultar elementos y quitar del layout para permitir centrado real
            selectedInstanceSidebarName.setVisible(false);
            selectedInstanceSidebarName.setManaged(false);
            selectedInstanceSidebarVersion.setVisible(false);
            selectedInstanceSidebarVersion.setManaged(false);
            sidebarPlayButton.setVisible(false);
            sidebarPlayButton.setManaged(false);

            // Deshabilitar botones de navegación si no hay selección
            navModpacksButton.setDisable(true);
            navResourcePacksButton.setDisable(true);
            navMapsButton.setDisable(true);

            // Mantener el tamaño original de la tarjeta mediante minHeight
            selectedInstanceCard.setMinHeight(160);

            // Cargar logo de Monkey Studio más grande y centrado
            try {
                Image logo = new Image(getClass().getResourceAsStream("/recursos2/Monkey-Logo.png"));
                selectedInstanceSidebarIcon.setImage(logo);
                selectedInstanceSidebarIcon.setFitHeight(100);
                selectedInstanceSidebarIcon.setFitWidth(100);
            } catch (Exception e) {
                logger.warn("No se pudo cargar el logo Monkey-Logo.png", e);
            }
            return;
        }

        // Mostrar elementos si hay una instancia seleccionada
        selectedInstanceSidebarName.setVisible(true);
        selectedInstanceSidebarName.setManaged(true);
        selectedInstanceSidebarVersion.setVisible(true);
        selectedInstanceSidebarVersion.setManaged(true);
        sidebarPlayButton.setVisible(true);
        sidebarPlayButton.setManaged(true);

        // Habilitar botones de navegación
        navModpacksButton.setDisable(false);
        navResourcePacksButton.setDisable(false);
        navMapsButton.setDisable(false);

        // Quitar restricción de altura mínima
        selectedInstanceCard.setMinHeight(javafx.scene.layout.Region.USE_COMPUTED_SIZE);

        // Restaurar tamaño estándar del icono
        selectedInstanceSidebarIcon.setFitHeight(56);
        selectedInstanceSidebarIcon.setFitWidth(56);

        selectedInstanceSidebarName.setText(instance.getName());

        // Formato: Minecraft 1.21.1 Fabric
        String versionText = "Minecraft " + instance.getVersion() + " " + instance.getLoader().getDisplayName();
        selectedInstanceSidebarVersion.setText(versionText);

        // Cargar icono
        String iconName = instance.getIcon();
        if (iconName != null && !iconName.isEmpty()) {
            Image iconImage = loadInstanceIcon(iconName);
            if (iconImage != null) {
                selectedInstanceSidebarIcon.setImage(iconImage);
            }
        }
    }

    /**
     * Acción cuando se hace clic en Jugar desde el sidebar
     */
    @FXML
    private void onSidebarPlayClicked() {
        if (selectedInstance == null) {
            showError("Error", "Por favor selecciona una instancia primero.");
            return;
        }

        logger.info("Iniciando instancia desde el sidebar: {}", selectedInstance.getName());

        try {
            // Aquí llamaríamos a la lógica de lanzamiento.
            // Para simplificar y dado que ya existe la lógica de LaunchService:
            launchService.launchInstance(selectedInstance);

            // Opcional: mostrar algún feedback visual en el botón
            sidebarPlayButton.setText("¡Lanzando!");
            sidebarPlayButton.setDisable(true);

            // Rehabilitar después de un tiempo
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                }
                Platform.runLater(() -> {
                    sidebarPlayButton.setText("▶ Jugar");
                    sidebarPlayButton.setDisable(false);
                });
            }).start();

        } catch (Exception e) {
            logger.error("Error al lanzar la instancia desde el sidebar", e);
            showError("Error de Lanzamiento", "No se pudo iniciar el juego: " + e.getMessage());
        }
    }

    /**
     * Actualiza la selección visual de las tarjetas de instancias
     */
    private void updateInstanceCardsSelection() {
        for (var node : instancesGrid.getChildren()) {
            if (node instanceof StackPane) {
                StackPane wrapper = (StackPane) node;
                String instanceName = (String) wrapper.getUserData();

                VBox card = null;
                Circle dot = null;
                for (var child : wrapper.getChildren()) {
                    if (child instanceof VBox)
                        card = (VBox) child;
                    if (child instanceof Circle)
                        dot = (Circle) child;
                }

                if (card != null && instanceName != null) {
                    boolean isSelected = selectedInstance != null && instanceName.equals(selectedInstance.getName());
                    if (isSelected) {
                        if (!card.getStyleClass().contains("instance-card-selected")) {
                            card.getStyleClass().add("instance-card-selected");
                        }
                        if (dot != null)
                            dot.setVisible(true);
                    } else {
                        card.getStyleClass().remove("instance-card-selected");
                        if (dot != null)
                            dot.setVisible(false);
                    }
                }
            }
        }
    }

    // ==================== ACCIONES DE LA BARRA DE TÍTULO ====================

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

    // ==================== NAVEGACIÓN DEL SIDEBAR ====================

    private void setActiveNavButton(Button active) {
        Button[] navButtons = { navModpacksButton, navResourcePacksButton, navMapsButton,
                navConfigButton };
        for (Button btn : navButtons) {
            btn.getStyleClass().remove("nav-button-active");
        }
        if (active != null && !active.getStyleClass().contains("nav-button-active")) {
            active.getStyleClass().add("nav-button-active");
        }
    }

    @FXML
    private void onSupportLinkClicked() {
        logger.info("Abriendo enlace de soporte: https://monkeystudio.netlify.app/");
        App.openWebPage("https://monkeystudio.netlify.app/");
    }

    @FXML
    private void onNavModpacksClicked() {
        restoreMainContent();
        if (selectedInstance == null) {
            AlertUtil.showWarning("No hay instancia seleccionada", "Por favor, selecciona una instancia primero.");
            return;
        }

        try {
            logger.info("Abriendo ventana de edición para: {}", selectedInstance.getName());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CrearEditar.fxml"));
            Parent root = loader.load();

            CrearEditarController controller = loader.getController();
            controller.setInstanceToEdit(selectedInstance);

            Stage stage = new Stage();
            stage.setTitle("Editar Instancia - " + selectedInstance.getName());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(App.getPrimaryStage());
            stage.initStyle(javafx.stage.StageStyle.TRANSPARENT);

            Scene scene = new Scene(root);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource("/css/dark-theme.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource("/css/create-instance.css").toExternalForm());
            stage.setScene(scene);
            stage.setResizable(false);

            stage.showAndWait();

            // Refrescar UI después de editar (loadInstances re-sincroniza la instancia
            // seleccionada desde disco)
            loadInstances();

        } catch (Exception e) {
            logger.error("Error al abrir ventana de edición", e);
            AlertUtil.showError("Error", "No se pudo abrir la ventana de edición: " + e.getMessage());
        }
    }

    @FXML
    private void onNavResourcePacksClicked() {
        restoreMainContent();
        if (selectedInstance == null) {
            AlertUtil.showWarning("No hay instancia seleccionada", "Selecciona una instancia para abrir su carpeta.");
            return;
        }

        logger.info("Abriendo carpeta de la instancia: {}", selectedInstance.getName());
        instanceService.openInstanceFolder(selectedInstance.getName());
    }

    @FXML
    private void onNavMapsClicked() {
        restoreMainContent();
        if (selectedInstance == null) {
            AlertUtil.showWarning("No hay instancia seleccionada", "Selecciona una instancia para eliminarla.");
            return;
        }

        boolean confirmed = AlertUtil.showConfirmation("Eliminar Instancia",
                "¿Estás seguro de que deseas eliminar la instancia \"" + selectedInstance.getName() + "\"?\n\n" +
                        "Esta acción eliminará todos tus mundos, mods y configuraciones de forma permanente.");

        if (confirmed) {
            try {
                logger.warn("Eliminando instancia: {}", selectedInstance.getName());
                instanceService.deleteInstance(selectedInstance.getName());

                selectedInstance = null;
                updateSidebarCard(null);
                loadInstances();

                AlertUtil.showInfo("Instancia eliminada", "La instancia se ha eliminado correctamente.");
            } catch (Exception e) {
                logger.error("Error al eliminar instancia", e);
                AlertUtil.showError("Error", "No se pudo eliminar la instancia: " + e.getMessage());
            }
        }
    }

    @FXML
    private void onNavConfigClicked() {
        setActiveNavButton(navConfigButton);

        // Si ya está mostrando la vista del servidor, no hacer nada
        if (vistaServidorNode != null) {
            return;
        }

        try {
            logger.info("Abriendo Vista_Servidor dentro del área central");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Vista_Servidor.fxml"));
            Parent root = loader.load();
            vistaServidorNode = root;

            // Ocultar contenido principal y mostrar Vista_Servidor
            mainContentArea.setVisible(false);
            mainContentArea.setManaged(false);

            centerStack.getChildren().add(vistaServidorNode);
            logger.info("Vista_Servidor mostrada en el área central");

        } catch (IOException e) {
            logger.error("Error al abrir Vista_Servidor", e);
            AlertUtil.showError("Error", "No se pudo abrir la vista del servidor: " + e.getMessage());
        }
    }

    /**
     * Restaura el contenido principal ocultando la Vista_Servidor.
     * Llamado desde Vista_ServidorController al cerrar la vista.
     */
    public void restoreMainContent() {
        if (vistaServidorNode != null) {
            centerStack.getChildren().remove(vistaServidorNode);
            vistaServidorNode = null;
        }

        mainContentArea.setVisible(true);
        mainContentArea.setManaged(true);

        // Quitar el estilo activo del botón de servidor
        navConfigButton.getStyleClass().remove("nav-button-active");
    }

    /**
     * Refresca la información del jugador en el sidebar.
     * Llamado desde ConfiguracionController después de guardar cambios.
     */
    public void refreshPlayerInfo() {
        Platform.runLater(this::setupPlayerInfo);
    }

    // ==================== ACCIONES DE INSTANCIAS ====================

    @FXML
    private void onPlayClicked() {
        if (selectedInstance == null)
            return;

        logger.info("Iniciando juego para instancia: {}", selectedInstance.getName());

        // Deshabilitar botones mientras se lanza
        createInstanceButton.setDisable(true);

        // Mostrar y configurar barra de progreso
        if (progressBar != null) {
            progressContainer.setVisible(true);
            progressBar.setProgress(0.01);
            progressBar.setStyle("-fx-accent: #4CAF50; -fx-control-inner-background: #2d2d2d;");
            if (progressLabel != null)
                progressLabel.setText("1%");
        }

        // Lanzar en un hilo separado para no bloquear la UI
        new Thread(() -> {
            try {
                Thread.sleep(50);

                updateProgress(0.1);
                Thread.sleep(100);

                updateProgress(0.2);
                Thread.sleep(100);

                logger.debug("Lanzando proceso de Minecraft...");
                Process process = launchService.launchInstance(selectedInstance);

                updateProgress(0.4);
                Thread.sleep(200);

                int maxWaitTime = 30000;
                long startTime = System.currentTimeMillis();
                double baseProgress = 0.4;

                while (process.isAlive() && (System.currentTimeMillis() - startTime) < maxWaitTime) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    double timeProgress = Math.min(0.95, baseProgress + (elapsed / (double) maxWaitTime) * 0.55);

                    updateProgress(timeProgress);

                    Thread.sleep(200);

                    if (!process.isAlive()) {
                        int exitCode = process.exitValue();
                        if (exitCode != 0) {
                            Platform.runLater(() -> {
                                if (progressContainer != null)
                                    progressContainer.setVisible(false);
                                createInstanceButton.setDisable(false);
                                showError("Error al iniciar el juego",
                                        "Minecraft se cerró con código de error: " + exitCode);
                            });
                            return;
                        }
                        break;
                    }
                }

                updateProgress(1.0);

                logger.debug("Esperando a que aparezca la ventana de Minecraft...");
                int maxWindowWaitTime = 15000;
                long windowStartTime = System.currentTimeMillis();
                boolean windowDetected = false;

                while (!windowDetected && process.isAlive() &&
                        (System.currentTimeMillis() - windowStartTime) < maxWindowWaitTime) {

                    if (PlatformUtil.hasVisibleWindow(process)) {
                        windowDetected = true;
                        logger.info("Ventana de Minecraft detectada (método 1) - cerrando launcher");
                        break;
                    }

                    if (PlatformUtil.findMinecraftWindow()) {
                        windowDetected = true;
                        logger.info("Ventana de Minecraft detectada (método 2) - cerrando launcher");
                        break;
                    }

                    Thread.sleep(200);
                }

                if (!windowDetected && process.isAlive()) {
                    logger.debug("Ventana no detectada automáticamente, esperando tiempo adicional...");
                    Thread.sleep(2000);
                }

                Platform.runLater(() -> {
                    try {
                        Stage mainStage = (Stage) createInstanceButton.getScene().getWindow();
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
                    createInstanceButton.setDisable(false);
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
                double clampedProgress = Math.max(0.0, Math.min(1.0, progress));
                progressBar.setProgress(clampedProgress);
                progressBar.setStyle("-fx-accent: #4CAF50; -fx-control-inner-background: #2d2d2d;");
                if (progressLabel != null) {
                    progressLabel.setText((int) (clampedProgress * 100) + "%");
                }
                logger.debug("Progreso actualizado: {}% (valor: {})", (int) (clampedProgress * 100), clampedProgress);
            }
        });
    }

    private void onModifyAction() {
        if (selectedInstance == null)
            return;
        logger.debug("Modificar instancia: {}", selectedInstance.getName());
        showError("Funcionalidad pendiente", "La modificación de instancias estará disponible en una futura versión.");
    }

    private void onRecursosAction() {
        if (selectedInstance == null)
            return;
        logger.debug("Recursos de instancia: {}", selectedInstance.getName());

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Recursos.fxml"));
            Parent recursosView = loader.load();

            RecursosController controller = loader.getController();
            if (controller != null) {
                controller.setInstance(selectedInstance);
            }

            Stage recursosStage = new Stage();
            recursosStage.setTitle("Recursos");
            recursosStage.initModality(Modality.WINDOW_MODAL);

            if (createInstanceButton != null && createInstanceButton.getScene() != null) {
                recursosStage.initOwner(createInstanceButton.getScene().getWindow());
            }

            Scene scene = new Scene(recursosView, 400, 250);
            scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource("/css/dark-theme.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource("/css/recursos-view.css").toExternalForm());

            recursosStage.setScene(scene);
            recursosStage.setMinWidth(350);
            recursosStage.setMinHeight(250);
            recursosStage.showAndWait();

        } catch (Exception e) {
            logger.error("Error al abrir ventana de recursos", e);
            showError("Error", "No se pudo abrir la ventana de recursos: " + e.getMessage());
        }
    }

    private void onMapsAction() {
        if (selectedInstance == null)
            return;
        logger.debug("Abrir mapas de instancia: {}", selectedInstance.getName());

        try {
            ConfigService configService = ConfigService.getInstance();
            Path minecraftDir = configService.getInstanceMinecraftDirectory(selectedInstance.getName());
            Path savesDir = minecraftDir.resolve("saves");

            File dir = savesDir.toFile();
            if (!dir.exists()) {
                dir.mkdirs();
                logger.info("Carpeta saves creada: {}", savesDir);
            }

            openFolderInExplorer(savesDir);

        } catch (Exception e) {
            logger.error("Error al abrir carpeta de mapas", e);
            showError("Error", "No se pudo abrir la carpeta de mapas: " + e.getMessage());
        }
    }

    private void onViewLocationAction() {
        if (selectedInstance == null)
            return;
        logger.debug("Ver ubicación de instancia: {}", selectedInstance.getName());
        instanceService.openInstanceFolder(selectedInstance.getName());
    }

    private void onDeleteAction() {
        if (selectedInstance == null)
            return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmar eliminación");
        alert.setHeaderText("¿Eliminar instancia?");
        alert.setContentText("¿Estás seguro de que deseas eliminar la instancia \"" + selectedInstance.getName()
                + "\"?\nEsta acción no se puede deshacer.");
        AlertUtil.styleAlert(alert);

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    instanceService.deleteInstance(selectedInstance.getName());
                    logger.info("Instancia eliminada: {}", selectedInstance.getName());
                    selectedInstance = null;
                    loadInstances();
                    updateBannerInfo(null);
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CrearEditar.fxml"));
            Parent createInstanceView = loader.load();

            Stage createInstanceStage = new Stage();
            createInstanceStage.setTitle("Crear Nueva Instancia");
            createInstanceStage.initModality(Modality.WINDOW_MODAL);
            createInstanceStage.initOwner(createInstanceButton.getScene().getWindow());
            createInstanceStage.initStyle(javafx.stage.StageStyle.TRANSPARENT);

            Scene scene = new Scene(createInstanceView);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource("/css/dark-theme.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource("/css/create-instance.css").toExternalForm());

            createInstanceStage.setScene(scene);
            createInstanceStage.setResizable(false);

            createInstanceStage.showAndWait();

            // Recargar instancias después de cerrar la ventana
            loadInstances();
            updateBannerInfo(null);

        } catch (IOException e) {
            logger.error("Error al abrir ventana de crear instancia", e);
            showError("Error", "No se pudo abrir la ventana de crear instancia: " + e.getMessage());
        }
    }

    /**
     * Carga un icono de instancia desde la carpeta de recursos o ruta absoluta
     */
    private Image loadInstanceIcon(String iconName) {
        try {
            String resourcePath = "/icons/" + iconName;
            java.io.InputStream resourceStream = getClass().getResourceAsStream(resourcePath);
            if (resourceStream != null) {
                logger.debug("Icono cargado desde recursos: {}", resourcePath);
                return new Image(resourceStream);
            }

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

        gc.setFill(Color.web("#1a3a4a"));
        gc.fillRoundRect(0, 0, size, size, 12, 12);

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
                    Runtime.getRuntime().exec(new String[] { "open", folder.getAbsolutePath() });
                    break;
                case LINUX:
                    Runtime.getRuntime().exec(new String[] { "xdg-open", folder.getAbsolutePath() });
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
