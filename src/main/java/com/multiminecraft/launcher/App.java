package com.multiminecraft.launcher;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.multiminecraft.launcher.service.ConfigService;

import java.io.IOException;

/**
 * Clase principal de la aplicación JavaFX
 */
public class App extends Application {

    private static final Logger logger = LoggerFactory.getLogger(App.class);
    private static final String APP_TITLE = "MultiMinecraft";
    private static final int WINDOW_WIDTH = 1080;
    /** Ancho de la ventana en píxeles */
    private static final int WINDOW_HEIGHT = 660;
    /** Alto de la ventana en píxeles */

    private static Scene scene;
    private static Stage primaryStage;
    private static App instance;

    public App() {
        instance = this;
    }

    /**
     * Abre una página web en el navegador predeterminado
     */
    public static void openWebPage(String url) {
        if (instance != null && url != null) {
            instance.getHostServices().showDocument(url);
        }
    }

    @Override
    public void init() throws Exception {
        super.init();
        logger.info("Inicializando aplicación JavaFX...");

        // Verificar que JavaFX esté disponible
        try {
            // Intentar acceder a clases de JavaFX para verificar disponibilidad
            Class.forName("javafx.application.Platform");
            logger.debug("JavaFX toolkit disponible");
        } catch (ClassNotFoundException e) {
            String errorMsg = "JavaFX no está disponible. " +
                    "Asegúrate de ejecutar con los módulos correctos:\n" +
                    "--module-path <ruta-a-javafx> --add-modules javafx.controls,javafx.fxml\n\n" +
                    "O usa: mvn clean javafx:run";
            logger.error(errorMsg, e);
            System.err.println("\n" + "=".repeat(60));
            System.err.println("ERROR: JavaFX no está disponible");
            System.err.println("=".repeat(60));
            System.err.println(errorMsg);
            System.err.println("=".repeat(60) + "\n");
            throw new IllegalStateException(errorMsg, e);
        }
    }

    @Override
    public void start(Stage stage) {
        primaryStage = stage;

        try {
            logger.info("Iniciando MultiMinecraft Launcher...");

            // Determinar vista inicial según si hay nombre de jugador
            String initialView;
            ConfigService configService = ConfigService.getInstance();
            String playerName = configService.getLauncherConfig().getPlayerName();
            if (playerName == null || playerName.trim().isEmpty()) {
                initialView = "Bienvenida";
                logger.info("No hay nombre de jugador configurado, mostrando pantalla de bienvenida");
            } else {
                initialView = "Principal";
                logger.info("Jugador: {}", playerName);
            }

            logger.debug("Cargando archivo FXML: {}", initialView);
            Parent root = loadFXML(initialView);
            logger.debug("FXML cargado correctamente");

            scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            logger.debug("Escena creada");

            // Aplicar tema por defecto
            applyTheme("dark");

            // Cargar CSS de bienvenida si es la vista inicial
            if ("Bienvenida".equals(initialView)) {
                var welcomeCss = App.class.getResource("/css/welcome.css");
                if (welcomeCss != null) {
                    scene.getStylesheets().add(welcomeCss.toExternalForm());
                }
            }

            stage.setTitle(APP_TITLE);
            stage.initStyle(StageStyle.TRANSPARENT);
            stage.setScene(scene);
            stage.setMinWidth(650);
            stage.setMinHeight(400);
            stage.show();

            logger.info("Launcher iniciado correctamente");

        } catch (Exception e) {
            logger.error("Error crítico al iniciar la aplicación", e);
            e.printStackTrace();

            // Mostrar error al usuario si es posible
            try {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.ERROR);
                alert.setTitle("Error al iniciar");
                alert.setHeaderText("No se pudo iniciar el launcher");
                alert.setContentText("Error: " + e.getMessage() + "\n\nRevisa los logs para más detalles.");
                alert.showAndWait();
            } catch (Exception ex) {
                // Si no se puede mostrar el diálogo, al menos imprimir en consola
                System.err.println("ERROR CRÍTICO: " + e.getMessage());
                e.printStackTrace();
            }

            throw new RuntimeException("No se pudo iniciar la aplicación", e);
        }
    }

    /**
     * Cambia la vista actual
     */
    public static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }

    /**
     * Carga un archivo FXML
     */
    private static Parent loadFXML(String fxml) throws IOException {
        String resourcePath = "/fxml/" + fxml + ".fxml";
        logger.debug("Intentando cargar recurso: {}", resourcePath);

        java.net.URL resource = App.class.getResource(resourcePath);
        if (resource == null) {
            String errorMsg = "No se encontró el archivo FXML: " + resourcePath;
            logger.error(errorMsg);
            throw new IOException(errorMsg);
        }

        logger.debug("Recurso encontrado: {}", resource);
        FXMLLoader fxmlLoader = new FXMLLoader(resource);

        try {
            Parent root = fxmlLoader.load();
            logger.debug("FXML cargado exitosamente: {}", fxml);
            return root;
        } catch (Exception e) {
            logger.error("Error al cargar el FXML: {}", fxml, e);
            throw new IOException("Error al cargar " + resourcePath + ": " + e.getMessage(), e);
        }
    }

    /**
     * Aplica un tema a la aplicación
     */
    public static void applyTheme(String theme) {
        if (scene == null) {
            logger.warn("No se puede aplicar el tema: la escena no está inicializada");
            return;
        }

        try {
            scene.getStylesheets().clear();

            // Cargar CSS principal
            var mainCss = App.class.getResource("/css/main.css");
            if (mainCss != null) {
                scene.getStylesheets().add(mainCss.toExternalForm());
            } else {
                logger.warn("No se encontró el archivo CSS principal: /css/main.css");
            }

            // Cargar CSS del tema
            var themeCss = App.class.getResource("/css/" + theme + "-theme.css");
            if (themeCss != null) {
                scene.getStylesheets().add(themeCss.toExternalForm());
                logger.info("Tema aplicado: {}", theme);
            } else {
                logger.warn("No se encontró el archivo CSS del tema: /css/{}-theme.css", theme);
            }

            // Cargar CSS específico de la ventana principal
            var mainWindowCss = App.class.getResource("/css/main-window.css");
            if (mainWindowCss != null) {
                scene.getStylesheets().add(mainWindowCss.toExternalForm());
            }
        } catch (Exception e) {
            logger.error("Error al aplicar el tema: {}", theme, e);
        }
    }

    /**
     * Obtiene el Stage principal
     */
    public static Stage getPrimaryStage() {
        return primaryStage;
    }
}
