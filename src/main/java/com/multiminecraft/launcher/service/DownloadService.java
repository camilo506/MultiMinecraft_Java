package com.multiminecraft.launcher.service;

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
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio para descargar archivos con seguimiento de progreso
 */
public class DownloadService {
    
    private static final Logger logger = LoggerFactory.getLogger(DownloadService.class);
    private static final int TIMEOUT_SECONDS = 300; // Aumentado a 5 minutos para descargas grandes
    private static final int BUFFER_SIZE = 8192;
    
    private final HttpClient httpClient;
    
    public DownloadService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
    
    /**
     * Descarga un archivo desde una URL
     */
    public void downloadFile(String url, Path destination) throws IOException, InterruptedException {
        downloadFile(url, destination, null);
    }
    
    /**
     * Descarga un archivo con callback de progreso
     * @param url URL del archivo
     * @param destination Ruta de destino
     * @param progressCallback Callback que recibe el progreso (0.0 a 1.0)
     */
    public void downloadFile(String url, Path destination, Consumer<Double> progressCallback) 
            throws IOException, InterruptedException {
        
        String currentUrl = url;
        boolean triedConfirm = false;
        
        for (int attempt = 0; attempt < 2; attempt++) {
            logger.info("Intento de descarga {}: {}", (attempt + 1), currentUrl);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(currentUrl))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET()
                    .build();
            
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            
            // Manejo especial para Google Drive (archivos grandes o advertencias)
            if (currentUrl.contains("drive.google.com") && response.statusCode() == 200 && !triedConfirm) {
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                if (contentType.contains("text/html")) {
                    logger.info("Detectada página HTML de Drive en lugar de archivo, analizando formulario...");
                    
                    try (InputStream bodyIn = response.body()) {
                        byte[] firstBytes = bodyIn.readNBytes(20000); // Leer un poco más para asegurar capturar todo el formulario
                        String body = new String(firstBytes);
                        
                        // Buscar todos los inputs ocultos (name y value)
                        Pattern inputPattern = Pattern.compile("<input type=\"hidden\" name=\"([^\"]+)\" value=\"([^\"]*)\">");
                        Matcher matcher = inputPattern.matcher(body);
                        StringBuilder params = new StringBuilder();
                        boolean hasConfirm = false;
                        
                        while (matcher.find()) {
                            String name = matcher.group(1);
                            String value = matcher.group(2);
                            if (params.length() > 0) params.append("&");
                            params.append(name).append("=").append(value);
                            if (name.equals("confirm")) hasConfirm = true;
                        }
                        
                        if (hasConfirm) {
                            // Cambiamos a la URL de descarga directa de contenido con todos los parámetros extraídos
                            currentUrl = "https://drive.usercontent.google.com/download?" + params.toString();
                            logger.info("Url de descarga construida con éxito. Reintentando...");
                            triedConfirm = true;
                            continue;
                        } else if (body.contains("Access Denied") || body.contains("Sign in") || body.contains("iniciar sesión")) {
                            throw new IOException("Acceso denegado a Google Drive. Asegúrate de que el enlace sea PÚBLICO.");
                        } else {
                            throw new IOException("No se pudo extraer el token de descarga de la página de Drive. El archivo podría no estar disponible.");
                        }
                    }
                }
            }

            if (response.statusCode() != 200) {
                try { response.body().close(); } catch (Exception ignored) {}
                throw new IOException("Error HTTP " + response.statusCode() + " al descargar: " + currentUrl);
            }

            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            
            // Si llegamos aquí, procedemos con la descarga real
            try (InputStream in = response.body()) {
                if (destination.getParent() != null) {
                    Files.createDirectories(destination.getParent());
                }
                Path tempFile = destination.getParent().resolve(destination.getFileName() + ".tmp");
                
                try (var out = Files.newOutputStream(tempFile)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
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
                
                Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Descarga completada con éxito: {}", destination.getFileName());
                return; // Éxito, salimos del bucle y del método
            }
        }
    }
    
    /**
     * Descarga contenido como String
     */
    public String downloadString(String url) throws IOException, InterruptedException {
        return downloadString(url, TIMEOUT_SECONDS);
    }
    
    /**
     * Descarga contenido como String con timeout personalizado
     */
    public String downloadString(String url, int timeoutSeconds) throws IOException, InterruptedException {
        logger.debug("Descargando contenido de: {} (timeout: {}s)", url, timeoutSeconds);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new IOException("Error HTTP " + response.statusCode() + " al descargar: " + url);
        }
        
        return response.body();
    }
    
    /**
     * Descarga un archivo con timeout personalizado
     * @param url URL del archivo
     * @param destination Ruta de destino
     * @param timeoutSeconds Timeout en segundos
     * @param progressCallback Callback que recibe el progreso (0.0 a 1.0)
     */
    public void downloadFileWithTimeout(String url, Path destination, int timeoutSeconds, Consumer<Double> progressCallback) 
            throws IOException, InterruptedException {
        
        logger.info("Descargando: {} -> {} (timeout: {}s)", url, destination, timeoutSeconds);
        
        // Crear directorio padre si no existe
        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .GET()
                .build();
        
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        
        if (response.statusCode() != 200) {
            throw new IOException("Error HTTP " + response.statusCode() + " al descargar: " + url);
        }
        
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        
        try (InputStream in = response.body()) {
            Path tempFile = destination.getParent().resolve(destination.getFileName() + ".tmp");
            
            try (var out = Files.newOutputStream(tempFile)) {
                byte[] buffer = new byte[BUFFER_SIZE];
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
            
            // Mover archivo temporal al destino final
            Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Descarga completada: {}", destination.getFileName());
        }
    }
    
    /**
     * Verifica si una URL es accesible
     */
    public boolean isUrlAccessible(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Descarga múltiples archivos en paralelo
     * @param downloads Lista de pares (URL, Path destino)
     * @param maxConcurrent Número máximo de descargas simultáneas
     * @param progressCallback Callback que recibe el progreso total (0.0 a 1.0)
     */
    public void downloadFilesParallel(List<DownloadTask> downloads, int maxConcurrent, Consumer<Double> progressCallback) 
            throws InterruptedException, ExecutionException {
        
        if (downloads.isEmpty()) {
            return;
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrent);
        List<Future<Void>> futures = new ArrayList<>();
        AtomicInteger completed = new AtomicInteger(0);
        int total = downloads.size();
        
        try {
            for (DownloadTask task : downloads) {
                Future<Void> future = executor.submit(() -> {
                    try {
                        downloadFile(task.url, task.destination);
                        int done = completed.incrementAndGet();
                        if (progressCallback != null) {
                            double progress = (double) done / total;
                            progressCallback.accept(progress);
                        }
                        return null;
                    } catch (Exception e) {
                        logger.error("Error al descargar {}: {}", task.url, e.getMessage());
                        throw new RuntimeException(e);
                    }
                });
                futures.add(future);
            }
            
            // Esperar a que todas las descargas terminen
            for (Future<Void> future : futures) {
                future.get();
            }
            
            logger.info("Descargas paralelas completadas: {}/{}", completed.get(), total);
            
        } finally {
            executor.shutdown();
        }
    }
    
    /**
     * Clase para representar una tarea de descarga
     */
    public static class DownloadTask {
        public final String url;
        public final Path destination;
        
        public DownloadTask(String url, Path destination) {
            this.url = url;
            this.destination = destination;
        }
    }
}
