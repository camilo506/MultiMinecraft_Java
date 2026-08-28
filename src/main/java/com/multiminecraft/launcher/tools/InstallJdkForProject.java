package com.multiminecraft.launcher.tools;

import com.multiminecraft.launcher.service.JavaRuntimeService;

import java.util.Locale;

public class InstallJdkForProject {

    public static void main(String[] args) {
        String choice = args.length > 0 ? args[0] : "21";

        String minecraftVersion;
        switch (choice.toLowerCase(Locale.ROOT)) {
            case "25":
            case "jdk25":
                minecraftVersion = "26.1"; // mapea a Java 25
                break;
            case "21":
            case "jdk21":
                minecraftVersion = "1.20.5"; // mapea a Java 21
                break;
            case "17":
            case "jdk17":
                minecraftVersion = "1.17.0"; // mapea a Java 17
                break;
            case "8":
            case "jdk8":
                minecraftVersion = "1.16.5"; // mapea a Java 8
                break;
            default:
                // Si el usuario pasa una versión de Minecraft, la usamos directamente
                minecraftVersion = choice;
        }

        System.out.println("Instalando JDK para mapeo de Minecraft: " + minecraftVersion);

        JavaRuntimeService svc = new JavaRuntimeService();
        try {
            String path = svc.ensureJavaForMinecraftVersion(minecraftVersion,
                    status -> System.out.println("STATUS: " + status),
                    progress -> System.out.println(String.format("PROGRESS: %.0f%%", progress * 100)));

            System.out.println("Instalación finalizada. Java disponible en: " + path);
        } catch (Exception e) {
            System.err.println("Error instalando JDK: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }
}
