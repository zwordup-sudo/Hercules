# Publicación GitHub v1.21 (2026-11-12)

- Repositorio **privado** `zwordup-sudo/Hercules` (`https://github.com/zwordup-sudo/Hercules`), rama principal `master` (también rama por defecto), commit **`9e5818592a988cdd95e036bb1c60fa53a022ea63`** y etiqueta anotada **`v1.21`** apuntando a ese mismo commit. Verificado por API: `private=true`, rama y etiqueta en GitHub; sin APK en la raíz remota.
- Publicado con la credencial de Git Credential Manager ya existente para `github.com` (usuario `zwordup-sudo`). `gh` no estaba instalado; se usó una copia portable en temporal solo para diagnóstico, sin cambiar configuración del sistema, OpenCode, Engram ni GGA.
- Antes del commit se auditó la copia local probada (`versionCode 22` / `1.21`): 94 archivos (código, pruebas, esquemas Room 10–12, Gradle Wrapper, AGENTS/MEMORY/CORRECCIONES/VALIDACION, `tools/` y `design/`). Sin coincidencias de secretos/credenciales, sin keystores y sin historial previo (primer commit).
- Excluidos por `.gitignore` y no subidos: `*.apk` de la raíz, `build/`, `.gradle/`, `.kotlin/`, `local.properties`, `gradle-8.9/` y `wrapper/`. No se creó release pública ni se cambió la app o sus mejoras pendientes.
- Mejoras del panel lateral quedan para otra versión, según indicación de Rafa.

# Hito v1.21 — Panel lateral de descanso (2026-11-12)

## Revisión y APK verificados en Windows

- Project ID Engram `hercules`. ZIP del Escritorio `Hercules-v1.21-panel-descanso-fuentes.zip`, extraído en temporal autorizado `C:\Users\Cervantes\AppData\Local\Temp\opencode\hercules-v121-440f373d7206429baddc25a507f86955\Hercules-v1.21`.
- Comparación con `git -c core.autocrlf=false diff --no-index`: `app/src` completo y `app/build.gradle.kts` **ya coincidían con el ZIP**. No se sobrescribieron fuentes ni cambios locales; no fue necesario corregir código para validar.
- Inspeccionados `RestEdgePanel.kt`, `MainActivity.kt`, `SettingsScreen.kt` y manifiesto: panel opcional, desactivado por defecto, permiso `SYSTEM_ALERT_WINDOW` comprobado antes de mostrar; se oculta en onStart/onDestroy, al vencer, por «Ocultar», permiso perdido o preferencia apagada. Pantalla no interactiva/keyguard se comprueban por polling (500/1000 ms), no se ha probado el instante físico de ocultación.
- Usa `TYPE_APPLICATION_OVERLAY` y vencimiento monotónico existente; «Saltar» llama al método existente, «Ocultar» no cancela. No añade servicios, alarmas, sonido, nutrición ni acceso a Internet. El ajuste del panel es local a este dispositivo, no se añade al respaldo v6. Room **12** y respaldo **v6** intactos.
- Documentación oficial de WindowManager consultada: el tipo de overlay requiere permiso y Android puede cambiar su posición/tamaño/visibilidad; no se garantiza supervivencia del proceso ni aparición sobre todas las pantallas de otras apps. Sin servicio de mantenimiento/reinicio automático.
- Ejecutado **`pwsh -NoProfile -ExecutionPolicy Bypass -File ".\tools\Validate-Hercules.ps1"`** con JBR **21.0.10** y SDK **35** locales: **BUILD SUCCESSFUL**. **176/176 pruebas JVM en 20 suites**, 0 fallos/errores/omitidas. Las suites existentes no son pruebas físicas ni automatización del nuevo overlay.
- Lint **0 errores / 19 avisos** (`app/build/reports/lint-results-debug.txt`); +1 respecto a v1.20 por `RtlHardcoded` (`Gravity.RIGHT`). Se conserva la posición derecha física descrita en el paquete, sin convertirla en END ni suprimir el aviso. `assembleDebug` OK.
- `gradlew.bat :app:compileDebugAndroidTestKotlin`: **BUILD SUCCESSFUL**, instrumentadas **compiladas, NO ejecutadas**. `python tools/check_sql.py`: **19/19 PASS**, comprobación SQL parcial.
- APK: **`C:\Users\Cervantes\Desktop\Hercules\Hercules-v1.21-panel-descanso.apk`**. `aapt dump badging`: **1.21 / versionCode 22**, **com.rafa.hercules**, ícono intacto, sin INTERNET; nuevo permiso opcional SYSTEM_ALERT_WINDOW además de los existentes.
- `apksigner verify --print-certs` de v1.21 y `Hercules-v1.20-discos-progresion-resumen.apk`: ambos válidos y **certificado SHA-256 idéntico `3406e2f940aee983892ae2fe7ccf6d7e6e9f5e74b54f5e1bb80583081eafaa1e`**. Sin claves nuevas ni lectura del keystore.
- Gradle raíz, Wrapper, `local.properties`, configuración OpenCode/harness/GGA y todos los cambios existentes preservados. Staging SHA-256 (`git ls-files --stage`, unido por LF): `F8D2D0F8335FA2FAE4A42BD2D8DC6338BCBD00526AB5F3EADD88FF60366D0C1F`, **idéntico antes/después**; hashes de Gradle raíz y `.gga` también idénticos. `git diff --check` sin errores (solo avisos CRLF). Sin commits/push ni instalación/desinstalación en teléfono.
- **Pendiente en teléfono:** actualizar sobre v1.20 conservando datos; activar panel y probar permiso concedido/denegado/revocado, Inicio/otra app, expandir/abrir/saltar/ocultar, regresar, apagado/bloqueo/desbloqueo, vencimiento sin beep duplicado, navegación gestual, rotación/multiventana y cierre del proceso. Revisar texto ampliado/teclado y regresión de sesión, resumen/compartir, discos, progresión y respaldo. No se declara revisión visual ni funcionamiento físico comprobado.

## Registro original del paquete

- Ajustes → Panel de descanso, apagado por defecto. Con el permiso de superposición concedido por el usuario, aparece en el borde derecho al salir de Hércules durante un descanso activo. Tocar expande Abrir Hércules, Saltar y Ocultar. No se muestra con la pantalla apagada o bloqueada, al volver a la app o después de vencer. Ocultar no cancela el descanso; Saltar usa la acción existente.
- El panel usa el vencimiento monotónico ya persistido; no agrega alarmas ni sonido. El panel depende de que Android mantenga viva la actividad/proceso; si lo cierra, desaparece y no se reinicia solo. El aviso de descanso existente sigue independiente, sujeto a los permisos y al comportamiento de Android.
- versionCode 22, Room 12 y respaldo v6 como en v1.20. Nutrición queda fuera de esta entrega.
- Verificación del entorno anterior del paquete: 19 comprobaciones SQL parciales, sintaxis XML y diff; entonces el Wrapper no pudo descargar Gradle. **Compilación, Lint y APK ya verificados en este Windows según evidencia superior**; las pruebas físicas siguen pendientes.

# Hito v1.20 — Discos, progresión y resumen (2026-11-12)

- «Discos» en sesión activa: kg/lb, carga de barra configurable, combinación simétrica por lado con tamaños habituales; si no es exacta, propone la mayor carga realizable sin exceder el objetivo. No administra inventarios.
- El editor admite «Reps máx. (opcional)». Un rango como 8–12 conserva el mínimo como objetivo y activa sugerencias. Para sugerir +2.5 kg o +5 lb, la última referencia debe corresponder a la misma rutina y bloque, con el mismo rango y número de series, y todas las series de trabajo completadas con la misma carga y al menos las reps máximas. Excluye dropsets y cardio. «Usar» rellena la carga de las series de trabajo sin completar registros ni cambiar la rutina. Si la última referencia no es comparable, no sugiere.
- Al finalizar y guardar se muestra un resumen inmediato con duración, series de trabajo, ejercicios y récords de la sesión. Compartir genera un PNG local y abre el selector nativo de Android. No comparte automáticamente.
- Room 11→12 añade dos columnas opcionales; las rutinas existentes conservan el objetivo fijo y los registros se preservan. Respaldo v6, compatible al importar con v1–v5; no usar respaldos v6 en versiones antiguas.
- Verificado en este entorno: 10 pruebas JVM de dominio (5 nuevas, 5 de récords; Kotlin disponible en el ZIP y stubs solo de anotaciones Room), 19 comprobaciones parciales SQLite, sintaxis XML y diff. Agregada prueba de respaldo que queda pendiente de ejecución completa.
- Pendiente: Gradle no pudo descargar su distribución, no se generó APK y quedan sin ejecutar compilación Android, KSP/Room (incluida la generación de 12.json), Lint, pruebas instrumentadas y prueba de interfaz/compartir en teléfono. Ejecutar `.\tools\Validate-Hercules.ps1` en el proyecto Windows con su SDK/JDK; conservar el certificado con el que se instaló v1.19. versionCode 21 / versionName 1.20.

# Hito v1.19 — Récords personales e ícono (2026-11-12)

