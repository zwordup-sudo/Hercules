# AGENTS.md — App personal de entrenamiento

## Objetivo y lectura inicial

Construir una aplicación Android de uso personal que permita planificar rutinas, registrar entrenamientos y consultar progreso, completamente sin conexión. El usuario trabaja actualmente con OpenCode/Warp; el entorno no es el framework de la app.

Antes de trabajar, leer `MEMORY.md`. Para implementar o revisar el flujo de entrenamiento, leer `.agents/skills/build-gym-tracker/SKILL.md`. Si el paquete todavía contiene ese archivo como `SKILL.md` en la raíz, leerlo allí y moverlo a la ubicación indicada antes de comenzar el proyecto. No mantener copias divergentes.

Las instrucciones actuales del usuario prevalecen. Este archivo define la forma de trabajar; MEMORY conserva requisitos y decisiones; SKILL define el procedimiento especializado. No presentar propuestas técnicas como decisiones ya aprobadas.

## Forma de colaborar

- Comunicar en español claro. Explicar qué cambió, cómo se comprobó y qué falta.
- Implementar en incrementos pequeños y funcionales. No generar toda la app en un solo paso.
- Resolver decisiones reversibles de implementación sin pedir confirmación repetidamente.
- Consultar cambios que alteren el alcance o el significado de métricas. Documentar supuestos sin bloquear el trabajo independiente.
- Revisar archivos y cambios existentes antes de editar. Preservar el trabajo del usuario.
- Actualizar MEMORY al finalizar cada hito: decisiones reales, estado comprobado, pruebas, pendientes y siguiente paso. No guardar conversaciones completas.
- No marcar algo terminado sin evidencia. Diferenciar prueba automatizada, prueba manual, inspección y pendiente.

## Selección de modelo en OpenCode

Rafa elige el modelo manualmente en `/models` antes de cada tarea. Gentle sigue siendo el agente predeterminado. El catálogo de proveedores y modelos permanece abierto. No fijar modelos globales, auxiliares ni por agente, ni añadir filtros al catálogo para aplicar estas recomendaciones.

- GPT-6.1 Sol: implementación y revisión de cambios que cruzan varias capas, como la sesión activa en Compose, su estado y el temporizador.
- GPT-6 Luna: tareas pequeñas y bien delimitadas, como textos, ajustes visuales o una prueba puntual.
- GPT-6 Astra: segunda revisión de problemas difíciles o cambios delicados de persistencia, si está disponible.

Conservar Engram, los permisos y la delegación actuales. Estas recomendaciones no requieren modificar la configuración global, la base de memoria ni el código Android.

## Propuesta técnica inicial

Propuesta, todavía no ratificada ni implementada: Kotlin, Jetpack Compose con Material 3, ViewModel/StateFlow y Room para datos estructurados; DataStore para preferencias. Justificar cualquier cambio antes de introducir otro framework.

Mantener una arquitectura pequeña: UI por funcionalidad, lógica de dominio para registro/métricas/temporizadores y repositorios locales. Una aplicación y los módulos estrictamente necesarios. Evitar capas vacías, microservicios y abstracciones genéricas sin uso real.

- Seleccionar versiones estables compatibles de Kotlin, AGP, Gradle, Compose y Room mediante documentación oficial. Registrar versiones elegidas. No copiar versiones antiguas de una skill.
- Usar Gradle Wrapper del proyecto; en Windows, `gradlew.bat`.
- Mantener estado observable y flujo unidireccional. La UI no escribe directamente en la base de datos.
- Mantener funciones de cálculo independientes de Android cuando sea posible.
- Guardar de forma local; no añadir login, backend, Firebase, sincronización, analítica remota ni IA alojada.
- No exigir permiso de internet para funcionalidades del producto. Descargar dependencias durante desarrollo no equivale a necesitar red en ejecución.

## Reglas de producto que no se deben perder

- Rutina, sesión y registro histórico son entidades distintas. Editar una plantilla no modifica sesiones anteriores.
- Mostrar aproximaciones por separado; admitir series convencionales, biseries y dropsets.
- Un dropset es una serie principal con etapas hijas, no varias series independientes. Una biserie agrupa dos ejercicios y sus rondas.
- Guardar peso, unidad, repeticiones y esfuerzo opcional con su escala. Vacío no equivale a cero.
- kg/lb se convierten; RPE/RIR no se reinterpretan al cambiar el selector.
- Mostrar datos anteriores reales, de la misma variante de ejercicio y contexto comparable. Si no existen, mostrar vacío.
- Guardar cada edición válida y cada finalización inmediatamente. No esperar al cierre de la sesión para persistir.
- Corregir el historial recalcula métricas sin alterar otras sesiones.
- Comparar ejercicios equivalentes y versiones de rutinas; el lunes no es una identidad de rutina.

