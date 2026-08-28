package com.multiminecraft.launcher.service;

import com.multiminecraft.launcher.model.Instance;
import com.multiminecraft.launcher.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Gestiona el despliegue de skins por instancia para CustomSkinLoader.
 */
public class SkinDeploymentService {

    private static final Logger logger = LoggerFactory.getLogger(SkinDeploymentService.class);

    /** Marca el JSON generado por este launcher (LocalSkin-only); se usa para borrarlo al migrar a skins de servidor. */
    private static final String CSL_LAUNCHER_FINGERPRINT = "\"forceDisableCache\": true";

    private final ConfigService configService;

    public SkinDeploymentService(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * Elimina {@code CustomSkinLoader.json} si fue generado por este launcher (solo LocalSkin),
     * para que mods como SkinRestorer o la configuración por defecto de CSL vuelvan a aplicar.
     */
    public void clearLauncherForcedCustomSkinLoader(Instance instance) {
        try {
            Path instanceDir = configService.getInstanceMinecraftDirectory(instance.getName());
            Path cslConfigFile = instanceDir.resolve("CustomSkinLoader").resolve("CustomSkinLoader.json");
            if (!Files.exists(cslConfigFile)) {
                return;
            }
            String content = Files.readString(cslConfigFile, StandardCharsets.UTF_8);
            if (!content.contains(CSL_LAUNCHER_FINGERPRINT)
                    || !content.contains("\"name\": \"LocalSkin\"")
                    || !content.contains("LocalSkin/skins/{USERNAME}.png")) {
                return;
            }
            Files.delete(cslConfigFile);
            logger.info("Eliminado CustomSkinLoader.json forzado por el launcher (instancia sin skin local).");
        } catch (Exception e) {
            logger.warn("No se pudo limpiar CustomSkinLoader.json: {}", e.getMessage());
        }
    }

    public void deploySkinToInstance(Instance instance, String playerName) {
        try {
            String skinPath = instance.getSkinPath();
            if (skinPath == null || skinPath.isEmpty()) {
                logger.info("La instancia '{}' no tiene skin configurada. Se usará Alex/Steve.", instance.getName());
                return;
            }

            Path skinSource = Path.of(skinPath);
            if (!Files.exists(skinSource)) {
                logger.warn("El archivo de skin no existe: {}", skinPath);
                return;
            }

            Path instanceDir = configService.getInstanceMinecraftDirectory(instance.getName());
            Path cslBaseDir = instanceDir.resolve("CustomSkinLoader");
            Path cslConfigFile = cslBaseDir.resolve("CustomSkinLoader.json");
            Path skinsDir = cslBaseDir.resolve("LocalSkin").resolve("skins");

            cleanCslDirectories(cslBaseDir, skinsDir);

            Path skinDest = skinsDir.resolve(playerName + ".png");
            Files.copy(skinSource, skinDest, StandardCopyOption.REPLACE_EXISTING);
            Path localRootAlias = cslBaseDir.resolve("LocalSkin").resolve(playerName + ".png");
            Files.copy(skinSource, localRootAlias, StandardCopyOption.REPLACE_EXISTING);
            copySkinUsernameAliases(skinSource, skinsDir, playerName);
            logger.info("Skin desplegada: {}", skinDest);

            FileUtil.writeStringToFile(cslConfigFile, buildLocalSkinOnlyConfigJson());
            logger.info("CustomSkinLoader.json forzado a LocalSkin-only");

            clearCslCache(cslBaseDir.resolve("caches"));
            patchMCARebornConfig(instanceDir);

            logger.info("Skin '{}' desplegada con éxito.", playerName);
        } catch (Exception e) {
            logger.error("Error al desplegar skin en instancia", e);
        }
    }

    private void cleanCslDirectories(Path cslBaseDir, Path skinsDir) {
        try {
            Path cacheDir = cslBaseDir.resolve("caches");
            if (Files.exists(cacheDir)) {
                FileUtil.deleteDirectory(cacheDir);
            }

            if (Files.exists(skinsDir)) {
                try (var stream = Files.list(skinsDir)) {
                    stream.forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {
                        }
                    });
                }
            } else {
                Files.createDirectories(skinsDir);
            }
        } catch (IOException e) {
            logger.warn("Error limpiando directorios de CustomSkinLoader: {}", e.getMessage());
        }
    }

    private void clearCslCache(Path cachesDir) {
        if (!Files.exists(cachesDir)) {
            return;
        }
        try (var cacheFiles = Files.list(cachesDir)) {
            cacheFiles.forEach(f -> {
                try {
                    Files.deleteIfExists(f);
                } catch (Exception ignored) {
                }
            });
            logger.info("Caché de CSL limpiada");
        } catch (IOException e) {
            logger.warn("No se pudo limpiar caché de CSL: {}", e.getMessage());
        }
    }

    private void patchMCARebornConfig(Path instanceDir) {
        Path mcaConfig = instanceDir.resolve("config").resolve("mca.json");
        if (Files.exists(mcaConfig)) {
            try {
                String content = Files.readString(mcaConfig, StandardCharsets.UTF_8);
                if (content.contains("\"enableVillagerPlayerModel\": true")) {
                    content = content.replace("\"enableVillagerPlayerModel\": true", "\"enableVillagerPlayerModel\": false");
                    Files.writeString(mcaConfig, content, StandardCharsets.UTF_8);
                    logger.info("MCA Reborn: Configuración parcheada para permitir skins (enableVillagerPlayerModel=false)");
                }
            } catch (Exception e) {
                logger.warn("No se pudo parchear mca.json: {}", e.getMessage());
            }
        }
    }

    private void copySkinUsernameAliases(Path skinSource, Path skinsDir, String playerName) throws IOException {
        String playerLower = playerName.toLowerCase();
        String playerUpper = playerName.toUpperCase();
        String playerCap = playerName.isEmpty()
                ? playerName
                : Character.toUpperCase(playerName.charAt(0)) + playerName.substring(1).toLowerCase();

        for (String alias : List.of(playerLower, playerUpper, playerCap)) {
            if (!alias.equals(playerName)) {
                Files.copy(skinSource, skinsDir.resolve(alias + ".png"), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private String buildLocalSkinOnlyConfigJson() {
        return "{\n" +
                "  \"version\": \"14.28\",\n" +
                "  \"buildNumber\": 38,\n" +
                "  \"loadlist\": [\n" +
                "    {\n" +
                "      \"name\": \"LocalSkin\",\n" +
                "      \"type\": \"Legacy\",\n" +
                "      \"checkPNG\": false,\n" +
                "      \"skin\": \"LocalSkin/skins/{USERNAME}.png\",\n" +
                "      \"model\": \"auto\",\n" +
                "      \"cape\": \"LocalSkin/capes/{USERNAME}.png\",\n" +
                "      \"elytra\": \"LocalSkin/elytras/{USERNAME}.png\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"enableDynamicSkull\": true,\n" +
                "  \"enableTransparentSkin\": true,\n" +
                "  \"forceLoadAllTextures\": true,\n" +
                "  \"enableCape\": true,\n" +
                "  \"threadPoolSize\": 8,\n" +
                "  \"enableLogStdOut\": false,\n" +
                "  \"cacheExpiry\": 0,\n" +
                "  \"forceUpdateSkull\": false,\n" +
                "  \"enableLocalProfileCache\": false,\n" +
                "  \"enableCacheAutoClean\": false,\n" +
                "  \"forceDisableCache\": true\n" +
                "}";
    }
}