- Récord de fuerza por identidad de ejercicio/variante y número exacto de repeticiones. Compara contra sesiones terminadas anteriores y series previas de la sesión activa. Primera marca: referencia; empate: sin aviso. Normaliza lb/kg, excluye aproximaciones, cardio y tanto la raíz como las etapas de dropsets. Aviso junto a la serie completada, derivado de los datos existentes: recalcula al editar o desmarcar.
- Ícono adaptativo: opción 1 aprobada por Rafa, hidra violeta con H biselada en negativo; el arte queda dentro del recorte circular seguro.
- versionCode 20 / versionName 1.19. Room 11 y respaldo v5 intactos. No se cambian permisos, sustitución ni lógica del descanso. Se restauraron los tres archivos de Gradle de la raíz desde la copia anterior con las versiones registradas aquí.
- Verificado: 5 pruebas de dominio con JUnit y Kotlin 1.9.23 disponible en la distribución parcial del ZIP (stubs solo para anotaciones Room), 17 comprobaciones SQL existentes más un caso de la consulta nueva en SQLite, y margen circular del ícono. Esto no ejecuta Room/KSP ni valida la UI Compose.
- Pendiente: Android SDK 35 ausente; el Wrapper no pudo descargar Gradle y el Gradle parcial del ZIP no arranca. Sin APK generado, lint ni pruebas Android ejecutadas. En Windows usar `powershell -ExecutionPolicy Bypass -File .\tools\Validate-Hercules.ps1` con JDK/SDK configurados y el keystore que firmó v1.18 para poder actualizar encima.

# Hito v1.18 — Sustituir por hoy y aviso visual (2026-11-12)

Project ID Engram: `hercules`. Base comprobada **v1.17 / código 18**; el usuario declara v1.17 validada en teléfono (sin detallar casos). Los pendientes físicos de v1.17 de abajo son el registro de aquella entrega, no una negación de esa validación posterior. Se preservó todo el trabajo local staged/unstaged/untracked; sin commits ni cambios del harness.

## Estado y decisiones
- Entrega **v1.18 / versionCode 19**: `Hercules-v1.18-sustitucion-descanso.apk` en la raíz. Misma firma que v1.17: SHA-256 `3406e2f940aee983892ae2fe7ccf6d7e6e9f5e74b54f5e1bb80583081eafaa1e`. Application ID/permisos intactos, sin INTERNET. No se instaló ni desinstaló nada.
- Room continúa **11**, respaldo **v5**: bastan los campos existentes `exerciseId`, `occurrenceId` y objetivos por fila. Cambios en DAO, no en entidades/esquema ni formato de respaldo.
- Menú de sesión ACTIVE: «Sustituir solo por hoy». Reutiliza `ExerciseCatalogScreen` y su búsqueda sin acentos en modo selección, sin crear/eliminar ni filtros de tipo; candidatos activos del mismo tipo, excluyendo el original.
- Se confirma siempre la sustitución y el descarte antes de guardar (también cubre texto intermedio no persistido). Bloqueo explicado si alguna fila de la aparición está completada; `completedAt` también bloquea una fila completada y luego desmarcada para no eludir un bloque ya resuelto.
- DAO `substituteSessionExercise` transaccional revalida sesión ACTIVE, identidad de aparición/original, completados, confirmación de descarte, sustituto activo y tipo. Cambia solo esa aparición, principales/aproximaciones/etapas; conserva IDs, orden, ronda, grupo, objetivos y descanso. Limpia peso/reps/esfuerzo/escala/notas/minutos; unidad inicial del sustituto.
- La instantánea de ESA sesión ajusta exclusivamente la identidad efectiva del ejercicio en la posición sustituida; conserva nombre/objetivos. Evita comparar como idéntica una composición sustituida. Plantilla y sesiones anteriores no se editan; snapshots legados ausentes/desconocidos no se inventan.
- El colector recalcula `previousById` con el historial del sustituto. Formularios se recrean por ID + ejercicio y callbacks obsoletos comprueban el ejercicio esperado. «Usar anterior» conserva conversión y no completa/inicia descanso.
- Descanso fijo: estado IDLE/COUNTING/READY; READY muestra **«Siguiente serie»** con `primaryContainer/onPrimaryContainer` de los temas existentes, sin animación/destellos. Otro descanso lo reemplaza; salto, finalización, cancelación o importación lo limpian.
- UI solo lee tiempo; no programa/consume/cancela beeps. El receiver consume el token sonoro una vez dentro de LocalMutations y conserva el deadline/sesión/boot en las preferencias existentes. Así volver/reabrir tras vencimiento reconstruye READY sin repetir sonido ni reiniciar. «Saltar» borra estado/cancela explícitamente y nunca produce READY. Tras reinicio de dispositivo no se simula una alarma existente.

## Evidencia ejecutada
- Pruebas enfocadas `:app:testDebugUnitTest --tests "com.rafa.hercules.viewmodel.SessionSubstitutionTest" --tests "com.rafa.hercules.viewmodel.SessionViewModelTest"`: BUILD SUCCESSFUL. Un intento previo falló por diálogo colocado fuera de SessionScreen, corregido antes de validar.
- `tools/Validate-Hercules.ps1` final (repetido tras ajustes): **BUILD SUCCESSFUL**, JBR **21.0.10**, **165/165 pruebas JVM en 18 suites**, lint **0 errores / 17 avisos** (antes 18; eliminado el commit de limpieza tras beep), assembleDebug OK.
- `SessionSubstitutionTest` (7): aparición repetida intacta, estructura/objetivos y snapshot, respaldo JSON v5 ida/vuelta tras sustituir, bloqueo de aproximación/trabajo/bajada y revalidación al guardar, descarte, tipo/archivado/órdenes obsoletas/histórico, reanudación con nuevo ViewModel, Anterior del sustituto y copia 45.359237 kg→100 lb sin completar, historial efectivo sin tocar sesión previa, cardio sin transferir minutos, biserie sustituida con dropset y un único descanso (doble toque incluido).
- `SessionViewModelTest` (31): nuevo caso con reloj falso distingue COUNTING→READY al volver de segundo plano, nuevo descanso, salto→IDLE incluso tras reabrir, y limpieza al finalizar; no añade efectos sonoros de UI. Son pruebas JVM con DAO/timer falsos, no ejecución física de Room/alarma/UI.
- `:app:compileDebugAndroidTestKotlin`: **BUILD SUCCESSFUL**. Nuevas instrumentadas: Room rollback si falla la segunda fila de sustitución + persistencia/bloqueo/plantilla; preferencias reales con reloj controlado para consumo único del token, vencimiento tras reabrir, salto y boot/token obsoleto. **Compiladas, NO ejecutadas**.
- `python tools/check_sql.py`: **17/17 PASS** (SQL parcial). `apksigner verify --print-certs` v1.18/v1.17: certificados idénticos. `aapt dump badging`: 19 / 1.18, permisos intactos, sin INTERNET. `git diff --check`: sin errores, solo avisos CRLF. `rg` no está instalado; el intento de filtrar aapt falló y se repitió aapt sin filtro con éxito.
- `adb devices`: vacío. No hay evidencia de revisión visual o instalación de v1.18 en teléfono/emulador.

## Pendientes físicos / siguiente paso
- Actualizar sobre v1.17 sin desinstalar; comprobar datos reales, sustitución de una aparición repetida (también cardio), confirmación/bloqueo, cierre/reapertura, Anterior e historial del sustituto.
- Ejecutar `tools/Validate-Hercules.ps1 -Device` con dispositivo autorizado, incluidas pruebas Room transaccionales y preferencias del temporizador.
- Revisar ambos temas, teclado, texto ampliado y scroll; contraste del aviso fijo, expiración natural vs salto, regreso desde segundo plano y muerte de proceso sin repetir beep.
- Comprobar un descanso único/oportuno en biserie con dropset, pantalla bloqueada, música/Doze y pantalla encendida solo en sesión visible. No se promete ejecución tras cierre forzado. GGA sigue pendiente, no reintentado.

Archivos del incremento: nuevo `data/SessionSubstitution.kt`; DAO `data/AppDatabase.kt`; `viewmodel/SessionViewModel.kt`; `ui/SessionScreen.kt`, `ui/ExerciseCatalog.kt`; `utils/TimerManager.kt`; `data/ActiveSessionPresentation.kt`; fake DAO, tests JVM e instrumentadas citadas; versión en `app/build.gradle.kts`, ruta APK en `tools/Validate-Hercules.ps1`, esta memoria.

---

# Hito v1.17 — Sesión activa pulida (2026-11-12)

Project ID de Engram comprobado por raíz Git: `hercules`. Base real: v1.16 / código 17; árbol con numerosos cambios staged/unstaged y archivos sin seguimiento anteriores al incremento, preservados. Sin commits ni cambios del harness.

## Estado y decisiones
- Entrega **1.17 / versionCode 18**: `Hercules-v1.17-sesion-activa.apk` en la raíz. Firma SHA-256 `3406e2f940aee983892ae2fe7ccf6d7e6e9f5e74b54f5e1bb80583081eafaa1e`, comparada con v1.16: idéntica. Application ID y permisos intactos; sin INTERNET.
- Room sigue **11**, respaldo **v5**; sin cambios de entidades, migraciones, importación, historial ni mecanismo de alarma. No se instaló ni desinstaló la app.
- Cabecera muestra nombre de la instantánea (fallback a rutina para sesiones legadas), avance de series principales de fuerza sin aproximaciones/etapas; solo cardio muestra ejercicios completados. Duración y descanso fuera del scroll; «Saltar» disponible durante descanso.
- Peso con unidad, reps, Anterior y completar visibles; menú por aparición de ejercicio con kg/lb, RPE/RIR/notas y añadir bajadas por serie pendiente. El selector kg/lb del menú convierte las filas de esa aparición, no otras apariciones del mismo ejercicio.
- «Usar anterior» reutiliza exclusivamente la correspondencia comparable existente `previousById`: copia peso/reps, convierte a la unidad visible con LB_TO_KG exacto, conserva esfuerzo/notas/objetivos/completado y no inicia descanso. Los valores válidos se guardan mediante `editSeries`.
- Biseries intercaladas A/B por ronda; aproximaciones separadas y bajadas indentadas debajo de su principal. Se conserva la lógica de descanso de `completedRound` (todas las principales y etapas de la ronda, una sola vez).
- Pantalla encendida solo con sesión ACTIVE compuesta y lifecycle RESUMED; observer limpia/restaura propiedad al salir. Usa View.keepScreenOn porque el modificador Compose oficial requiere UI 1.9, superior a la dependencia actual; sin wakelock ni actualización de dependencias.
- Llegar a cero solo limpia la visualización (`clearExpiredRestDisplay`); no llama a cancelar la alarma. «Saltar» sí cancela explícitamente.

