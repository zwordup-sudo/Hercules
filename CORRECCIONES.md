# Hercules v1.13 — candidato con correcciones de auditoría

El código incluye las correcciones de A01–A17. **Todavía requiere compilación, tests JVM y validación Android en el PC original.** Este paquete no contiene un APK validado. Conserva el applicationId y no cambia la configuración de firma. Frontend temático y mascota siguen fuera de este incremento.

## Integración con Gentle

1. Comparar esta carpeta con el proyecto actual y aplicar los cambios conservando cualquier trabajo local posterior a v1.12. `cambios-v1.12-v1.13.patch` permite revisar la diferencia; no aplicarlo a ciegas sobre cambios locales.
2. Mantener el `local.properties` del PC, su keystore original y el harness. No sustituir la firma ni desinstalar Hercules. Exportar un respaldo desde la versión instalada antes de actualizar.
3. Ejecutar `tools/Validate-Hercules.ps1` con PowerShell desde el proyecto. Usa JDK de Android Studio solo en ese proceso; no altera configuración global. Corregir cualquier fallo Kotlin/KSP/Lint/test hasta obtener una ejecución satisfactoria. No inventar resultados ni limitarse a contar tareas Gradle como pruebas.
4. Si hay dispositivo autorizado, ejecutar `tools/Validate-Hercules.ps1 -Device`. Sin dispositivo, dejar las pruebas instrumentadas pendientes; no crear esperas infinitas ni instalar otro emulador.
5. Conservar los esquemas Room generados en `app/schemas`. Registrar resultados reales en MEMORY. GGA continúa pendiente por decisión previa, sin cambiar permisos del harness.
6. Entregar el APK solo después de las validaciones locales, indicando qué pruebas del teléfono faltan. Mantener la firma original para actualizar encima de la app existente.

## Cambios por hallazgo

| ID | Corrección incorporada al código |
|---|---|
| A01 | Ediciones serializadas sobre la última fila persistida; no sobrescribir campos con copias antiguas de Compose. |
| A02 | Finalizar cualquier bajada evalúa toda la ronda de biserie, incluidos padres y etapas. |
| A03 | Índices de ronda de trabajo independientes de las aproximaciones. |
| A04 | Inicio de sesión y guardado completo de plantilla en transacciones. |
| A05 | Anterior de una única sesión finalizada previa, solo registros completados, sin consultar la propia sesión; correspondencia por aparición/ronda/etapa, sin desplazar huecos. |
| A06 | Orden y aparición congelados por sesión; un ejercicio repetido mantiene tarjetas independientes. |
| A07 | Catálogos observables y barrera común que invalida órdenes anteriores a una restauración. |
| A08 | Progreso usa un scroll vertical único para todos sus módulos. |
| A09 | Validación de entradas y mensajes de error; no persistir negativos o números no finitos. |
| A10 | Respaldo valida parentesco, ciclos, sesiones múltiples activas y apariciones incompatibles. |
| A11 | Duración histórica tomada del cierre; recuperación de la pantalla y sesión tras recreación. |
| A12 | El vencimiento visual no cancela la alarma; identidad persistida, reloj monotónico y receptor asincrónico acotado. |
| A13 | Cambiar RPE/RIR limpia el valor para pedir un registro nuevo, sin reinterpretarlo. |
| A14 | Aproximaciones identificadas, repeticiones objetivo separadas de las realizadas y notas en fuerza/cardio. |
| A15 | Edición de rutinas existentes; configuración de sesión congelada al iniciarla. |
| A16 | Métricas ausentes siguen siendo null; gráficas separadas por composición comparable, sin división por cero. |
| A17 | Semanas con cierre exclusivo, fechas deterministas, metas históricas y actividad consistente entre racha/calendario/mapa. |

## Reglas explícitas

- Aproximaciones descansan individualmente. Biseries con distinto número de series continúan individualmente en las rondas sobrantes. El descanso compartido toma el mayor descanso configurado del par.
- Agregar las bajadas antes de completar el padre; después de iniciar el descanso no se amplía el bloque.
- Las series comienzan sin repeticiones reales: el objetivo visible no se considera realizado automáticamente.
- Campos antiguos fuera de rango pueden corregirse de forma independiente. No se reinterpreta esfuerzo histórico al cambiar de escala.
- En datos anteriores que no distinguían apariciones repetidas, no se puede reconstruir una distinción que nunca se guardó. La migración conserva datos y evita inventar referencias ambiguas.
- La migración 9→10 añade metadatos anulables y conserva cargas/repeticiones. Para sesiones anteriores copia la configuración que todavía exista al actualizar; cambios antiguos de plantilla no pueden deshacerse retrospectivamente.
- Respaldo nuevo v4; importación compatible con v1–v4. La versión anterior de la app no conoce v4: no hacer downgrade después de actualizar.

## Prueba final en teléfono

Actualizar encima de v1.12 con datos. Registrar campos rápidamente; cerrar y reabrir; verificar aproximaciones y biserie con dropset; finalizar y consultar Anterior; editar plantilla sin alterar una sesión ya iniciada; comprobar beep, salto y cancelación; probar exportación/importación y rechazo de archivo corrupto; recorrer Progreso completo y probar escala de texto ampliada.

## Límites

El beep previo estaba confirmado por el usuario. Se conserva su tono, pero el mecanismo de recuperación cambiado requiere nueva prueba real. Alarma inexacta puede retrasarse sin permiso exacto; no se promete entrega después de cierre forzado/reinicio. Una muerte de proceso entre el marcador del bloque y la programación del sistema sigue siendo una ventana estrecha pendiente de prueba y reconciliación adicional si se reproduce. No se certifica resistencia total a todos los fallos de proceso.
