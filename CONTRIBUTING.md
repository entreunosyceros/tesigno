# Contribuir a TeSigno

TeSigno prioriza la accesibilidad, la sencillez y el funcionamiento sin conexión. Antes de proponer cambios grandes, explica el problema en una incidencia del repositorio.

## Preparar el entorno

Consulta el README para instalar JDK y Android SDK. Clona el repositorio y abre el proyecto en Android Studio o configura `local.properties` con la ubicación local del SDK. Este archivo nunca debe subirse.

Antes de enviar un cambio ejecuta:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

También puedes usar `./tools/build_debug.sh`, que limpia los informes tras una compilación correcta. Para ejecutar las pruebas instrumentadas necesitas un teléfono o emulador operativo: `./gradlew connectedDebugAndroidTest`.

Describe qué has cambiado, cómo lo has comprobado y qué pruebas no has podido ejecutar. Revisa texto grande, contraste, TalkBack, navegación Atrás y funcionamiento sin conexión cuando el cambio afecte a la interfaz.

## Código y contenidos

Mantén Kotlin y Compose, el reproductor Media3 y la arquitectura sencilla existente. No añadas servicios de red, publicidad, analítica ni permisos sin una necesidad acordada. Conserva los IDs estables y la compatibilidad de los datos personales al cambiar la persistencia.

Las contribuciones de código se distribuyen bajo la GPL-3.0 del proyecto. No incluyas vídeos, imágenes ni grabaciones de personas sin los derechos y autorizaciones necesarios. Las licencias de contenidos son independientes de la del código. No inventes signos ni traducciones de LSE. El vídeo técnico de los tests es únicamente un patrón de color sin signos.

## Informar de un problema

Indica la versión de TeSigno y Android, los pasos para reproducirlo, el resultado esperado y el resultado observado. No adjuntes claves, datos del teléfono, catálogos personales ni grabaciones familiares a incidencias públicas.

## Archivos que se publican

Incluye fuentes, recursos autorizados, tests, documentación, licencia y Gradle Wrapper, incluido `gradle-wrapper.jar`. No incluyas `local.properties`, cachés, carpetas build, claves de firma ni APK. Los APK se pueden distribuir por separado en una publicación de GitHub Releases.