## Evidencia ejecutada
- `gradlew.bat :app:testDebugUnitTest --tests "com.rafa.hercules.data.ActiveSessionPresentationTest" --tests "com.rafa.hercules.viewmodel.SessionViewModelTest"`: BUILD SUCCESSFUL durante desarrollo.
- `tools/Validate-Hercules.ps1`: **BUILD SUCCESSFUL** con JBR 21.0.10, repetido tras los últimos cambios. **157/157 tests JVM**, 17 suites; **lint 0 errores / 18 avisos** preexistentes; assembleDebug OK.
- Pruebas añadidas: conteo mixto/cardio/vacío, conversión 100 lb↔45.359237 kg, misma unidad y null, preservación de campos al copiar, orden por ronda, copia persistida sin completar/descanso, nombre legado, biserie que espera última bajada y doble toque sin doble alarma. Son pruebas JVM con DAO/timer falsos, no toques de UI ni beeps físicos.
- `python tools/check_sql.py`: **17/17 PASS** (comprobación SQL parcial). `gradlew.bat :app:compileDebugAndroidTestKotlin`: **BUILD SUCCESSFUL**, solo compilación.
- `apksigner verify --print-certs` de v1.17 y v1.16: ambos válidos, certificado idéntico. `aapt dump badging`: 18 / 1.17, com.rafa.hercules, sin INTERNET. `git diff --check`: sin errores (solo advertencias CRLF).
- `adb devices`: lista vacía. No se ejecutaron instrumentadas ni revisión visual en dispositivo/emulador.

## Pendiente en teléfono / siguiente paso
- Actualizar sobre v1.16 sin desinstalar y comprobar conservación de datos, reanudación, historial y respaldo real.
- Revisar temas claro/oscuro, teclado abierto, texto ampliado, scroll y accesibilidad; comprobar «Usar anterior» con kg/lb desde UI.
- Confirmar pantalla encendida únicamente en sesión visible, apagado normal en segundo plano/historial, «Saltar», y aviso único al cerrar ronda con dropset. Precisión/Doze, bloqueo y música siguen requiriendo teléfono.
- Ejecutar `tools/Validate-Hercules.ps1 -Device` con dispositivo autorizado. GGA sigue pendiente por decisión previa; no reintentado.

Archivos del incremento: `ui/SessionScreen.kt`, `viewmodel/SessionViewModel.kt`, nuevo `data/ActiveSessionPresentation.kt`, nuevo `data/ActiveSessionPresentationTest.kt`, `viewmodel/SessionViewModelTest.kt`, `app/build.gradle.kts`, ruta de salida en `tools/Validate-Hercules.ps1` y esta memoria.

---

# Hito v1.16 — Editor, catálogo y ajustes visuales (2026-10-07)

Rediseño del editor de rutina (cabecera con volver, tarjetas compactas, controles 2×2 con etiquetas y unidades, contenedor de biserie, botón Guardar visible con el teclado) y nuevo catálogo de ejercicios (buscar sin acentos, filtros Todos/Fuerza/Cardio, añadir/crear/eliminar) accesible desde el editor. Inicio más compacto con plurales correctos; Ajustes con secciones en tarjetas; Progreso con módulos en contenedores, mapa anual con etiquetas de mes que arranca en las semanas recientes y métricas que distinguen "No aplica". Sin cambios de esquema Room (sigue **11**) ni de respaldo (sigue **v5**).

**Validación local REAL ejecutada con `tools/Validate-Hercules.ps1` (JDK 21.0.10 del jbr): BUILD SUCCESSFUL.**
- Tests JVM: **149 / 149 sin fallos** en 16 suites (nuevas: `ExerciseCatalogTest` 5, `RoutineDraftTest` 6, `MetricPresentationTest` 6).
- Lint: **0 errores / 18 avisos** (sin cambios respecto a v1.15).
- `python tools/check_sql.py`: **17/17 PASS**; `:app:compileDebugAndroidTestKotlin` OK (instrumentadas **NO ejecutadas**: sin dispositivo/emulador).
- APK `C:\Users\Cervantes\Desktop\Hercules\Hercules-v1.16-editor-catalogo.apk` (9.949.998 B, versionCode 17 / versionName 1.16, firma SHA-256 `3406e2f9…` **idéntica**, permisos intactos SCHEDULE_EXACT_ALARM/POST_NOTIFICATIONS/VIBRATE, sin INTERNET).

**Datos puros nuevos (JVM, sin Android):**
- `data/MetricPresentation.kt`: `StrengthVolumeState {NOT_APPLICABLE, INCOMPLETE, VALUE}` + `strengthVolumeState()` y `CardioDurationState {NOT_APPLICABLE, MISSING, VALUE}` + `cardioDurationState()`. Evita mostrar "0.0 kg·rep" en sesiones solo de cardio y confundir cardio sin duración con 0 minutos.
- `data/SessionSummary.kt`: `strengthSeriesCount`, `cardioSeriesCount`, `cardioDurationRegistered` (con valores por defecto) rellenados en `buildSessionSummary`.
- `data/ExerciseCatalog.kt`: `ExerciseFilter {ALL, STRENGTH, CARDIO}` + `filterExercises()` (búsqueda sin acentos con `Normalizer`, orden alfabético estable).
- `data/TextFormat.kt`: `countLabel(count, singular, plural)`.
- `data/RoutineEditing.kt`: `routineDraftChanged()`, `routineEditorGroups()` para el editor.

**UI:**
- `ui/ExerciseCatalog.kt` (nuevo): `ExerciseCatalogScreen` + `CreateExerciseDialog` compartido.
- `ui/RoutineScreen.kt`: `RoutineEditor` reescrito conservando todas las funciones (añadir/crear, reordenar, quitar, series/reps/descanso/cardio/bajadas y biseries) y `isEditing`; `swapRows`/`toggleBiserie` como funciones puras; `imePadding` + barra inferior fija; confirmación al salir vía `routineDraftChanged`. Inicio compactado y plurales con `countLabel`.
- `ui/SettingsScreen.kt`: secciones Apariencia/Respaldo/Almacenamiento agrupadas en `SettingsCard`.
- `ui/HistoryScreen.kt`: tarjeta de sesión con "Duración de la sesión" separada de "Cardio" y volumen de fuerza según estado; `AnnualHeatmap` reescrito con `LazyRow` semanal, etiquetas de mes y arranque en las semanas recientes (rango completo conservado, clic por día); módulos envueltos en `ProgressCard`.
- `ui/Previews.kt`: previews del editor y del catálogo (Claro/Oscuro/320 dp/fontScale 1.5).
- `app/build.gradle.kts` → 17 / "1.16"; `tools/Validate-Hercules.ps1` → `Hercules-v1.16-editor-catalogo.apk`.

