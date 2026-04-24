# Chaca #5 POS — App Android Nativa

## ¿Qué hace esta app?
- Carga tu sistema RestoPOS directamente (sin browser)
- Al presionar "Imprimir" en el modal del ticket, envía los bytes ESC/POS 
  directo a RawBT sin ningún diálogo ni pasos extras
- Pantalla siempre encendida (ideal para tablet de restaurante)
- Funciona en modo landscape (horizontal)

## PASO 1 — Cambiar la URL de tu sistema

Abre el archivo:
  app/src/main/java/com/chacarestaurante/pos/MainActivity.java

Busca la línea:
  private static final String POS_URL = "https://script.google.com/macros/s/TU_DEPLOYMENT_ID/exec";

Cámbiala por la URL real de tu deploy de Google Apps Script.

## PASO 2 — Instalar Android Studio (gratis)

Descarga desde: https://developer.android.com/studio
Instala en tu PC/laptop con Windows o Mac.

## PASO 3 — Abrir el proyecto

1. Abre Android Studio
2. File → Open → selecciona la carpeta "RestoPOS_Android"
3. Espera que descargue las dependencias (primera vez ~5 minutos)

## PASO 4 — Generar el APK

1. En Android Studio: Build → Build Bundle(s) / APK(s) → Build APK(s)
2. Espera la compilación (~2 minutos)
3. Aparece una notificación "APK(s) generated" → click "locate"
4. El APK está en: app/build/outputs/apk/debug/app-debug.apk

## PASO 5 — Instalar en la tablet

Opción A (cable USB):
1. En la tablet: Ajustes → Acerca del dispositivo → toca "Número de compilación" 7 veces
2. Ajustes → Opciones de desarrollador → activa "Depuración USB"
3. Conecta la tablet al PC con cable USB
4. En Android Studio: Run → Run 'app' (instala directo en la tablet)

Opción B (sin cable):
1. Copia el APK a la tablet (por email, WhatsApp, Drive, USB)
2. En la tablet abre el APK
3. Si pregunta "permitir instalación de fuentes desconocidas" → acepta

## FLUJO DE IMPRESIÓN después de instalar

Al presionar "Imprimir" en el modal del ticket:
  JavaScript → AndroidPrint.imprimirEscPos(base64) 
  → App guarda bytes en archivo temporal
  → Lanza Intent directo a RawBT (sin preguntar)
  → RawBT imprime en la térmica automáticamente

✅ Cero diálogos. Cero pasos extras. Impresión en 1 segundo.

## Requisitos mínimos
- Android 7.0 o superior (la tablet tiene Android 10+ seguramente)
- RawBT instalado en la tablet
- Bluetooth conectado a la impresora SAT AF330

## Cambiar nombre de la app
En app/src/main/res/values/strings.xml cambia "Chaca #5 POS" por lo que prefieras.
