# Configuración del IDE para Ejecutar el Launcher

## IntelliJ IDEA / Cursor

Para ejecutar la aplicación desde el IDE, necesitas configurar los argumentos de VM (VM options) para JavaFX.

### Pasos:

1. **Clic derecho en `Main.java`** → **Run 'Main.main()'** o **Modify Run Configuration**

2. **En la configuración de ejecución, agrega estos VM options:**

```
--module-path "${env:MAVEN_REPOSITORY}/org/openjfx/javafx-controls/21.0.1/javafx-controls-21.0.1.jar;${env:MAVEN_REPOSITORY}/org/openjfx/javafx-fxml/21.0.1/javafx-fxml-21.0.1.jar;${env:MAVEN_REPOSITORY}/org/openjfx/javafx-base/21.0.1/javafx-base-21.0.1.jar;${env:MAVEN_REPOSITORY}/org/openjfx/javafx-graphics/21.0.1/javafx-graphics-21.0.1.jar" --add-modules javafx.controls,javafx.fxml
```

**O más simple (si Maven está configurado):**

```
--module-path "${PROJECT_DIR}/target/classes" --add-modules javafx.controls,javafx.fxml
```

3. **Alternativa más fácil - Usar Maven:**

En lugar de ejecutar directamente desde el IDE, usa el comando Maven:

```bash
mvn clean javafx:run
```

O desde el IDE:
- Abre la terminal integrada
- Ejecuta: `mvn clean javafx:run`

## Visual Studio Code

1. Crea o edita `.vscode/launch.json`:

```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Launch Main",
            "request": "launch",
            "mainClass": "com.multiminecraft.launcher.Main",
            "projectName": "launcher",
            "vmArgs": "--module-path ${env:USERPROFILE}/.m2/repository/org/openjfx/javafx-controls/21.0.1/javafx-controls-21.0.1.jar;${env:USERPROFILE}/.m2/repository/org/openjfx/javafx-fxml/21.0.1/javafx-fxml-21.0.1.jar;${env:USERPROFILE}/.m2/repository/org/openjfx/javafx-base/21.0.1/javafx-base-21.0.1.jar;${env:USERPROFILE}/.m2/repository/org/openjfx/javafx-graphics/21.0.1/javafx-graphics-21.0.1.jar --add-modules javafx.controls,javafx.fxml"
        }
    ]
}
```

## Solución Recomendada

**La forma más fácil es usar Maven directamente:**

```bash
mvn clean compile javafx:run
```

Esto automáticamente configura todos los módulos y dependencias necesarias.

