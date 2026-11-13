---
name: build-gym-tracker
description: Implementa y revisa el flujo de entrenamiento de una app Android personal y local. Usar al trabajar en rutinas, aproximaciones, series, biseries, dropsets, kg/lb, RPE/RIR, persistencia, recuperación, temporizadores en segundo plano, historial o métricas de progreso de este proyecto.
---

# Desarrollar el registro de entrenamiento

## Preparar la tarea

1. Localizar la raíz real del proyecto. Leer sus AGENTS.md y MEMORY.md; no asumir que la raíz es la carpeta de esta skill.
2. Identificar el comportamiento que se modifica y el criterio observable de aceptación.
3. Revisar implementación, pruebas y cambios existentes. Si no existe proyecto, comprobar herramientas y proponer una arquitectura pequeña antes de crearlo.
4. Leer documentación Android actual para APIs afectadas. Cargar una skill de Compose para UI o testing-setup para infraestructura solo cuando aporte a la tarea.
5. Separar requisitos aprobados de propuestas; registrar las decisiones que cambien datos o métricas.

## Elegir el procedimiento

| Cambio | Trabajo necesario |
| --- | --- |
| Tarjeta o captura de datos | Validar campos, unidades, anterior, teclado, scroll y guardado |
| Rutina o estructura de series | Revisar IDs, plantilla/instantánea, agrupación y transacción |
| Temporizador | Revisar estado persistido, reloj, permisos, mecanismo Android y dispositivo |
| Historial/métricas | Revisar comparabilidad, cálculos, fechas, invalidación y resultados vacíos |
| Importación/migración | Validar esquema, compatibilidad, atomicidad y conservación de datos |

## Modelar el dominio antes de dibujar la UI

Separar definición del ejercicio/variante, plantilla versionada de rutina y sesión ejecutada. Modelar orden y bloques convencionales/biseries; cada serie tiene identidad, finalidad (aproximación/trabajo), estado y etapas. Una etapa guarda carga, unidad, repeticiones y esfuerzo con escala. No confundir finalidad de la serie con técnica de ejecución.

Conservar en la sesión el plan de referencia y cambios realizados. Usar estados explícitos de sesión (activa, pausada, finalizada, abandonada) y serie/bloque (pendiente, completado, omitido). Diferenciar un resultado vacío de una omisión. Evitar finalizar algo solo porque los campos están llenos.

## Implementar el registro

- Validar decimales de carga y repeticiones enteras según el tipo de ejercicio. Manejar coma/punto decimal sin convertir una entrada incompleta en cero.
- Conservar unidad original y cálculo normalizado. Usar `1 lb = 0.45359237 kg`; redondear para mostrar, no repetidamente al persistir.
- Conservar escala y valor de esfuerzo originales. Elegir RPE o RIR para nuevas entradas; una edición de esfuerzo existente es explícita, no una conversión silenciosa.
- Definir rangos y ayudas de cada escala en un solo lugar; permitir datos vacíos. No promediar RPE con RIR.
- Registrar cada cambio válido con una estrategia que no pierda la última edición al cerrar pantalla. Finalizar dentro de una transacción y confirmar persistencia antes de mostrar éxito.
- Evitar guardar o finalizar dos veces ante doble toque. Mantener IDs estables durante scroll y reordenamiento.
- Seleccionar Anterior por ejercicio/variante y convención de carga, diferenciando aproximación/trabajo y estructura de serie. Si no hay correspondencia exacta, mostrar el último contexto claramente identificado, no inventar correspondencia.

## Ejecutar estructuras especiales

Para aproximaciones, conservar resultados y mostrarlos diferenciados; separar su contribución al volumen y al conteo de trabajo.

Para biseries, registrar A y B por ronda. Al completar A, dirigir a B sin iniciar el descanso de ronda. Iniciar descanso al terminar ambos o resolver explícitamente una omisión. No compartir pesos/repeticiones de A con B.

Para dropsets, añadir etapas hijas indentadas, sin crear otra serie principal. Permitir terminar una etapa y continuar inmediatamente. Iniciar descanso al completar la serie completa. Al volver a una serie convencional, cerrar visual y funcionalmente el bloque anterior. Permitir agregar/eliminar etapas pendientes; conservar la consistencia al corregir resultados históricos.

## Implementar los dos temporizadores