**Límite declarado:** el asistente no admite entrada de imagen; **no pudo ver** las capturas de `design\` (paletas muestreadas por píxeles en v1.15). Las previews escritas **no equivalen a una revisión visual ejecutada** en dispositivo.

**Pendientes:** instrumentadas sin ejecutar (sin dispositivo/emulador); comprobación visual e instalación sobre v1.15 con datos reales en el teléfono; el catálogo no modela grupos musculares/variantes/equipamiento (limitación conocida, no se inventaron); GGA pendiente por decisión del usuario.

La información histórica que sigue describe v1.15 y versiones anteriores.

---

# Hito v1.15 — Base visual, temas e Inicio (2026-10-07)

Sistema visual compartido claro/oscuro sobre las referencias aprobadas, apariencia persistida y navegación Inicio/Progreso/Ajustes con rediseño de Inicio. Sin cambios de lógica de entrenamiento, de esquema Room (sigue **11**) ni de temporizadores; respaldo sigue **v5** (la apariencia NO se incluye).

**Validación local REAL ejecutada con `tools/Validate-Hercules.ps1` (JDK 21.0.10 del jbr): BUILD SUCCESSFUL.**
- Tests JVM: **132 / 132 sin fallos** en 13 suites (nuevas: `AppearancePreferencesTest` 4, `WeeklyConsistencyTest` 6, `RoutineStatsTest` 4).
- Lint: **0 errores, 18 avisos** (+2 informaciones) preexistentes; sin `NewApi` ni deprecaciones nuevas.
- `python tools/check_sql.py`: **17/17 PASS**. `:app:compileDebugAndroidTestKotlin` OK (instrumentadas **NO ejecutadas**: sin dispositivo/emulador).
- APK `C:\Users\Cervantes\Desktop\Hercules\Hercules-v1.15-temas-inicio.apk` (9.917.230 B), `com.rafa.hercules`, versionCode 16 / 1.15, firma SHA-256 `3406e2f9…` **idéntica**, permisos intactos (SCHEDULE_EXACT_ALARM, POST_NOTIFICATIONS, VIBRATE; sin INTERNET).

**Implementación:**
- `ui/theme/Theme.kt` (nuevo): paletas claro (`#E9F6FE` fondo, `#FFFFFF` tarjetas, acento `#0B66FE`) y oscuro (`#15181D`, `#1F232B`, acento `#9364FB`), `HerculesTypography`, `HerculesShapes`, `HerculesSpacing` (touch 48 dp) y `HerculesTheme`. Colores dinámicos **desactivados**.
- `data/AppearancePreferences.kt` (nuevo): `AppearanceMode {SYSTEM, LIGHT, DARK}` (valor vacío/desconocido → SYSTEM) persistido con DataStore (`apariencia`); “Sistema” es el valor inicial. El estado `null` inicial evita mostrar el tema equivocado durante la carga.
- `MainActivity.kt`: aplica el tema, ajusta las barras del sistema y usa `NavigationBar` de 3 pestañas (Inicio/Progreso/Ajustes); la sesión ocupa pantalla completa sin barra inferior.
- `ui/SettingsScreen.kt` (nuevo): Apariencia, Respaldo (reutiliza export/import de `HistoryViewModel`, movido aquí desde Progreso) y Almacenamiento (“solo en este dispositivo…; la apariencia no se incluye en el respaldo”).
- `ui/RoutineScreen.kt`: Inicio rediseñado (cabecera, selector semanal, banner de sesión activa, tarjeta de constancia semanal, secciones “Rutinas de este día”/“Todas las rutinas”/“Otras rutinas”, tarjetas con “X ejercicios · Y series”); se conservan crear/editar/compartir/asignar/desasignar/eliminar y reanudar sesión; el editor no cambió.
- `data/Streaks.kt` (`WeeklyConsistency`), `data/RoutineStats.kt` (nuevo) y `AppDatabase` (`observeActiveSession`, `observeRoutineExercises`): datos reales de Inicio sin inventar métricas. Reglas de constancia intactas (días únicos, solo `FINISHED`).
- `ui/Previews.kt` (nuevo): previews Claro/Oscuro/320 dp/fontScale 1.5 con datos ficticios identificados.

**Límite declarado:** el asistente no admite entrada de imagen; **no pudo ver** las capturas `design\Hércules_ panel celeste de entrenamiento.png` ni `design\Hércules en grafito y violeta.png`. Las paletas se obtuvieron por muestreo programático de píxeles y el diseño sigue la descripción textual del encargo: las previews escritas **no equivalen a una revisión visual ejecutada** en dispositivo.

**Pendientes:** instrumentadas sin ejecutar (sin dispositivo/emulador); comprobación visual e instalación sobre v1.14 con datos reales en el teléfono; GGA pendiente por decisión del usuario (no reintentado). La apariencia es preferencia local y no entra en el respaldo por diseño.

La información histórica que sigue describe v1.14 y versiones anteriores.

---

# v1.14 — Eliminar ejercicios/rutinas sin perder historial (2026-10-07)

Archivado no destructivo implementado: Room 10→11 añade `Exercise.origin` (USER/SEED/LEGACY) y `archivedAt` en `exercises`/`routines`; todas las filas previas quedan en LEGACY porque la semilla inicial y "Crear Propio" usaban el mismo generador UUID en cada versión (indistinguibles; verificado con dexdump de los APK v1.7/v1.13). Decisión del usuario: LEGACY sí es eliminable con aviso de "origen antiguo no determinable"; solo SEED queda protegido (sin botón + guarda en DAO). Respaldo v5 (importa v1..5; campos nuevos ausentes → LEGACY/null; valida `origin` y `archivedAt >= 0`). versionCode 15, versionName 1.14.

**Validación local REAL ejecutada con `tools/Validate-Hercules.ps1` (JDK 21.0.10 del jbr): BUILD SUCCESSFUL.**
- Tests JVM: **118 / 118 sin fallos** en 10 suites (nuevas: `ArchivadoTest` 7; `BackupCodecTest` 33→37).
- Lint: **0 errores, 18 avisos** (`app/build/reports/lint-results-debug.txt`).
- `python tools/check_sql.py`: **17/17 PASS** (corregida su regex: localiza la consulta ancla por nombre de función, ya no depende del orden de las `@Query`).
- Simulación sqlite de la migración 10→11 (script temporal): esquema migrado **== `11.json`** (columnas, `DEFAULT 'LEGACY'`, índices) y filas previas conservadas con origin LEGACY / archivedAt null.
- `:app:compileDebugAndroidTestKotlin` OK (incluye `SchemaMigrationTest` caso 10→11). Pruebas instrumentadas **NO ejecutadas**: sin dispositivo/emulador (`adb devices` vacío).
- APK `C:\Users\Cervantes\Desktop\Hercules\Hercules-v1.14-eliminar.apk`: 9.851.636 B, `com.rafa.hercules`, versionCode 15 / 1.14, firma SHA-256 `3406e2f940aee983892ae2fe7ccf6d7e6e9f5e74b54f5e1bb80583081eafaa1e` **idéntica**; permisos intactos (SCHEDULE_EXACT_ALARM, POST_NOTIFICATIONS, VIBRATE; sin INTERNET).

**Comportamiento:** botón "Eliminar" en el catálogo "Añadir Ejercicio" y en cada tarjeta de Inicio, con confirmación (nombre + "los entrenamientos anteriores se conservarán"; LEGACY añade el aviso de origen). Bloqueos: ejercicio en uso en rutina no archivada → error con nombres ("Retíralo de esas rutinas…"); rutina con sesión activa → "finalízalo o cancélalo"; ejercicio inicial SEED no se puede eliminar. Inicio/selector/opciones de inicio ven solo activos; historial, sesiones, métricas, columna "Anterior" y export del respaldo conservan todo (consultas separadas `observeActive*` vs `observe*`/`getAll*`). `saveRoutine` valida contra el catálogo activo. Acciones serializadas con `command {}`/`LocalMutations`.

**Pendientes:** instrumentadas sin ejecutar (migración 10→11 en dispositivo, `BackupRestoreTest` con v5, prueba manual de eliminación y de que el historial queda intacto). Ojo: v1.14 exporta respaldo **v5** → no importarlo en v1.13 o anterior. GGA pendiente por decisión del usuario; harness intacto. Rediseño Gojo/Toji y mascota aún fuera de alcance.

La información histórica que sigue describe v1.13 y versiones anteriores.

---

# MEMORY.md — Contexto y decisiones del proyecto

Última actualización: 2026-10-07. Propietario: Rafa. Nombre de app pendiente.

## Estado comprobado

El entorno Android se inspeccionó el 2026-10-06. Se encontró JDK 21 (versión del JBR de Android Studio en `C:\Program Files\Android\Android Studio\jbr`) y Android SDK.
La compilación base con `./gradlew :app:assembleDebug` fue exitosa tras corregir recursos faltantes, generando el APK en la ruta real: `app\build\outputs\apk\debug\app-debug.apk`.

**Versiones base estables elegidas (octubre 2026):**
- **Gradle 8.9 & AGP 8.7.0:** Compatibilidad confirmada con JDK 21.
- **Kotlin 2.0.0:** Soporte nativo de Compose sin configuración experimental externa.
- **Compose BOM 2024.09.02:** Soporte base actual comprobado para SDK 35.
- **Room 2.6.1:** Se conserva la versión estándar y no la serie 3.x para evitar la capa adicional de KMP en un proyecto que es puramente Android nativo.

