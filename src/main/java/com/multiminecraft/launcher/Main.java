package com.multiminecraft.launcher;

import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Punto de entrada principal del launcher.
 * Esta clase NO extiende Application, lo cual permite lanzar JavaFX
 * sin necesidad de --module-path / --add-modules en la línea de comandos.
 * Es el truco estándar para ejecutar apps JavaFX 11+ desde un fat-JAR o IDE.
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            logger.info("Iniciando aplicación desde Main...");
            // IMPORTANTE: Usar Application.launch(App.class, args) en vez de App.main(args)
            // Esto evita el error "faltan los componentes de JavaFX runtime"
            // porque la clase que contiene main() NO extiende Application.
            Application.launch(App.class, args);
        } catch (Exception e) {
            logger.error("Error fatal al iniciar la aplicación", e);
            e.printStackTrace();
            System.err.println("ERROR: No se pudo iniciar la aplicación");
            System.err.println("Causa: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Causa raíz: " + e.getCause().getMessage());
            }
            System.exit(1);
        }
    }
}
