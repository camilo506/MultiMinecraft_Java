package com.multiminecraft.launcher.downloader;

import com.multiminecraft.launcher.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Descargador paralelo optimizado para múltiples archivos
 * Implementa multidescarga con máximo 3 hilos simultáneos según especificaciones
 */
public class ParallelDownloader {
    
    private static final Logger logger = LoggerFactory.getLogger(ParallelDownloader.class);
    
    // HttpClient compartido (singleton) — reutiliza conexiones TCP/TLS entre todas las instancias
    private static final HttpClient SHARED_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(Config.DOWNLOAD_TIMEOUT))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_2)
            .build();
    
    private final HttpClient httpClient;
    private final ExecutorService executorService;
    
    public ParallelDownloader() {
        this.httpClient = SHARED_HTTP_CLIENT;
        this.executorService = Executors.newFixedThreadPool(Config.MAX_CONCURRENT_DOWNLOADS);
    }
    
    /**
     * Descarga múltiples archivos en paralelo
     * @param tasks Lista de tareas de descarga
     * @param callback Callback de progreso (recibe progreso total 0.0 a 1.0)
     * @return Mapa con resultados: URL -> éxito (true/false)
     */
    public Map<String, Boolean> downloadFiles(List<DownloadTask> tasks, ProgressCallback callback) {
        if (tasks.isEmpty()) {
            return new HashMap<>();
        }
        
        Map<String, Boolean> results = new ConcurrentHashMap<>();
        AtomicInteger completed = new AtomicInteger(0);
        int total = tasks.size();
        
        List<Future<Boolean>> futures = new ArrayList<>();
        
        try {
            for (DownloadTask task : tasks) {
                Future<Boolean> future = executorService.submit(() -> {
                    try {
                        downloadFile(task.getUrl(), task.getDestination(), task.getSha1(), 
                                   progress -> {
                                       // Progreso individual no se reporta, solo el total
                                   });
                        
                        results.put(task.getUrl(), true);
                        int done = completed.incrementAndGet();
                        
                        if (callback != null) {
                            double totalProgress = (double) done / total;
                            callback.onProgress(totalProgress, done, total);
                        }
                        
                        logger.debug("Descarga completada: {}", task.getDestination().getFileName());
                        return true;
                        
                    } catch (Exception e) {
                        logger.error("Error al descargar {}: {}", task.getUrl(), e.getMessage());
                        results.put(task.getUrl(), false);
                        
                        int done = completed.incrementAndGet();
                        if (callback != null) {
                            double totalProgress = (double) done / total;
                            callback.onProgress(totalProgress, done, total);
                        }
                        
                        return false;
                    }
                });
                
                futures.add(future);
            }
            
            // Esperar a que todas las descargas terminen
            for (Future<Boolean> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    logger.error("Error en descarga", e);
                }
            }
            
            int successCount = (int) results.values().stream().filter(Boolean::booleanValue).count();
            logger.info("Descargas paralelas completadas: {}/{} exitosas", successCount, total);
            
        } catch (InterruptedException e) {
            logger.error("Descargas interrumpidas", e);
            Thread.currentThread().interrupt();
        }
        
        return results;
    }
    
    /**
     * Descarga un archivo individual
     */
    private void downloadFile(String url, Path destination, String expectedSha1, 
                             Consumer<Double> progressCallback) 
            throws IOException, InterruptedException {
        
        logger.debug("Descargando: {} -> {}", url, destination);
        
        // Crear directorio padre si no existe
        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }
        
        // Verificar si el archivo ya existe y tiene el hash correcto
        if (expectedSha1 != null && Files.exists(destination)) {
            if (verifyFileHash(destination, expectedSha1)) {
                logger.debug("Archivo ya existe y es válido: {}", destination);
                if (progressCallback != null) {
                    progressCallback.accept(1.0);
                }
                return;
            }
        }
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Config.DOWNLOAD_TIMEOUT))
                .GET()
                .build();
        
        HttpResponse<InputStream> response = httpClient.send(
                request, 
                HttpResponse.BodyHandlers.ofInputStream()
        );
        
        if (response.statusCode() != 200) {
            throw new IOException("Error HTTP " + response.statusCode() + " al descargar: " + url);
        }
        
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        
        // Usar chunks de 8KB según especificaciones
        Path tempFile = destination.getParent().resolve(destination.getFileName() + ".tmp");
        
        try (InputStream in = response.body();
             var out = Files.newOutputStream(tempFile)) {
            
            byte[] buffer = new byte[Config.CHUNK_SIZE];
            long totalBytesRead = 0;
            int bytesRead;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                
                if (progressCallback != null && contentLength > 0) {
                    double progress = (double) totalBytesRead / contentLength;
                    progressCallback.accept(progress);
                }
            }
        }
        
        // Verificar hash si se proporcionó
        if (expectedSha1 != null) {
            if (!verifyFileHash(tempFile, expectedSha1)) {
                Files.deleteIfExists(tempFile);
                throw new IOException("Hash SHA1 no coincide para: " + url);
            }
        }
        
        // Mover archivo temporal al destino final
        Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING);
        logger.debug("Descarga completada: {}", destination.getFileName());
    }
    
    /**
     * Verifica el hash SHA1 de un archivo
     */
    private boolean verifyFileHash(Path file, String expectedSha1) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[Config.CHUNK_SIZE];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }
            }
            
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            
            String actualSha1 = sb.toString();
            return actualSha1.equalsIgnoreCase(expectedSha1);
            
        } catch (Exception e) {
            logger.warn("Error al verificar hash de {}: {}", file, e.getMessage());
            return false;
        }
    }
    
    /**
     * Cierra el executor service
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Clase para representar una tarea de descarga
     */
    public static class DownloadTask {
        private final String url;
        private final Path destination;
        private final String sha1;
        
        public DownloadTask(String url, Path destination) {
            this(url, destination, null);
        }
        
        public DownloadTask(String url, Path destination, String sha1) {
            this.url = url;
            this.destination = destination;
            this.sha1 = sha1;
        }
        
        public String getUrl() {
            return url;
        }
        
        public Path getDestination() {
            return destination;
        }
        
        public String getSha1() {
            return sha1;
        }
    }
    
    /**
     * Interfaz para callback de progreso
     */
    public interface ProgressCallback {
        /**
         * Se llama cuando hay actualización de progreso
         * @param progress Progreso total (0.0 a 1.0)
         * @param completed Número de archivos completados
         * @param total Número total de archivos
         */
        void onProgress(double progress, int completed, int total);
    }
}