El Hito 1 se considera **parcialmente implementado y compilado**, pero bloqueado en su validación física.
**Preparación de Emulador y Estado:**
- **Herramientas Instaladas:** `cmdline-tools` extraído de ZIP oficial. `sdkmanager` utilizado para instalar `system-images;android-35;google_apis;x86_64` y `emulator`.
- **AVD Creado:** `test_avd` configurado con API 35 (x86_64).
- **Ruta del APK:** `app\build\outputs\apk\debug\app-debug.apk`.
- **Bloqueo Observado:** Tras habilitar la Plataforma del hipervisor de Windows y reiniciar, la ejecución de `emulator -accel-check` sigue devolviendo el error: `Android Emulator hypervisor driver is not installed on this machine`. El inicio del emulador continúa bloqueado. No se han recreado AVD ni modificado configuraciones adicionales a la espera de instrucciones.
- **Diagnóstico Pendiente:** El origen exacto del diagnóstico `ProtectedPermissions` para `SCHEDULE_EXACT_ALARM` en Lint sigue documentado pero en investigación. No se atribuye de forma definitiva a Android 14 sin más evidencia, aunque sí se comprobó que `SCHEDULE_EXACT_ALARM` difiere del más restrictivo `USE_EXACT_ALARM`. Se conserva la supresión localizada en Lint y la comprobación de permiso en ejecución (`canScheduleExactAlarms`).
- **Pruebas (Transición a Online):** Se utilizará el emulador Appetize por limitaciones locales de firmware. Nota: Las herramientas de automatización de pruebas online (canvas visual) no permiten extraer selectores precisos ni interactuar libremente, por lo que el desarrollo continuará de manera autónoma integrando características de Biseries y Dropsets a nivel de datos y UI.
- **Implementaciones Recientes (Actualización Biseries y Selector Semanal 2026-10-06):**
  - **Biseries Completadas:** 
    - Lógica corregida para no disparar doble temporizador al remarcar una serie completada.
    - Se limitó la agrupación en el editor a un máximo de 2 ejercicios.
    - Se incluyó la opción "Desagrupar" para remover el agrupamiento de biseries.
  - **Pantalla de Inicio (Módulo 2, Parte 1):** 
    - Se rediseñó `RoutineScreen` (estado normal) para mostrar un selector horizontal de días de la semana (L, M, X, J, V, S, D).
    - Al tocar un día, el filtro muestra primero las rutinas del día ("Rutinas de este día") y debajo el resto de opciones ("Otras Rutinas"), permitiendo reasignar libremente sin que las opciones desaparezcan de la vista.
    - Se agregó el botón "Asignar a este día" o "Desasignar" en cada tarjeta de rutina.
  - **Historial de Sesiones (Módulo 2, Parte 2 Finalizada):**
    - Se integró un `Bottom Navigation` en la aplicación para alternar entre "Inicio" (Rutinas) y "Progreso" (Historial de sesiones finalizadas).
    - El historial lee directamente de Room (`state = 'FINISHED'`) utilizando `HistoryViewModel` y `HistoryScreen`.
    - Al abrir una sesión pasada, se reutiliza `SessionScreen` en modo solo lectura/histórico (desactivando el botón "Finalizar Sesión" por "Cerrar").
    - **Corrección de Registros Históricos:** Es posible modificar repeticiones, peso y esfuerzo desde el historial. La modificación se guarda sin desencadenar ningún temporizador de descanso adicional (validado verificando el estado de la sesión y previniendo ejecuciones dobles).
    - **Resumen y Esfuerzo General:** Se añadió una captura opcional del esfuerzo general de la sesión (0-10) al finalizarla, mostrada mediante un `AlertDialog`. En la pestaña de Historial, ahora se muestra un resumen por sesión que incluye la duración, cantidad de ejercicios distintos realizados (solo los que tienen registros completados), el conteo de series de trabajo reales (excluyendo aproximaciones y sub-etapas de dropsets), y el volumen de trabajo total levantado (normalizado a kilogramos para mantener consistencia métrica al cruzar datos de kg/lb, sumando también las bajadas de los dropsets). Adicionalmente se desglosa el volumen de aproximaciones de manera independiente.
  - **Constancia y Rachas (Módulo 3, Parte 2 Finalizada):**
    - Se implementó un calendario mensual interactivo (`LazyVerticalGrid`) en la pantalla de historial. Los días que tienen al menos una sesión de entrenamiento aparecen remarcados, formando una especie de mapa de calor base para medir la consistencia.
    - Al tocar un día específico en el calendario, la lista de sesiones de abajo se filtra inmediatamente para mostrar únicamente los entrenamientos de ese día (y al tocar la misma fecha nuevamente se borra el filtro para mostrar todas).
    - **Metas Configurables:** El usuario puede configurar su meta semanal (1-7 días) desde el Historial.
    - **Registro Histórico de Metas:** Se implementó una nueva tabla `goal_changes` (`MIGRATION_6_7`) que registra cuándo cambió la meta. Esto permite que el cálculo de la racha evalúe cada semana contra la meta que estaba vigente en ese momento, impidiendo recalcular o romper rachas pasadas.
    - **Cálculo de Rachas:** Se añadió cálculo de racha actual y mejor racha de semanas cumplidas. Las semanas se agrupan de lunes a domingo. Las sesiones vacías (sin series completadas) se excluyen. La semana en curso no rompe la racha si todavía no se ha alcanzado la meta, dándole tiempo al usuario de completarla hasta el domingo.
  - **Gráficas y Mapa Anual (Módulo 3, Parte 3 Finalizada):**
    - Se agregó el "Mapa Anual estilo GitHub" (`AnnualHeatmap`) que funciona independientemente del calendario mensual. Muestra visualmente la densidad de entrenamientos en un `LazyHorizontalGrid` y permite interactuar con los días seleccionados.
    - Se incluyó `ProgressCharts` para visualizar progreso en gráficas lineales mediante `Canvas` de Compose.
    - Las gráficas actuales incluyen esfuerzo general por rutina comparable a lo largo del tiempo, y evolución del volumen total por sesión (con un padding del 10% en el eje para mejor visibilidad, considerando solo las series principales y con normalización de peso).
  - **Respaldo local:** 
    - Implementación de un exportador/importador JSON (`BackupManager.kt`) accesible desde un botón de configuración en la cabecera del historial.
    - Exporta `routines`, `exercises`, `routine_exercises`, `sessions`, `series`, y `goals` a un archivo `.json` de forma íntegra.
    - La importación se efectúa dentro de una `transaction` atómica para evitar cambios parciales. Elimina el esquema previo y lo regenera a partir del respaldo validado.
    - Se añadió un diálogo de confirmación explícito advirtiendo sobre el borrado y recomendando exportar, manejando los errores de esquema sin crashear.
  - Comprobaciones y Tests: La app compila y se comprobaron unit tests (`testDebugUnitTest`). 
    - Se ejecutaron **7 pruebas unitarias** en total comprobadas según los reportes, abarcando lógica de `SessionViewModel` e `HistoryViewModel`.
    - Lint permanece **pendiente** debido a un error de permisos con la caché temporal en Windows (`El proceso no tiene acceso al archivo porque está siendo utilizado por otro proceso`), por lo que no ha podido emitir un reporte final limpio.
    - Se añadió `HistoryViewModelTest` para corroborar el cálculo de rachas, resolviendo detalles específicos de cálculo de semanas y fin de año (utilizando fechas fijas para evitar inconsistencias de cruce de años) así como evaluación de la meta en curso al finalizar cada semana. Se documenta explícitamente que un cambio de meta en domingo permite completar la semana si el usuario logra el objetivo de días antes de cerrar el lunes, sin corromper la constancia.
    - El mapa de calor del historial se confirmó que muestra los 12 meses naturales precisos, usando el lunes anterior como inicio posicional de la cuadrícula.
  - Compilación de este módulo con `versionCode = 8` y `versionName = "1.7"`.
- **Corrección v1.8 (bug descanso ignorado):**
  - **Causa real:** `SessionViewModel.completeSeries()` tenía `val durationMs = 90000L` fijo en código (línea 164). El valor `targetRestMs` guardado en `RoutineExercise` nunca se leía al iniciar el temporizador.
  - **Corrección:** Se añadió el método `getRoutineExerciseForSession(sessionId, exerciseId)` en `GymDao`, que busca la configuración de la rutina mediante JOIN entre `sessions` y `routine_exercises`. `completeSeries` ahora lo usa para obtener `targetRestMs`; el fallback a 90 s solo aplica cuando la sesión no tiene rutina asociada (sesión libre).
  - **No hay migración de esquema:** La corrección es de lógica, no de datos. Los datos existentes en Room no se modifican.
  - **Pruebas de regresión automatizadas añadidas** (`SessionViewModelTest`):
    - `completeSeries usa targetRestMs de la rutina cuando es 120 segundos` → verifica `lastDuration == 120_000L`
    - `completeSeries usa targetRestMs de la rutina cuando es 60 segundos` → verifica `lastDuration == 60_000L`
    - `completeSeries usa 90 segundos como fallback cuando la sesion no tiene rutina` → verifica fallback correcto
  - **Pruebas ejecutadas:** `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL. **10 pruebas unitarias** en total (7 previas + 3 nuevas de regresión), todas pasadas.
  - Lint: pendiente de aprobación limpia (error de caché de archivos en Windows, no relacionado con la corrección).
  - Compilación con `versionCode = 9` y `versionName = "1.8"`.
  - APK guardado como `Hercules-v1.8-descanso-corregido.apk` (9.7 MB).
  - **Pendiente de verificación en teléfono real:** que el contador y la alarma en segundo plano muestren el valor configurado (p. ej. 120 s).
- **Mejoras UI y Cancelación v1.9:**
  - **Redistribución de UI:** Se mejoró `SeriesRow` en `SessionScreen.kt`. La columna "Anterior" se movió a su propia fila arriba de los campos. Los campos de Peso, Reps y Esfuerzo (junto a sus botones de unidad y escala) ahora tienen más ancho (`Modifier.weight()`) para acomodar decimales largos (ej. 120.5) sin cortarse, y el `Checkbox` de completado se ajustó con un ancho mínimo. Los dropsets mantienen su indentación (`padding(start = 24.dp)`).
  - **Cancelar Entrenamiento:** Se añadió la acción "Cancelar" dentro de la sesión activa, precedida por un `AlertDialog` de confirmación. Al cancelar, el `SessionViewModel` actualiza el estado a `CANCELLED`, detiene el temporizador en curso (`timerManager.cancelRestAlarm()`) y cierra la sesión.
  - **Exclusiones de Canceladas:** Dado que todas las consultas de la aplicación (historial, rachas, gráficas y la query de `Anterior` en Room) se basan estrictamente en `state = 'FINISHED'`, las sesiones canceladas quedan automáticamente excluidas de estadísticas y métricas, pero persisten en la base de datos sin alterar la rutina original. Se añadió prueba unitaria confirmando `state = 'CANCELLED'` y la cancelación de alarmas.
  - **Pruebas ejecutadas:** `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → **11 pruebas unitarias** aprobadas. 
  - Lint reportado como **aprobado y limpio**.
  - Compilación con `versionCode = 10` y `versionName = "1.9"`.
  - APK guardado como `Hercules-v1.9-ui-y-cancelar.apk`.
