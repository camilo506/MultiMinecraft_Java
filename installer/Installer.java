package installer;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Instalador del launcher
 * Crea el directorio de instalación, copia archivos y crea acceso directo
 */
public class Installer extends Application {
    
    private static final Logger logger = LoggerFactory.getLogger(Installer.class);
    private static final String APP_TITLE = "Minecraft Launcher - Instalador";
    private static final int WINDOW_WIDTH = 500;
    private static final int WINDOW_HEIGHT = 450;
    
    private CheckBox desktopShortcutCheckbox;
    private Button installButton;
    private Button cancelButton;
    private ProgressBar progressBar;
    private Label statusLabel;
    
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle(APP_TITLE);
        primaryStage.setWidth(WINDOW_WIDTH);
        primaryStage.setHeight(WINDOW_HEIGHT);
        primaryStage.setResizable(false);
        
        // Crear interfaz
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);
        
        // Título
        Label titleLabel = new Label("Instalador de MultiMinecraft Launcher");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        
        // Checkbox para acceso directo
        desktopShortcutCheckbox = new CheckBox("Crear acceso directo en el escritorio");
        desktopShortcutCheckbox.setSelected(true);
        
        // Barra de progreso
        progressBar = new ProgressBar();
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        progressBar.setVisible(false);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        
        // Etiqueta de estado
        statusLabel = new Label("Listo para instalar");
        statusLabel.setWrapText(true);
        
        // Botones
        installButton = new Button("Instalar Launcher");
        installButton.setPrefWidth(150);
        installButton.setOnAction(e -> installLauncher());
        
        cancelButton = new Button("Cancelar");
        cancelButton.setPrefWidth(150);
        cancelButton.setOnAction(e -> primaryStage.close());
        
        // Layout de botones
        VBox buttonBox = new VBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().addAll(installButton, cancelButton);
        
        root.getChildren().addAll(
                titleLabel,
                desktopShortcutCheckbox,
                progressBar,
                statusLabel,
                buttonBox
        );
        
        // Aplicar tema oscuro
        root.setStyle("-fx-background-color: #2b2b2b;");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");
        desktopShortcutCheckbox.setStyle("-fx-text-fill: white;");
        statusLabel.setStyle("-fx-text-fill: white;");
        
        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/css/dark-theme.css").toExternalForm());
        
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    
    /**
     * Instala el launcher
     */
    private void installLauncher() {
        installButton.setDisable(true);
        cancelButton.setDisable(true);
        progressBar.setVisible(true);
        statusLabel.setText("Instalando...");
        
        // Ejecutar instalación en hilo separado
        new Thread(() -> {
            try {
                // 1. Crear directorio de instalación
                statusLabel.setText("Creando directorio de instalación...");
                Path installDir = getInstallDirectory();
                Files.createDirectories(installDir);
                logger.info("Directorio de instalación creado: {}", installDir);
                
                // 2. Copiar archivos del launcher
                statusLabel.setText("Copiando archivos del launcher...");
                copyLauncherFiles(installDir);
                logger.info("Archivos del launcher copiados");
                
                // 3. Crear acceso directo si está marcado
                if (desktopShortcutCheckbox.isSelected()) {
                    statusLabel.setText("Creando acceso directo...");
                    createDesktopShortcut(installDir);
                    logger.info("Acceso directo creado");
                }
                
                // 4. Crear archivo de configuración inicial
                statusLabel.setText("Creando configuración inicial...");
                createInitialConfig(installDir);
                logger.info("Configuración inicial creada");
                
                // 5. Mostrar mensaje de éxito
                javafx.application.Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("¡Instalación completada exitosamente!");
                    
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Instalación completada");
                    alert.setHeaderText("El launcher se ha instalado correctamente");
                    alert.setContentText("El launcher se ha instalado en:\n" + installDir);
                    alert.showAndWait();
                    
                    // Cerrar instalador
                    Stage stage = (Stage) installButton.getScene().getWindow();
                    stage.close();
                });
                
            } catch (Exception e) {
                logger.error("Error durante la instalación", e);
                javafx.application.Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("Error: " + e.getMessage());
                    
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error de instalación");
                    alert.setHeaderText("No se pudo completar la instalación");
                    alert.setContentText("Error: " + e.getMessage());
                    alert.showAndWait();
                    
                    installButton.setDisable(false);
                    cancelButton.setDisable(false);
                });
            }
        }).start();
    }
    
    /**
     * Obtiene el directorio de instalación
     */
    private Path getInstallDirectory() {
        String appData = System.getenv("APPDATA");
        if (appData == null) {
            appData = System.getProperty("user.home");
        }
        return Paths.get(appData, ".MultiMinecraft");
    }
    
    /**
     * Copia los archivos del launcher
     */
    private void copyLauncherFiles(Path installDir) throws IOException {
        // Obtener el JAR actual
        String jarPath = getJarPath();
        if (jarPath == null) {
            throw new IOException("No se pudo determinar la ubicación del launcher");
        }
        
        Path sourceJar = Paths.get(jarPath);
        Path targetJar = installDir.resolve("MultiMinecraftLauncher.jar");
        
        Files.copy(sourceJar, targetJar, StandardCopyOption.REPLACE_EXISTING);
        logger.info("JAR copiado: {} -> {}", sourceJar, targetJar);
    }
    
    /**
     * Obtiene la ruta del JAR actual
     */
    private String getJarPath() {
        try {
            String className = getClass().getName().replace('.', '/') + ".class";
            String classPath = getClass().getClassLoader().getResource(className).toString();
            
            if (classPath.startsWith("jar:")) {
                String jarPath = classPath.substring(4, classPath.lastIndexOf("!"));
                return new java.net.URI(jarPath).getPath();
            }
            
            // Si no está en un JAR, obtener el directorio de clases
            Path classFile = Paths.get(getClass().getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            
            if (Files.isDirectory(classFile)) {
                // Estamos en desarrollo, buscar el JAR en target
                return System.getProperty("user.dir") + "/target/launcher-1.0.0.jar";
            }
            
            return classFile.toString();
            
        } catch (Exception e) {
            logger.error("Error al obtener ruta del JAR", e);
            return null;
        }
    }
    
    /**
     * Crea un acceso directo en el escritorio
     */
    private void createDesktopShortcut(Path installDir) throws IOException {
        String desktopPath = System.getProperty("user.home") + File.separator + "Desktop";
        Path shortcutPath = Paths.get(desktopPath, "MultiMinecraft Launcher.url");
        
        // Crear archivo .url para Windows
        String urlContent = "[InternetShortcut]\n" +
                "URL=file:///" + installDir.resolve("MultiMinecraftLauncher.jar").toString().replace("\\", "/") + "\n" +
                "IconFile=" + installDir.resolve("MultiMinecraftLauncher.jar").toString().replace("\\", "/") + "\n" +
                "IconIndex=0\n";
        
        Files.write(shortcutPath, urlContent.getBytes());
        logger.info("Acceso directo creado: {}", shortcutPath);
    }
    
    /**
     * Crea el archivo de configuración inicial
     */
    private void createInitialConfig(Path installDir) throws IOException {
        Path configFile = installDir.resolve("config.json");
        
        String configJson = "{\n" +
                "  \"install_path\": \"" + installDir.toString().replace("\\", "\\\\") + "\",\n" +
                "  \"installed_version\": \"1.0.0\",\n" +
                "  \"install_date\": \"" + java.time.LocalDateTime.now().toString() + "\",\n" +
                "  \"desktop_shortcut\": " + desktopShortcutCheckbox.isSelected() + ",\n" +
                "  \"ram_recommended\": \"4\",\n" +
                "  \"ram_optimal\": \"8\",\n" +
                "  \"ram_maximum\": \"16\"\n" +
                "}";
        
        Files.write(configFile, configJson.getBytes());
        logger.info("Configuración inicial creada: {}", configFile);
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
