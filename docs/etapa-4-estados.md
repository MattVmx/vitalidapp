# Etapa 4 — Estados de consulta y conservación del historial

Implementada el 20 de octubre de 2026 sobre `6e8e243` (etapa 3).

## Comportamiento

| Acción | Condiciones | Resultado |
| --- | --- | --- |
| Reservar | Reglas de agenda, identidad y disponibilidad de etapa 3 | `RESERVADA` |
| Cancelar | Paciente propietario, reserva antes del inicio | `CANCELADA`, horario liberado e historial conservado |
| Finalizar con diagnóstico | Profesional propietario, reserva cuya hora de atención terminó, texto no vacío de hasta 1500 caracteres | `FINALIZADA`, diagnóstico guardado y cierre registrado |
| Calificar | Paciente propietario, consulta finalizada, hora de atención terminada, sin nota previa, valor entre 1 y 5 | Nota y promedio guardados en una transacción |

El paso del tiempo no finaliza una consulta: una reserva vencida queda pendiente del cierre por el profesional. Se mantiene la duración de una hora y el reloj de Buenos Aires. La cancelación deja de estar disponible exactamente al comenzar el turno; el diagnóstico se habilita exactamente al terminar la hora.

No se puede diagnosticar una consulta cancelada o futura, sobrescribir un diagnóstico finalizado, cancelar una finalizada ni calificar antes del cierre. El diagnóstico se guarda sin espacios exteriores. Repetir una cancelación ya realizada no modifica su fecha ni su versión. Los cambios inválidos devuelven HTTP 409 con una explicación; los permisos y CSRF de etapas anteriores siguen vigentes.

La ruta POST histórica `/consulta/eliminar/{id}` se conserva por compatibilidad, pero ya no elimina filas. Tanto paciente como profesional ven el estado persistido; los formularios se muestran según las mismas reglas temporales del servidor. Los mensajes de éxito usan flash; el diagnóstico no se coloca en parámetros de URL ni mensajes de error.

## Persistencia y concurrencia

`Consulta` incorpora `estado`, `version` para bloqueo optimista y las marcas `canceladaEn`/`finalizadaEn` (hora local de Buenos Aires). Se conservan identidad, fecha, hora, precio y relaciones originales al cancelar.

La columna interna `turno_activo` vale `true` para reservas/finalizadas y `NULL` para canceladas, sincronizada por callbacks JPA. No se usa `false`: se necesitan múltiples registros cancelados del mismo horario. Las consultas de disponibilidad consideran solamente la ocupación activa.

Las restricciones de la etapa 3 se reemplazan por:

- `uk_consulta_profesional_activa`: `(profesional_id, fecha, horario, turno_activo)`.
- `uk_consulta_paciente_activa`: `(paciente_id, fecha, horario, turno_activo)`.

La base sigue decidiendo los conflictos entre reservas concurrentes. `@Version` y `saveAndFlush` impiden que dos modificaciones sobre una consulta se sobrescriban silenciosamente. El controlador captura el conflicto fuera de la transacción y muestra HTTP 409; no reintenta automáticamente.