- **Hito v1.10 — Cardio en rutinas y "Compartir rutina" (2026-10-07):**
  - **Datos (Room 7→8):** `Exercise.type: String?` (null = fuerza, `"CARDIO"` = cardio, con extensión `isCardio`), `RoutineExercise.targetDurationMs/targetDropsets` (extensiones `durationMinutes`, `dropsetStages`), `Series.durationMs` (minutos realizados; null = vacío, nunca 0). `MIGRATION_7_8` añade columnas anulables **sin cláusula DEFAULT** para que el esquema Room coincida con las entidades; sin borrado destructivo. `GymDao.observeExercises()` expone `Flow<List<Exercise>>`.
  - **Sesión:** un ejercicio cardio genera **una sola fila de serie**, sin calentamientos/series/reps/peso. Completar cardio **no** dispara la alarma de descanso (el beep de fuerza no cambia). `targetDropsets` es solo pista en sesión (se añaden con `+D`); no se auto-generan series de dropset para no forzar su cumplimiento.
  - **Historial y progreso:** `buildSessionSummary` (función pura en `data/SessionSummary.kt`) separa cardio: no cuenta series de trabajo ni volumen y suma `cardioMinutes`; racha y mapas de calor sin cambios (cardio cuenta como actividad). La gráfica de volumen solo pinta sesiones con trabajo de fuerza para no hundir la curva.
  - **Respaldo y compartir:** exportación a esquema **v2** (importa v1..2; campos ausentes → null). `buildRoutineShareText` (`data/RoutineShare.kt`) genera texto de plantilla **sin datos personales**, compartido con `ACTION_SEND` desde la tarjeta de rutina y el editor.
  - **Verificación ejecutada:** `gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → BUILD SUCCESSFUL; **24 pruebas unitarias** (5 RoutineShare, 5 SessionSummary, 3 cardio añadidas a SessionViewModel); lint **0 errores / 10 avisos** preexistentes; APK `Hercules-v1.10-cardio-compartir.apk` (versionCode 11, versionName 1.10).
  - **SQL de la migración — comprobación parcial:** el script `MIGRATION_7_8` se ejecutó contra SQLite real con `node:sqlite` (9 comprobaciones: columnas anulables, filas legadas intactas, ida y vuelta de datos cardio). Es **solo validación de SQL**, no valida el esquema que Room espera ni el `MigrationTestHelper`. **Pendiente: migración instrumentada con Room en dispositivo/emulador.**
  - **Repo local:** se creó `git init` (sin commits), `.gga` (del template del harness) y `.gitignore` (excluye `gradle-8.9/`, `wrapper/` de distribución, `build/` y `*.apk`). **El Wrapper queda en Git y se comprobó:** `git check-ignore -v gradlew gradlew.bat gradle/wrapper/gradle-wrapper.properties gradle/wrapper/gradle-wrapper.jar` → sin resultados (nada ignorado) y los cuatro aparecen como `A` en el índice.
  - **GGA bloqueado (dos fallos encadenados):** (1) `gga run` envía el contenido íntegro de los archivos staged + AGENTS.md como argumento de `opencode run`; con ~142 KB de .kt supera el límite de Windows (~32767 caracteres) → `Argument list too long` (exit 126). Solución técnica: revisión por lotes de archivos staged (comprobado: los lotes ya no fallan por tamaño). (2) El overlay de `Review-GGA.ps1` define un agente con permisos `deny` y OpenCode devuelve `Error from provider (Console): OpenCode's free tier can only be used from within OpenCode`. Aislado con pruebas: agente propio que incluye **cualquier entrada `permission` con `deny`** → rechazo; sin `permission` o con `allow` → funciona; `opencode run` directo con big-pickle → funciona.
  - **Decisión del usuario (2026-10-07):** GGA queda como **pendiente documentado**. **No** retirar los permisos `deny` del overlay ni modificar el harness para forzar la revisión. La revisión GGA **no** se ha ejecutado con éxito; no equivale a tests fallidos.
  - **APK del hito (ruta absoluta):** `C:\Users\Cervantes\Desktop\Hercules\Hercules-v1.10-cardio-compartir.apk` (9.736.952 bytes, versionCode 11, versionName 1.10).
  - **Pendiente en dispositivo real:** migración 7→8 con datos existentes, registro de cardio en sesión e historial, compartir rutina e instalación del APK (sin emulador: hipervisor no instalado).
- **Hito v1.11 — Gráficas por ejercicio y comparación de rutinas (2026-10-07):**
  - **Continuidad de actualización (evidencia, sin atribuir a migración):** los APK v1.7, v1.8, v1.9 y v1.10 de disco comparten `applicationId com.rafa.hercules`, `versionCode` ascendente 8→11 y el **mismo certificado debug** (`3406e2f940aee983892ae2fe7ccf6d7e6e9f5e74b54f5e1bb80583081eafaa1e`, keystore sin cambios desde 2026-06-27, sin `signingConfig` propio). `AndroidManifest` tiene `allowBackup="true"`. **No hay evidencia de firma incompatible ni de versionCode descendente**; la causa exacta de la pérdida de datos al instalar v1.10 **se desconoce** (el usuario tampoco la sabe) y queda documentada como incertidumbre, no atribuida a la migración.
  - **Datos (Room 8→9):** `Session.routineSnapshot: String?` con `MIGRATION_8_9` (`ALTER TABLE sessions ADD COLUMN routineSnapshot TEXT`, anulable sin DEFAULT, sin tocar filas). Validada con `node:sqlite`: columna creada, filas previas en `null`, conteo intacto. **Solo validación de SQL; falta migración instrumentada Room en dispositivo/emulador.**
  - **Instantánea de rutina:** `buildRoutineSnapshot` (`data/RoutineSnapshot.kt`) serializa de forma determinista (independiente del orden de entrada) nombre + ejercicios/objetivos al **iniciar** la sesión; `routineSnapshotSignature` da null si falta o la versión es desconocida. Editar la plantilla después no altera la instantánea guardada (test en `SessionViewModelTest`). Sin instantánea no se supone equivalencia: comparabilidad limitada.
  - **Métricas por ejercicio:** `buildExerciseMetrics` (`data/ExerciseMetrics.kt`, pura) da por sesión: mayor carga + reps de esa misma serie, reps totales, volumen kg·rep (etapas de dropset una vez, distinción en `workSeriesCount`), minutos de cardio; filtro de rango de fechas por `startTime`; solo `FINISHED` + series completadas; sin datos → lista vacía o `null`, nunca cero inventado; no mezcla ejercicios distintos. Conversión unificada en `data/Units.kt` con **`LB_TO_KG = 0.45359237` exacto** (antes había dos literales `0.453592` en `SessionSummary` y `ProgressCharts`; ahora ambos usan `seriesWeightKg`).
  - **Comparación de rutinas:** `buildRoutineComparison` (`data/RoutineComparison.kt`, pura) agrupa por firma de instantánea y devuelve duración (null sin fin, no 0), esfuerzo general de sesión (separado del RPE/RIR por serie) y volumen de fuerza; `comparabilityLimited` si hay sesiones sin instantánea o >1 composición. Recalcula al corregir el historial (test). Respaldo sube a **esquema v3** (importa v1..3; `routineSnapshot` ausente → null).
  - **UI:** `ui/ProgressDetail.kt` añade en Progreso "Por ejercicio" (selector de ejercicio/variante solo con registros, chips 30d/90d/12m/Todo, gráficas + **lista de valores en texto** para que una sola sesión sea legible) y "Comparación por rutina" (selector, tabla fecha/duración/esfuerzo/volumen, aviso de comparabilidad limitada). `HistoryViewModel` expone `routines`.
  - **Verificación ejecutada:** `gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → BUILD SUCCESSFUL; **46 pruebas unitarias** (22 nuevas: 10 ExerciseMetrics, 9 RoutineComparison+instantánea, 3 snapshot en SessionViewModel), 0 fallos; lint **0 errores / 10 avisos** preexistentes. APK `C:\Users\Cervantes\Desktop\Hercules\Hercules-v1.11-graficas-rutinas.apk` (9.769.716 bytes, versionCode 12, versionName 1.11, firma `3406e2f9…` idéntica a versiones previas).
  - **Pendiente en dispositivo real:** migraciones 7→8 y 8→9 con datos existentes, selector/rango/comparación en UI, respaldo ida y vuelta v3, instalación actualizando sobre v1.10 (sin emulador: hipervisor no instalado).
- **Hito v1.12 — Validación y corrección del respaldo (2026-10-07):**
  - **Evidencia de continuidad (registrada sin sobreafirmar):** el usuario confirmó que actualizó desde v1.10 conservando sus datos. Eso es evidencia de que la migración 8→9 funcionó en su teléfono real; **no** prueba el resto de migraciones (la pérdida v1.10 sigue sin atribución conocida).
  - **Nuevo `data/BackupCodec.kt` (puro, JVM):** `serialize` emite esquema v3 idéntico al anterior; `parse` acepta versiones 1..3 y valida antes de tocar la BD: JSON completo, campo `version` presente y numérico, colecciones requeridas (`goals` opcional), filas objetos, ids no vacíos ni duplicados, referencias foráneas reales (`routineExercises→routines/exercises`, `series→sessions/exercises`, `series.parentId→series`), y valores: estados {ACTIVE, FINISHED, CANCELLED, ABANDONED}, unidades {kg, lb}, escalas {RPE, RIR}, `overallEffort` 0..10, `assignedDay`/`targetDays` 1..7, pesos/reps/effort/duraciones ≥ 0. Errores con mensaje claro en español (`BackupFormatException`), p. ej. "Versión de respaldo no soportada: 99. Usa un respaldo con versión entre 1 y 3."
  - **Reglas documentadas (por qué NO se rechazan):** `sessions.routineId` colgante se acepta (la app permite borrar rutinas sin clave foránea y sus sesiones quedan así); `effort` sin cota superior y `endTime` sin orden con `startTime` (la interfaz admite esos valores, un respaldo no debe rechazar lo que la app pudo generar).
  - **`BackupManager`:** export ahora lee **dentro de una transacción** (archivo consistente ante escrituras concurrentes); importa parsea+valida todo **antes** de la transacción, luego `clearAllTables` + inserts atómicos; si el respaldo no trae `goals` repone la meta por defecto `('default_goal', 0, 3)` igual que la migración original. Gancho `testFailureAfterClear` (interno, solo pruebas) para forzar fallo a mitad de restauración y comprobar rollback.
  - **Importar no deja avisos zombis:** `SessionViewModel.refreshAfterImport()` (llamado desde `MainActivity` vía `onImported` en `HistoryScreen`) cancela la alarma de descanso pendiente (pertenecía a datos ya reemplazados) y recarga o limpia la sesión activa. Además `loadSessionData` ahora cancela el colector anterior (`sessionCollectJob`): antes, cambiar de sesión acumulaba colectores que podían sobrescribir el estado (bug latente preexistente, corregido aquí). La importación en sí no programa ni dispara beeps.
  - **Mensajes de error reales en UI:** el toast de error muestra el motivo (`e.message`), no el genérico "archivo inválido o corrupto"; el de éxito es "Datos importados con éxito" (sin pedir reinicio).
  - **Preferencias:** no hay preferencias persistidas — el proyecto no usa DataStore/SharedPreferences (la dependencia `datastore-preferences` está declarada sin uso). Nada que añadir al respaldo; documentado en lugar de inventar datos.
  - **Verificación ejecutada:** `gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → BUILD SUCCESSFUL; **72 pruebas unitarias** (26 nuevas `BackupCodecTest`: round-trip completo, v1 sin goals, versión no soportada/no numérica/ausente, JSON truncado, colección ausente, fila no objeto, ids duplicados/vacíos, 4 referencias inexistentes, sesión con rutina colgante aceptada, 7 valores inválidos, effort sin cota) 0 fallos; lint **0 errores / 14 avisos** (los 4 nuevos son solo "hay versión más nueva" de las dependencias de prueba añadidas). `compileDebugAndroidTestKotlin` → OK: se compilan **7 pruebas instrumentadas** con Room en memoria (`BackupRestoreTest`: round-trip, doble import sin duplicados, rollback con fallo simulado, versión no soportada y truncado sin tocar datos, meta por defecto, sesión ACTIVE conservada).
  - **Pendiente (sin dispositivo/emulador):** ejecutar `:app:connectedDebugAndroidTest`; probar en el teléfono: exportar→importar con sus datos reales, error de versión mostrado con motivo, alarma de descanso cancelada tras importar.
  - **APK del hito (ruta absoluta):** `C:\Users\Cervantes\Desktop\Hercules\Hercules-v1.12-respaldo.apk` (9.786.100 bytes, versionCode 13, versionName 1.12, firma `3406e2f9…` idéntica a versiones previas).