1. Separar reloj de sesión y descanso. Inyectar un reloj para probar lógica sin esperar tiempo real.
2. Persistir inicio, pausas acumuladas, estado y deadline/version del descanso. Recalcular tiempo restante al reabrir; no depender del estado de una pantalla.
3. Seleccionar mecanismo Android conforme a API objetivo, frecuencia real de descansos y reposo. Revisar permisos y cuotas: una alarma exacta aislada no demuestra que varias alarmas cortas seguidas sean fiables en Doze.
4. No usar trabajo periódico de WorkManager para un beep de descanso corto. No declarar un servicio en primer plano con tipo arbitrario. Consultar requisitos actuales y comprobar compatibilidad.
5. Programar un aviso identificable; cancelar/reemplazar el anterior al cambiar descanso. Consumir una finalización una sola vez. No emitir varios avisos atrasados al reabrir la app.
6. Si se deniegan permisos, mantener registro y reloj en pantalla, indicar la limitación del aviso y ofrecer acceso a ajustes; no simular que el aviso en segundo plano está disponible.
7. Probar primer plano, segundo plano, bloqueo de pantalla, modo ahorro/reposo, música y audífonos. Diferenciar reinicio y detención forzada; documentar qué se recupera y qué no puede avisar.
8. Al finalizar/abandonar sesión, cancelar avisos activos. Editar una sesión pasada nunca inicia temporizadores.

## Calcular feedback e historial

- Contar series principales de trabajo; contar etapas de dropset por separado. Una ronda de biserie contiene una serie de cada ejercicio, no una serie total.
- Calcular volumen registrado como suma de carga normalizada × repeticiones de etapas completadas y válidas. No sumar también un total padre. Mostrar unidad inequívoca y separar aproximaciones.
- No comparar volumen de máquinas diferentes, variantes diferentes o convenciones incompatibles como si midiera el mismo rendimiento. Identificar ejercicios sin carga externa; no inventar peso corporal ni volumen.
- Comparar misma identidad/versión de rutina y variante de ejercicio; informar modificaciones y datos insuficientes.
- Construir gráficas con datos reales, filtros de escala y unidades consistentes. No rellenar esfuerzo faltante con cero.
- Agrupar por fecha local de entrenamiento. Contar días distintos para la meta semanal, no número de sesiones. Tratar semana abierta sin romper anticipadamente racha y preservar metas históricas.
- Hacer de correcciones históricas una operación transaccional; actualizar resumen, anterior, récords, gráficas, calendario y racha afectados.
- Validar respaldo versionado antes de importar; resolver colisiones y evitar duplicar sesiones. Probar exportación→importación sin pérdida de relaciones.

## Verificar con casos concretos

Aplicar los casos pertinentes al cambio; no crear pruebas que solo repitan la implementación.

| Caso | Resultado esperado |
| --- | --- |
| 100 lb → kg → lb | Mantener precisión del registro y mostrar aproximadamente 100 lb |
| Esfuerzo sin registrar | Conservar null; no aportar cero a promedios |
| Cambiar selector RPE/RIR | Preservar registros previos y etiquetar su escala |
| Remo: 30×10, bajadas 25×8 y 20×6; otra serie 30×10 | Dos series; volumen 920 kg·repeticiones; ningún doble conteo |
| Completar A de una biserie | Solicitar B; no iniciar descanso de ronda |
| Modificar plantilla | Mantener sesión histórica intacta |
| Corregir peso histórico | Recalcular datos derivados afectados |
| Mover rutina del lunes al martes | Mantener su identidad para comparación |
| Doble toque en completar | Un guardado lógico y un descanso |
| Cambiar descanso antes del beep | Invalidar aviso anterior |
| Bloquear pantalla y completar varios descansos sucesivos | Aviso único y oportuno según mecanismo probado |
| Muerte de proceso durante sesión | Recuperar registros confirmados y reconciliar reloj |
| Dos sesiones en una fecha | Un día para cumplimiento semanal |
| Importar archivo inválido | No dejar cambios parciales |

## Cerrar la tarea

Ejecutar compilación, lint y pruebas relevantes disponibles. Documentar resultados y limitaciones; no afirmar prueba física sin dispositivo. Actualizar MEMORY con estado real y siguiente paso. Entregar un incremento revisable; no publicar ni instalar herramientas externas innecesarias como parte implícita de esta skill.

## Referencias oficiales

- Arquitectura: https://developer.android.com/topic/architecture/recommendations
- Room: https://developer.android.com/training/data-storage/room
- Alarmas: https://developer.android.com/develop/background-work/services/alarms
- Servicios en primer plano: https://developer.android.com/develop/background-work/services/fgs
- Notificaciones: https://developer.android.com/develop/ui/views/notifications/notification-permission
- Pruebas: https://developer.android.com/training/testing
- Formato/rutas Antigravity: https://antigravity.google/docs/skills/