Para calificar, se bloquea el profesional con `PESSIMISTIC_WRITE`, se guarda la nota y se recalcula su promedio de consultas finalizadas en la misma transacción. Esta operación usa `READ_COMMITTED` para que el promedio lea las notas confirmadas mientras esperaba el bloqueo. Es una decisión basada en los [niveles de aislamiento de InnoDB](https://dev.mysql.com/doc/refman/8.4/en/innodb-transaction-isolation-levels.html); todavía no se verificó contra una instancia MySQL.

## Verificación

```powershell
cd health-service-app
.\mvnw.cmd -B '-Dspring.profiles.active=demo' test
```

**140 pruebas aprobadas, 0 fallos, 0 errores y 0 omitidas:** 21 de estados, 37 de reservas, 59 de CSRF, 21 de permisos y 2 de arranque/página pública. Las fixtures de permisos y CSRF se ajustaron a los nuevos estados válidos sin retirar comprobaciones de seguridad.

Los nuevos casos cubren cancelación conservando datos y liberando el horario, tres ciclos de reserva/cancelación sobre el mismo turno, cancelación repetida, límites exactos de inicio/fin, diagnóstico vacío/excesivo/anticipado, cierre, rechazo de sobrescritura, nota única y rango válido, promedio y formularios visibles según el estado.

Tres casos lanzan solicitudes HTTP concurrentes con transacciones independientes:

1. Dos diagnósticos sobre la misma consulta: un éxito y un conflicto; queda un solo diagnóstico.
2. Dos notas sobre consultas distintas del mismo profesional: ambos guardados y promedio correcto.
3. Dos notas sobre la misma consulta: un éxito y un conflicto; queda una sola nota.

Se mantienen además las pruebas de reservas concurrentes de la etapa 3. Los datos son ficticios y los casos concurrentes limpian exclusivamente sus propias fixtures.

En el navegador de la demo local se reservó con Laura Fernandez el 21/10/2026 a las 9:00, se canceló y se volvió a reservar ese mismo horario. La paciente Sofia Torres ve la consulta cancelada y la nueva reserva. El profesional ve ambos registros; no hay formulario de diagnóstico en la cancelada ni en la futura. El cierre y la calificación se comprobaron mediante integración HTTP y HTML renderizado con reloj fijo, sin alterar el reloj del sistema para la prueba visual.

Evidencia local fuera del repositorio: carpeta hermana `vitalidapp-etapa-4`, con `pruebas.log`, reportes Surefire y capturas `cancelada-con-historial.png`, `nueva-reserva-con-historial.png` e `historial-profesional.png`.

## Base MySQL existente

No se ejecutó una migración ni se verificó esta etapa con una instancia MySQL. H2 crea un esquema nuevo para demo/pruebas. El siguiente procedimiento requiere una copia restaurada de la base real y verificación antes de su despliegue. No alcanza con `ddl-auto=update`: no resuelve estados históricos ni elimina automáticamente los índices anteriores.

1. Respaldar y detener las escrituras. Inspeccionar `SHOW CREATE TABLE consulta` y `SHOW INDEX FROM consulta`. El SQL de abajo supone el esquema exacto de etapa 3, sin las nuevas columnas; no es un script idempotente. Si hay una actualización parcial, adaptar el procedimiento a ese esquema.
2. Ejecutar la auditoría de duplicados, relaciones y fechas de etapa 3. Revisar también diagnósticos en turnos todavía no terminados, calificaciones sin diagnóstico y notas fuera de 1–5. Resolver cada inconsistencia explícitamente, sin borrar ni convertir registros automáticamente. No mostrar texto clínico en logs de auditoría.
3. Agregar las columnas y completar estados **solo después de resolver la auditoría**. Un diagnóstico histórico válido se considera una consulta finalizada; una consulta sin diagnóstico conserva `RESERVADA`, aunque sea pasada. No se inventan cancelaciones: las consultas eliminadas por versiones anteriores no pueden reconstruirse a partir de esta tabla.

```sql
ALTER TABLE consulta
  ADD COLUMN estado VARCHAR(255) NULL,
  ADD COLUMN turno_activo BIT NULL,
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN cancelada_en DATETIME(6) NULL,
  ADD COLUMN finalizada_en DATETIME(6) NULL;

UPDATE consulta SET estado = CASE
  WHEN diagnostico IS NOT NULL AND CHAR_LENGTH(TRIM(diagnostico)) > 0
    THEN 'FINALIZADA'
  ELSE 'RESERVADA' END,
  turno_activo = b'1';

ALTER TABLE consulta MODIFY COLUMN estado VARCHAR(255) NOT NULL;
```

Las fechas de cierre históricas desconocidas quedan `NULL`: no se usa la fecha del turno como si fuera evidencia de cuándo se cargó el diagnóstico. Revisar el promedio almacenado de cada profesional contra sus calificaciones migradas y corregir discrepancias antes de habilitar el servicio.

4. Instalar y verificar los nuevos índices **antes** de quitar los anteriores. Comprobar `Non_unique = 0` y las cuatro columnas, en el orden mostrado.

```sql
ALTER TABLE consulta ADD CONSTRAINT uk_consulta_profesional_activa
  UNIQUE (profesional_id, fecha, horario, turno_activo);
ALTER TABLE consulta ADD CONSTRAINT uk_consulta_paciente_activa
  UNIQUE (paciente_id, fecha, horario, turno_activo);
SHOW INDEX FROM consulta;
```

5. Una vez comprobados ambos índices nuevos, retirar únicamente las restricciones de tres columnas de etapa 3. Si siguen presentes, una consulta cancelada continuará bloqueando la nueva reserva.

```sql
ALTER TABLE consulta DROP INDEX uk_consulta_profesional_turno;
ALTER TABLE consulta DROP INDEX uk_consulta_paciente_turno;
SHOW INDEX FROM consulta;
```

6. Arrancar sobre la copia restaurada y probar reserva/cancelación/repetición, finalización, calificación y solicitudes concurrentes con datos ficticios. Verificar conservación del historial, `NULL` en ocupación de canceladas y promedio correcto. Habilitar la base real únicamente después de validar esquema y comportamiento en la versión MySQL utilizada.

La etapa queda limitada a estados, historial y transiciones. No agrega reprogramación, notificaciones, edición posterior del diagnóstico ni un despliegue de producción.
