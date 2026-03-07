package com.multiminecraft.launcher.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import com.multiminecraft.launcher.model.LoaderType;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilidad para operaciones JSON
 */
public class JsonUtil {
    
    private static final Gson gson;
    private static final Gson prettyGson;
    
    static {
        GsonBuilder builder = new GsonBuilder();
        
        // Serializer/Deserializer para LocalDateTime
        JsonSerializer<LocalDateTime> dateTimeSerializer = (src, typeOfSrc, context) ->
                context.serialize(src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        JsonDeserializer<LocalDateTime> dateTimeDeserializer = (json, typeOfT, context) ->
                LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        builder.registerTypeAdapter(LocalDateTime.class, dateTimeSerializer);
        builder.registerTypeAdapter(LocalDateTime.class, dateTimeDeserializer);
        
        // Serializer/Deserializer para LoaderType
        JsonSerializer<LoaderType> loaderTypeSerializer = (src, typeOfSrc, context) ->
                new JsonPrimitive(src.name());
        
        JsonDeserializer<LoaderType> loaderTypeDeserializer = (json, typeOfT, context) -> {
            try {
                String value = json.getAsString();
                return LoaderType.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Si no se puede parsear, intentar con el nombre del display
                String value = json.getAsString();
                for (LoaderType type : LoaderType.values()) {
                    if (type.getDisplayName().equalsIgnoreCase(value)) {
                        return type;
                    }
                }
                throw new JsonParseException("No se pudo deserializar LoaderType: " + value);
            }
        };
        
        builder.registerTypeAdapter(LoaderType.class, loaderTypeSerializer);
        builder.registerTypeAdapter(LoaderType.class, loaderTypeDeserializer);
        
        gson = builder.create();
        prettyGson = builder.setPrettyPrinting().create();
    }
    
    /**
     * Convierte un objeto a JSON
     */
    public static String toJson(Object obj) {
        return prettyGson.toJson(obj);
    }
    
    /**
     * Convierte un objeto a JSON compacto
     */
    public static String toJsonCompact(Object obj) {
        return gson.toJson(obj);
    }
    
    /**
     * Convierte JSON a un objeto
     */
    public static <T> T fromJson(String json, Class<T> classOfT) {
        return gson.fromJson(json, classOfT);
    }
    
    /**
     * Convierte JSON a una lista de objetos
     */
    public static <T> List<T> fromJsonList(String json, Class<T> classOfT) {
        Type listType = TypeToken.getParameterized(List.class, classOfT).getType();
        return gson.fromJson(json, listType);
    }
    
    /**
     * Obtiene la instancia de Gson
     */
    public static Gson getGson() {
        return gson;
    }
    
    /**
     * Obtiene la instancia de Gson con formato pretty
     */
    public static Gson getPrettyGson() {
        return prettyGson;
    }
}