- **Hito v1.13 — Correcciones A01–A17 (2026-10-07):**
  - Integradas desde `cambios-v1.12-v1.13.patch` con `git apply` (el árbol local coincidía exactamente con la base; sin conflictos ni pérdida de trabajo v1.12). Detalle completo en `CORRECCIONES.md` y `VALIDACION.md`.
  - Room 10 (migración 9→10), respaldo **v4** (importa v1..4), versionCode 14, versionName 1.13. Esquema Room exportado por primera vez: `app/schemas/com.rafa.hercules.data.AppDatabase/10.json`.
  - Validación local real: BUILD SUCCESSFUL, **107 / 107 tests** en 9 suites, lint **0 errores / 18 avisos**, `check_sql.py` 17/17, `compileDebugAndroidTestKotlin` OK (`SchemaMigrationTest` y `SessionIntegrityTest` compilados, sin ejecutar por falta de dispositivo).
  - APK `Hercules-v1.13-correcciones.apk` (9.835.252 B, versionCode 14 / 1.13, firma `3406e2f9…` idéntica). Pendientes v1.13: migración 9→10 en el teléfono con datos reales, alarmas en segundo plano, respaldo v4 ida y vuelta. Ojo: v1.13 escribe respaldo v4 → no hacer downgrade a v1.12 con ese archivo.
- **Limitaciones de la entrega actual:**
  - El rango de fechas son chips fijos (30/90/365 días/Todo), no un selector libre de fechas.
  - Sesiones antiguas sin instantánea se muestran con comparabilidad limitada; no se reconstruye su composición histórica.
  - Desde v1.15 la app sí persiste una preferencia (apariencia) en DataStore, pero **por diseño queda fuera del respaldo**: una preferencia de interfaz no debe viajar con los datos de entrenamiento. Las escalas RPE/RIR sí viajan dentro de cada serie.

## Objetivo

Aplicación Android personal y completamente local para llevar rutina semanal, registrar entrenamientos, consultar historial y recibir feedback mediante gráficas. Referencia de experiencia: Feast Fit, utilizada previamente por Rafa. Animaciones, sprites y videos de técnica no son prioritarios.

## Requisitos acordados

### Rutinas y entrenamiento

- Crear/editar rutinas, asignarlas a días, ordenar ejercicios y configurar series, repeticiones objetivo y descansos.
- Registrar valores reales por serie, conservando por separado el plan.
- Distinguir aproximaciones/calentamientos y series de trabajo.
- Permitir kg o lb y preferencia predeterminada; convertir valores al cambiar la unidad sin sobrescribir su procedencia.
- Permitir esfuerzo opcional por serie con selector RPE/RIR, guardando la escala original. No hacer conversión automática ni mezclar escalas en promedios.
- Mostrar columna Anterior con datos comparables de la última sesión; registrar notas cuando se necesiten.
- Guardado inmediato, retomar una sesión interrumpida, cambiar orden o sustituir un ejercicio durante la sesión sin modificar involuntariamente la plantilla.
- Corregir datos históricos y recalcular estadísticas.

### Biseries

Dos ejercicios en tarjetas agrupadas; registrar cada uno independientemente por ronda. El descanso de la ronda empieza al completar ambos. No dar por hecho que completar el primero termina el grupo. Definir acción explícita para omitir un ejercicio sin simular repeticiones.

### Dropsets

Representar bajadas indentadas dentro de su serie principal. Permitir configurarlas en la rutina y añadirlas durante entrenamiento. Cada etapa conserva peso, unidad, repeticiones y esfuerzo opcional.

Ejemplo acordado de Remo sentado:

| Serie / etapa | Peso | Reps |
| --- | ---: | ---: |
| 1 · Principal | 30 kg | 10 |
| ↳ Bajada 1 | 25 kg | 8 |
| ↳ Bajada 2 | 20 kg | 6 |
| 2 · Convencional | 30 kg | 10 |

Son dos series de trabajo; la primera contiene tres etapas. El volumen registrado del ejemplo, cuando su convención de carga sea comparable, es 30×10 + 25×8 + 20×6 + 30×10 = 920 kg·repeticiones. Guardar una etapa no inicia descanso; completar el bloque sí. Una línea/fondo agrupa la serie y sus bajadas.

### Temporizadores

- Duración total: comienza al iniciar la rutina y termina al finalizar; incluye descansos y permite pausa manual.
- Descanso: comienza al completar la unidad de trabajo correspondiente; cuenta hacia atrás y avisa con beep, vibración opcional.
- Ajustar o saltar descanso; no iniciar descanso innecesario al terminar la sesión.
- Ambos conservan el tiempo correcto al cambiar de app y bloquear pantalla. Aviso de descanso en segundo plano es requisito de primera versión.
- Probar en teléfono real con música y audífonos. Forzar detención y reiniciar teléfono no se consideran equivalentes a dejar la app en segundo plano.

### Pantallas y referencia visual

- Inicio: selector semanal horizontal, día resaltado y rutina seleccionada.
- Entrenamiento: tarjetas verticales con scroll, columnas Anterior/Reps/Peso/Esfuerzo y finalización por serie.
- Resumen: duración, volumen registrado, ejercicios y series; detalle debajo.
- Progreso: estadísticas por periodo, gráficas, mapa de calor anual estilo GitHub y calendario de sesiones.
- Calendario: tocar un día permite revisar las sesiones registradas.
- Propuesta visual inicial aceptada como referencia: oscuro, acentos azules, pestañas Inicio/Progreso; editor de rutinas desde Inicio y ajustes arriba.

Las cuatro capturas revisadas muestran: inicio sin rutina asignada, estadísticas/mapa anual, calendario/racha y detalle de sesión pasada. El detalle muestra series de aproximación, kg/lb, RPE, datos pasados y notas. No hay referencia completa de pantalla activa. No copiar el aviso de pago, nombre de coach, fotografías o sprites de la app original.

### Feedback y comparaciones

- Comparación principal por ejercicio y variante; comparación de sesión por identidad y versión de rutina.
- El día semanal organiza la rutina; no identifica qué registros son equivalentes. Pecho realizado el martes sigue comparándose con pecho, aunque antes fuera lunes.
- Versionar cambios importantes en plantillas; conservar instantánea histórica. Marcar sesiones modificadas como contexto distinto cuando corresponda.
- Gráficas de peso/repeticiones por ejercicio y esfuerzo/duración por rutina. RPE/RIR por serie se filtran por escala, y aproximaciones se separan de trabajo.
- Valoración general de sesión propuesta y aceptada en el planteamiento: esfuerzo percibido 0–10, separado del esfuerzo de cada serie. Pendiente confirmar si captura obligatoria u opcional; implementar inicialmente opcional para no bloquear el guardado.
- Más esfuerzo o más volumen no equivale automáticamente a mejor progreso. Comparar resultados y esfuerzo en contextos compatibles; no puntuar pierna contra pecho por kilos totales.
- Sin historial comparable o suficiente información, mostrarlo explícitamente y evitar conclusiones inventadas. Feedback inicial descriptivo, sin cambiar automáticamente rutinas.

