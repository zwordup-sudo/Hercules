# Validación real del candidato v1.13

Fecha: 2026-10-07.

- Inspección de código y ampliación de regresiones: realizada.
- `python3 tools/check_sql.py`: **17 comprobaciones SQL parciales satisfactorias** sobre el SQL de migración y consultas reales. No ejecuta Kotlin, KSP o Room.
- Pruebas declaradas en código: **107 JVM y 10 instrumentadas**. Este conteo no demuestra su ejecución ni su éxito.
- `bash gradlew --offline :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`: falló antes de compilar, al intentar descargar Gradle 8.9 (`java.net.SocketException: Network is unreachable`).
- Entorno de esta entrega: Java 17; sin SDK Android, JDK 21 del proyecto ni distribución Gradle disponible.
- No hay APK nuevo, tests Kotlin ejecutados, resultado Lint ni instalación Android comprobada en esta entrega.
- Las pruebas instrumentadas preparadas incluyen validación de migración 9→10 con apertura real de Room, integridad de inicio y edición de plantillas. Pendientes de ejecución en Android.
- Firma no cambiada por código; la firma del APK futuro depende del keystore del PC donde se compile.
- GGA continúa pendiente, sin alterar el harness.

Ejecutar el script PowerShell incluido en el PC original; reparar fallos y registrar evidencia antes de presentar v1.13 como versión terminada.
