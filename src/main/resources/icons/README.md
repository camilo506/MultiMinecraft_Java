# Iconos de Instancias

Esta carpeta contiene los iconos personalizados para las instancias de Minecraft.

## Cómo usar

1. Coloca tus archivos de imagen (PNG, JPG, etc.) en esta carpeta
2. En la configuración de la instancia (`instance.json`), usa solo el nombre del archivo en el campo `icon`

### Ejemplo

Si tienes un archivo llamado `mi-icono.png` en esta carpeta, en el `instance.json` usa:

```json
{
  "icon": "mi-icono.png"
}
```

## Formatos soportados

- PNG (recomendado)
- JPG/JPEG
- GIF
- BMP

## Notas

- Los iconos se cargan primero desde esta carpeta de recursos
- Si no se encuentra en esta carpeta, se intentará cargar como ruta absoluta
- Si no se encuentra ningún icono, se usará un icono por defecto generado automáticamente