## Temporizadores

Separar duración total de sesión y descanso. Guardar inicio, pausas y vencimiento; calcular el tiempo a partir de marcas temporales, no de contar ticks en la pantalla. Considerar reloj monotónico mientras el dispositivo permanece encendido y reconciliación tras reinicio.

El descanso debe avisar con la app en segundo plano y pantalla bloqueada. Evaluar mecanismos compatibles con la versión Android y las restricciones de batería; consultar documentación de AlarmManager y servicios antes de seleccionar una estrategia. No suponer que una coroutine de UI, WorkManager periódico o una alarma inexacta resuelven descansos cortos y repetidos.

Si se requiere servicio en primer plano, justificar su uso y tipo permitido; no inventar un tipo. Si se requieren permisos de notificación o alarmas, explicar su propósito en la app y manejar su denegación. No declarar precisión garantizada sin probarla. Probar cuotas de alarmas durante reposo, no solamente un aviso aislado.

Cada descanso tiene identidad y versión: modificarlo o cancelarlo invalida avisos anteriores. Evitar beeps duplicados. Finalizar una sesión cancela sus avisos. Diferenciar segundo plano, eliminación de recientes, muerte de proceso, reinicio y cierre forzado: no prometer ejecución tras cierre forzado.

## Interfaz

Español; rediseño pendiente con tema claro blanco/celeste/azul y oscuro gris/morado, según referencias aprobadas, dos pestañas: Inicio y Progreso. Selector semanal arriba; tarjetas de ejercicios en scroll vertical; columna Anterior y registro por renglón. Dropsets indentados; biseries agrupadas; notas desplegables.

Priorizar uso rápido entre series: teclado adecuado, objetivos separados de resultados reales, controles táctiles amplios, scroll estable y etiquetas accesibles. No depender solo del color para aproximaciones, completado o errores. Respetar teclado, barras del sistema y tamaños de texto.

Referencias visuales: capturas aportadas por el usuario. Inspirarse en su organización sin reproducir avisos de pago, funciones de coach o activos gráficos de terceros. No inventar que existe una captura del entrenamiento activo: la referencia disponible muestra una sesión histórica.

## Persistencia y datos

Identificadores estables para rutinas/versiones, ejercicios/variantes, sesiones, bloques, rondas, series y etapas. Cambios relacionados dentro de transacciones. Migraciones comprobadas; no usar borrado destructivo como solución en datos reales.

Conservar valor/unidad originales y una representación normalizada para cálculos. Definir convención de mancuernas, barras, máquinas y ejercicios unilaterales; comparar solo convenciones compatibles. Exportar/importar respaldo con versión de esquema, validación, manejo de IDs duplicados y transacción. No importar parcialmente archivos inválidos.

## Verificación y entrega

Comprobar el entorno antes de crear el proyecto: JDK, SDK Android, herramientas de compilación, emulador/dispositivo y Gradle. No instalar o actualizar componentes grandes sin necesidad. Detectar herramientas disponibles antes de ejecutar comandos sugeridos por skills.

Una vez exista el proyecto, ejecutar tareas disponibles equivalentes a:

```text
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

La segunda línea requiere un dispositivo/emulador. Si no está disponible, indicarlo y conservar la comprobación como pendiente. Adaptar nombres reales de módulos y tareas; no inventar resultados.

Pruebas prioritarias: conversiones sin deriva, etapas/series sin doble conteo, avance de biserie, recuperación tras muerte de proceso, edición histórica, agrupación por rutina, fechas/rachas, permisos denegados, aviso único y oportuno en segundo plano, y respaldo de ida y vuelta.

Entregar al finalizar un hito: archivos modificados, comportamiento logrado, comandos/resultados, limitaciones y próximo paso. Generar APK cuando corresponda; comprobar instalación real antes de afirmar que funciona en el teléfono. Nunca cambiar la firma entre actualizaciones sin plan para los datos.

## Skills externas

Consultar la selección y fuentes en MEMORY. Instalar solo las elegidas para la tarea, con sus referencias necesarias y licencia. Revisar compatibilidad y fijar una revisión reproducible. No instalar todo un catálogo por defecto. Estas instrucciones no autorizan publicar la app, conectar cuentas ni enviar mensajes.
