package com.multiminecraft.launcher.controller;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Controlador para la ventana de selección de iconos
 */
public class SelectIconController {
    
    private static final Logger logger = LoggerFactory.getLogger(SelectIconController.class);
    
    @FXML private FlowPane iconsContainer;
    @FXML private ImageView previewImageView;
    
    private String selectedIconName;
    private List<String> availableIcons;
    
    @FXML
    public void initialize() {
        logger.info("Inicializando ventana de selección de iconos");
        loadIcons();
    }
    
    /**
     * Carga todos los iconos disponibles desde la carpeta de recursos
     */
    private void loadIcons() {
        availableIcons = new ArrayList<>();
        iconsContainer.getChildren().clear();
        
        try {
            // Obtener la URL de la carpeta de iconos
            URL iconsUrl = getClass().getResource("/icons");
            
            if (iconsUrl != null) {
                URI iconsUri = iconsUrl.toURI();
                Path iconsPath;
                
                // Manejar tanto JAR como sistema de archivos
                if (iconsUri.getScheme().equals("jar")) {
                    FileSystem fileSystem = FileSystems.newFileSystem(iconsUri, Collections.emptyMap());
                    iconsPath = fileSystem.getPath("/icons");
                } else {
                    iconsPath = Paths.get(iconsUri);
                }
                
                // Listar todos los archivos en la carpeta de iconos
                try (Stream<Path> paths = Files.list(iconsPath)) {
                    paths.filter(Files::isRegularFile)
                         .filter(path -> {
                             String fileName = path.getFileName().toString().toLowerCase();
                             return fileName.endsWith(".png") || 
                                    fileName.endsWith(".jpg") || 
                                    fileName.endsWith(".jpeg") ||
                                    fileName.endsWith(".gif");
                         })
                         .sorted()
                         .forEach(path -> {
                             String iconName = path.getFileName().toString();
                             loadIcon(iconName);
                         });
                }
                
                // Cerrar el FileSystem si fue creado
                if (iconsUri.getScheme().equals("jar")) {
                    iconsPath.getFileSystem().close();
                }
            }
        } catch (Exception e) {
            logger.error("Error al cargar iconos desde recursos", e);
            // Fallback: cargar iconos conocidos
            loadKnownIcons();
        }
        
        logger.info("Cargados {} iconos", availableIcons.size());
        
        // Si no hay iconos, mostrar mensaje
        if (availableIcons.isEmpty()) {
            Label noIconsLabel = new Label("No hay iconos disponibles");
            noIconsLabel.setStyle("-fx-text-fill: gray;");
            iconsContainer.getChildren().add(noIconsLabel);
        }
    }
    
    /**
     * Carga un icono específico
     */
    private void loadIcon(String iconName) {
        String resourcePath = "/icons/" + iconName;
        InputStream iconStream = getClass().getResourceAsStream(resourcePath);
        
        if (iconStream != null) {
            try {
                Image iconImage = new Image(iconStream);
                VBox iconCard = createIconCard(iconName, iconImage);
                iconsContainer.getChildren().add(iconCard);
                availableIcons.add(iconName);
            } catch (Exception e) {
                logger.warn("No se pudo cargar el icono: {}", iconName, e);
            }
        }
    }
    
    /**
     * Método de respaldo para cargar iconos conocidos si falla la carga dinámica
     */
    private void loadKnownIcons() {
        String[] iconNames = {
            "art-Crafting_Table.png", "art-Creeper.png", "art-Diamond_Sword.png",
            "art-Enderman.png", "art-Ghast.png", "art-Gold_Sword.png",
            "art-Grass.png", "art-Iron_Sword.png", "art-TNT.png",
            "art-Wooden_Sword.png", "art-Zombie.png",
            "block-chest.png", "block-crafting.png", "block-Diamond_ore.png",
            "block-diamond-block.png", "block-emerald-block.png", "block-Furnace.png",
            "block-grass.png", "block-tnt.png",
            "mini-creeper.png", "mini-steve.png", "mini-zombie.png",
            "mobs-creeper_2.png", "mobs-enderman_2.png", "mobs-skeleton.png",
            "mobs-zombie_2.png", "mobs-spider.png",
            "sword-1.png", "sword-2.png", "sword-3.png",
            "otro-minecraft.png", "otro-perla.png"
        };
        
        for (String iconName : iconNames) {
            loadIcon(iconName);
        }
    }
    
    /**
     * Crea una tarjeta visual para un icono
     */
    private VBox createIconCard(String iconName, Image iconImage) {
        VBox card = new VBox(5);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(8));
        card.setPrefWidth(80);
        card.setPrefHeight(100);
        card.getStyleClass().add("background-secondary");
        card.setStyle("-fx-background-radius: 5px;");
        
        ImageView iconView = new ImageView(iconImage);
        iconView.setFitWidth(64);
        iconView.setFitHeight(64);
        iconView.setPreserveRatio(true);
        
        Label nameLabel = new Label(iconName.length() > 15 ? iconName.substring(0, 12) + "..." : iconName);
        nameLabel.setStyle("-fx-font-size: 9px; -fx-text-alignment: center;");
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(75);
        
        card.getChildren().addAll(iconView, nameLabel);
        
        // Evento de clic para seleccionar
        card.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            selectIcon(iconName, iconImage);
        });
        
        // Efecto hover
        card.setOnMouseEntered(e -> {
            card.setStyle("-fx-background-color: derive(-fx-base, 20%); -fx-background-radius: 5px;");
        });
        card.setOnMouseExited(e -> {
            card.setStyle("-fx-background-color: -fx-background-secondary; -fx-background-radius: 5px;");
        });
        
        return card;
    }
    
    /**
     * Selecciona un icono
     */
    private void selectIcon(String iconName, Image iconImage) {
        selectedIconName = iconName;
        previewImageView.setImage(iconImage);
        
        // Resaltar la tarjeta seleccionada
        for (var node : iconsContainer.getChildren()) {
            if (node instanceof VBox) {
                VBox card = (VBox) node;
                if (card.getChildren().size() >= 2 && card.getChildren().get(1) instanceof Label) {
                    Label label = (Label) card.getChildren().get(1);
                    String cardIconName = label.getText();
                    if (iconName.startsWith(cardIconName.replace("...", ""))) {
                        card.setStyle("-fx-background-color: derive(-fx-accent, 30%); -fx-background-radius: 5px; -fx-border-color: -fx-accent; -fx-border-width: 2px;");
                    } else {
                        card.setStyle("-fx-background-color: -fx-background-secondary; -fx-background-radius: 5px;");
                    }
                }
            }
        }
        
        logger.debug("Icono seleccionado: {}", iconName);
    }
    
    @FXML
    private void onSelect() {
        if (selectedIconName != null) {
            // Cerrar la ventana y retornar el icono seleccionado
            Stage stage = (Stage) previewImageView.getScene().getWindow();
            if (stage != null) {
                stage.setUserData(selectedIconName);
                stage.close();
            }
        }
    }
    
    @FXML
    private void onClose() {
        Stage stage = (Stage) previewImageView.getScene().getWindow();
        if (stage != null) {
            stage.close();
        }
    }
    
    /**
     * Obtiene el nombre del icono seleccionado
     */
    public String getSelectedIcon() {
        return selectedIconName;
    }
}

