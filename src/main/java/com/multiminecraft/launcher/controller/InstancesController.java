package com.multiminecraft.launcher.controller;

import com.multiminecraft.launcher.App;
import com.multiminecraft.launcher.model.Instance;
import com.multiminecraft.launcher.service.InstanceService;
import com.multiminecraft.launcher.service.LaunchService;
import com.multiminecraft.launcher.util.AlertUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Controlador de la vista de instancias
 */
public class InstancesController {
    
    private static final Logger logger = LoggerFactory.getLogger(InstancesController.class);
    
    @FXML private FlowPane instancesContainer;
    @FXML private VBox emptyStateContainer;
    
    private InstanceService instanceService;
    private LaunchService launchService;
    
    @FXML
    public void initialize() {
        logger.info("Inicializando vista de instancias");
        
        instanceService = new InstanceService();
        launchService = new LaunchService();
        
        loadInstances();
    }
    
    /**
     * Carga todas las instancias
     */
    private void loadInstances() {
        instancesContainer.getChildren().clear();
        
        List<Instance> instances = instanceService.listInstances();
        
        if (instances.isEmpty()) {
            emptyStateContainer.setVisible(true);
            instancesContainer.setVisible(false);
        } else {
            emptyStateContainer.setVisible(false);
            instancesContainer.setVisible(true);
            
            for (Instance instance : instances) {
                instancesContainer.getChildren().add(createInstanceCard(instance));
            }
        }
        
        logger.info("Cargadas {} instancias", instances.size());
    }
    
    /**
     * Crea una tarjeta visual para una instancia
     */
    private VBox createInstanceCard(Instance instance) {
        VBox card = new VBox(10);
        card.getStyleClass().add("instance-card");
        card.setPrefWidth(300);
        card.setPadding(new Insets(15));
        
        // Nombre
        Label nameLabel = new Label(instance.getName());
        nameLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        
        // Descripción
        Label descLabel = new Label(instance.getDescription());
        descLabel.setStyle("-fx-text-fill: gray;");
        
        // Botones
        HBox buttonsBox = new HBox(10);
        buttonsBox.setAlignment(Pos.CENTER);
        
        Button playButton = new Button("▶ Jugar");
        playButton.getStyleClass().add("button-primary");
        playButton.setOnAction(e -> onPlayInstance(instance));
        
        Button folderButton = new Button("📁");
        folderButton.setOnAction(e -> onOpenFolder(instance));
        
        Button deleteButton = new Button("🗑");
        deleteButton.getStyleClass().add("button-danger");
        deleteButton.setOnAction(e -> onDeleteInstance(instance));
        
        buttonsBox.getChildren().addAll(playButton, folderButton, deleteButton);
        
        card.getChildren().addAll(nameLabel, descLabel, buttonsBox);
        
        return card;
    }
    
    /**
     * Lanza una instancia
     */
    private void onPlayInstance(Instance instance) {
        logger.info("Lanzando instancia: {}", instance.getName());
        
        new Thread(() -> {
            try {
                launchService.launchInstance(instance);
                
            } catch (Exception e) {
                logger.error("Error al lanzar instancia", e);
                
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText("No se pudo lanzar Minecraft");
                    alert.setContentText(e.getMessage());
                    AlertUtil.styleAlert(alert);
                    alert.showAndWait();
                });
            }
        }).start();
    }
    
    /**
     * Abre la carpeta de una instancia
     */
    private void onOpenFolder(Instance instance) {
        logger.info("Abriendo carpeta de instancia: {}", instance.getName());
        instanceService.openInstanceFolder(instance.getName());
    }
    
    /**
     * Elimina una instancia
     */
    private void onDeleteInstance(Instance instance) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirmar eliminación");
        confirmAlert.setHeaderText("¿Eliminar instancia?");
        confirmAlert.setContentText("¿Estás seguro de que deseas eliminar la instancia \"" + instance.getName() + "\"?\nEsta acción no se puede deshacer.");
        AlertUtil.styleAlert(confirmAlert);
        
        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    instanceService.deleteInstance(instance.getName());
                    logger.info("Instancia eliminada: {}", instance.getName());
                    loadInstances(); // Recargar lista
                    
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("Instancia eliminada");
                    successAlert.setHeaderText(null);
                    successAlert.setContentText("La instancia ha sido eliminada correctamente");
                    AlertUtil.styleAlert(successAlert);
                    successAlert.showAndWait();
                    
                } catch (Exception e) {
                    logger.error("Error al eliminar instancia", e);
                    
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("Error");
                    errorAlert.setHeaderText("No se pudo eliminar la instancia");
                    errorAlert.setContentText(e.getMessage());
                    AlertUtil.styleAlert(errorAlert);
                    errorAlert.showAndWait();
                }
            }
        });
    }
    
    @FXML
    private void onCreateNewInstance() {
        logger.debug("Navegando a crear nueva instancia");
        try {
            App.setRoot("CreateInstanceView");
        } catch (Exception e) {
            logger.error("Error al cambiar vista", e);
        }
    }
}