### Constancia

Mapa de calor por días con actividad registrada y racha por semanas consecutivas que cumplen una meta configurable de días (ejemplo: cinco). Días de descanso no rompen la racha.

Propuesta de implementación, pendiente de validación con uso: contar días distintos con una sesión de trabajo finalizada; mantener parciales visibles pero separados. Semana lunes–domingo en zona local del teléfono. La semana actual en curso no rompe racha antes de cerrar. Conservar objetivo vigente en cada semana para no recalcular el pasado al editar la meta. El porcentaje completado como intensidad del mapa queda para una iteración posterior.

### Respaldo y alcance

Exportar/importar datos a archivo local. Sin cuenta, backend o dependencia de internet en ejecución. No incluye nutrición, fotos corporales, comunidad, entrenador remoto, pagos, smartwatch o IA en nube. No desarrollar nuevas funciones sin incorporarlas primero al alcance.

## Propuesta técnica, no requisito ya aprobado

Android nativo: Kotlin + Jetpack Compose + Material 3; ViewModel/StateFlow; Room para registros y DataStore para preferencias. Justificación: plataforma única, datos locales y control directo de capacidades Android. Evitar infraestructura de sincronización innecesaria. Fijar versiones estables compatibles al iniciar, no dentro de esta memoria antes de verificar el entorno.

## Skills investigadas y selección recomendada

Investigación documental del 2026-10-06; no instalación ni evaluación ejecutando un proyecto.

| Skill | Origen | Uso recomendado | Condición |
| --- | --- | --- | --- |
| build-gym-tracker | Propia, incluida en este paquete | Reglas y validación de registro, persistencia, temporizadores y progreso | Base específica del proyecto |
| android-cli | android/skills, autoría Android/Google | Herramientas Android, entorno, dispositivos y consulta de documentación | Verificar disponibilidad de la CLI; no confundirla con el antiguo SDK `android` |
| testing-setup | android/skills, autoría Android/Google | Elegir/configurar pruebas unitarias, UI e instrumentadas | Adoptar solo infraestructura necesaria; no instalar todos los frameworks que enumera |
| modern-jetpack-compose | anhvt52/jetpack-compose-skills, comunitaria | Estado, navegación, accesibilidad e interfaz Compose | Referencias a BOM 2024/2025: contrastar versiones y APIs actuales |

La skill propia concentra el procedimiento del flujo de entrenamiento; los requisitos viven aquí para evitar duplicación. Las externas son candidatas, no una garantía de calidad ni una dependencia de ejecución de la app. No usar skills web/React por defecto para una app Android nativa. Tampoco instalar migraciones XML, facturación o TV cuando no corresponden.

### Fuentes consultadas

- Antigravity skills y rutas: https://antigravity.google/docs/skills/
- Antigravity reglas AGENTS.md: https://antigravity.google/docs/rules/
- Guía oficial de creación de skills: https://codelabs.developers.google.com/getting-started-with-antigravity-skills
- Catálogo Android: https://github.com/android/skills
- android-cli: https://github.com/android/skills/blob/main/devtools/android-cli/SKILL.md
- testing-setup: https://github.com/android/skills/blob/main/testing/testing-setup/SKILL.md
- Compose comunitaria: https://github.com/anhvt52/jetpack-compose-skills/tree/master/modern-jetpack-compose
- Arquitectura recomendada: https://developer.android.com/topic/architecture/recommendations
- Room: https://developer.android.com/training/data-storage/room
- Alarmas: https://developer.android.com/develop/background-work/services/alarms

## Instalación de los documentos en Antigravity

Colocar `AGENTS.md` y `MEMORY.md` en la raíz del proyecto. Colocar el archivo entregado `SKILL.md` en `.agents/skills/build-gym-tracker/SKILL.md`. La documentación actual de Antigravity usa `.agents/skills`; admite `.agent/skills` por compatibilidad. Verificar reconocimiento en Customizations. MEMORY no es un mecanismo de memoria automática: AGENTS instruye leerlo y mantenerlo.

Para las skills externas, copiar la carpeta completa elegida, incluyendo referencias, recursos y licencia, a `.agents/skills/<nombre>/`, o usar el instalador compatible con la versión disponible. Revisar y fijar una revisión del repositorio antes de incorporarla. No basta copiar SKILL.md si referencia otros archivos.

Primer encargo sugerido:

> Lee AGENTS.md, MEMORY.md y la skill build-gym-tracker. Inspecciona mi entorno Android y propone el plan del primer hito con versiones compatibles. Distingue requisitos acordados de propuestas técnicas. Comienza por una sesión convencional local con aproximaciones, guardado inmediato, columna Anterior y temporizadores; prueba el aviso en segundo plano antes de extender a biseries/dropsets. No afirmes que funciona en mi teléfono sin probarlo.

## Hitos propuestos

1. Verificar entorno y arquitectura; crear proyecto compilable y navegación básica.
2. Flujo vertical: una rutina editable, sesión convencional, aproximaciones, registro local, anterior y recuperación. Prototipo funcional del temporizador y prueba temprana en dispositivo.
3. Biseries/dropsets, selectores de unidades/esfuerzo y notas.
4. Resumen, calendario, edición histórica, gráficas y racha.
5. Respaldo, pruebas de datos/segundo plano y APK para uso personal.

Todos los requisitos de primera versión deben estar completos al cerrar el hito 5; los hitos no eliminan funciones acordadas.

## Pendientes que no bloquean la documentación

- Modelo de teléfono y versión Android; Antigravity instalado y versión.
- Nombre, package ID, Android mínimo y esquema de firma para actualizaciones.
- Rutina real inicial y catálogo de ejercicios/variantes.
- Convención de peso: por mancuerna/par, barra incluida, máquinas y unilateral.
- Precisar pausas/descansos de aproximaciones y transiciones entre ejercicios.
- Validar criterio de días para la meta semanal y tratamiento de sesiones parciales.
- Decidir mecanismo de temporizador tras comprobar Android objetivo y restricciones.

## Registro de avances

2026-10-06: requisitos y documentos de arranque preparados. 
2026-10-06: entorno verificado (JDK 21, Android SDK existentes). Se generó la estructura de proyecto inicial con versiones recientes estables de AGP (8.7.0), Kotlin (2.0.0) y Compose BOM (2024.09.02).
2026-10-06: Desarrollo del flujo central parcialmente implementado. Pendiente refinar persistencia, escribir pruebas unitarias e integrar lógica de notificaciones en segundo plano antes de la prueba en teléfono.
2026-10-07: Hito v1.10 completado en código y pruebas locales (cardio en rutinas + compartir rutina, migración 7→8, respaldo v2). Revisión GGA pendiente por incompatibilidad del overlay del harness con la free tier de OpenCode (ver sección v1.10).
2026-10-07: Hito v1.11 completado en código y pruebas locales (métricas por ejercicio con rango de fechas, comparación de rutinas con instantánea de composición, migración 8→9, respaldo v3). 46 pruebas, lint 0/10, APK Hercules-v1.11-graficas-rutinas.apk. Ver sección v1.11.
2026-10-07: Hito v1.12 completado en código y pruebas locales (validación completa del respaldo con mensajes claros, export en transacción, rollback atómico, alarma cancelada al importar). 72 pruebas, lint 0/14, APK Hercules-v1.12-respaldo.apk; instrumentadas compiladas, pendiente de dispositivo. Ver sección v1.12.
2026-10-07: Hito v1.13 completado (correcciones A01–A17 vía patch, Room 10, respaldo v4). 107 pruebas, lint 0/18, APK Hercules-v1.13-correcciones.apk. Detalle histórico en la sección v1.13.
2026-10-07: Hito v1.14 completado en código y pruebas locales (archivado no destructivo de ejercicios/rutinas, Room 10→11 con `origin` LEGACY/USER/SEED, respaldo v5, UI "Eliminar" con confirmaciones y bloqueos). 118 pruebas, lint 0/18, check_sql 17/17, migración 10→11 simulada == `11.json`, APK Hercules-v1.14-eliminar.apk; instrumentadas compiladas, pendiente de dispositivo. Ver sección v1.14.
2026-10-07: Hito v1.15 completado en código y pruebas locales (tema claro/oscuro, apariencia persistida con DataStore, navegación Inicio/Progreso/Ajustes, rediseño de Inicio, previews). 132 pruebas, lint 0/18, check_sql 17/17, APK Hercules-v1.15-temas-inicio.apk (versionCode 16). Instrumentadas compiladas, pendiente de dispositivo; mockups no visualizados (límite declarado). Ver sección v1.15.
2026-10-07: Hito v1.16 completado en código y pruebas locales (editor de rutina rediseñado, catálogo de ejercicios, Inicio compacto con plurales, Ajustes en tarjetas, Progreso con contenedores y mapa anual con meses, métricas "No aplica"). 149 pruebas, lint 0/18, check_sql 17/17, APK Hercules-v1.16-editor-catalogo.apk (versionCode 17 / 1.16, firma idéntica). Instrumentadas compiladas, pendiente de dispositivo; sin cambios de Room (11) ni respaldo (v5). Ver sección v1.16.
