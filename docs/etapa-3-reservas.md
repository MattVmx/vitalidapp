# Etapa 3 — Validaciones de reserva y concurrencia

Implementada el 19 de octubre de 2026 sobre `6ef028f` (etapa 2).

## Comportamiento

- El servidor valida la reserva aunque se modifique o se reenvíe el formulario. Se mantienen identidad, permisos y CSRF de las etapas anteriores.
- Fecha estricta `AAAA-MM-DD`, desde hoy hasta hoy + 90 días inclusive. La hora de inicio debe ser posterior al instante actual; no se admiten horas pasadas ni el turno que acaba de comenzar.
- Los días laborales, entrada, salida y descanso se comprueban al confirmar. Los turnos mantienen la duración implícita de una hora del modelo actual. Entrada y fin de descanso son inclusivos; inicio de descanso y salida quedan excluidos.
- Se usa un reloj con zona `America/Argentina/Buenos_Aires`, independiente de la zona del servidor. Las pruebas fijan el reloj para reproducir los límites.
- Profesional existente, activo, con rol profesional, agenda consistente y precio válido. Paciente autenticado con cobertura válida. El importe final no puede ser negativo; la selección de profesional refleja ese mismo mínimo de cero.
- Un profesional no puede recibir dos reservas para la misma fecha/hora; un paciente tampoco puede tener turnos simultáneos con distintos profesionales.
- Calendario, listado de horarios y confirmación comparten las reglas. Se ocultan los horarios ocupados o pasados y las fechas sin opciones; las pantallas vacías muestran una explicación y deshabilitan la confirmación.
- Una reserva válida redirige a Mis consultas con un aviso de confirmación. Un dato inválido o un conflicto vuelve al inicio del recorrido con una explicación. Parámetros ausentes o de tipo incorrecto conservan la respuesta HTTP 400.

## Protección de concurrencia

La comprobación previa mejora la respuesta al usuario, pero la garantía final reside en dos restricciones únicas de `consulta`:

- `uk_consulta_profesional_turno`: `(profesional_id, fecha, horario)`.
- `uk_consulta_paciente_turno`: `(paciente_id, fecha, horario)`.

El guardado hace `saveAndFlush` dentro de una transacción. Si otra solicitud ocupa el turno, la base rechaza la segunda inserción y se revierte su transacción. El controlador recibe el fallo fuera de esa transacción y muestra un mensaje recuperable. No se reintenta automáticamente ni se modifica el turno que ganó la carrera.

## Verificación

```powershell
cd health-service-app
.\mvnw.cmd -B '-Dspring.profiles.active=demo' test
```

**119 pruebas aprobadas, 0 fallos, 0 errores y 0 omitidas:** 37 de reserva, 59 de CSRF, 21 de permisos y 2 de arranque/página pública.

Los casos de reserva comprueban fechas malformadas, imposibles, pasadas y fuera del límite; días no laborales; descanso y bordes de jornada; turno actual o vencido; profesionales y perfiles inválidos; precio persistido; formularios de éxito/error; desaparición de horas ocupadas y días completos; identidad manipulada y doble envío.

Tres casos usan transacciones reales independientes, sin envolverlos en el rollback habitual de los tests:

1. Dos inserciones concurrentes para un profesional, con distintos pacientes: solo una persiste.
2. Dos inserciones concurrentes para un paciente, con distintos profesionales: solo una persiste.
3. Dos confirmaciones HTTP simultáneas con MockMvc: un éxito, un mensaje de error y una sola consulta guardada.

Los dos primeros casos escriben directamente por repositorio para comprobar la protección de la base incluso si se omite la comprobación del servicio. Al terminar se limpian únicamente los registros ficticios creados por esos casos.

En navegador, usando la demo local y la paciente ficticia Sofia Torres:

- Se escribió manualmente `2026-10-18` en el calendario: el servidor la rechazó con un aviso.
- Se reservó con Laura Fernandez el `2026-10-20` a las 9:00: apareció un solo turno y el aviso de confirmación en Mis consultas.
- Una segunda pestaña, abierta antes de confirmar, intentó guardar el mismo turno: recibió el aviso de horario ocupado.
- Al volver a consultar las opciones, las 9:00 ya no figuraban.

Las capturas y reportes locales están fuera del repositorio, en la carpeta hermana `vitalidapp-etapa-3`: `fecha-rechazada.png`, `turno-confirmado.png`, `duplicado-rechazado.png`, `pruebas.log` e informes Surefire.

## Base MySQL existente

Las pruebas anteriores se ejecutaron con H2 en perfil demo; no se ejecutó una migración ni se verificó concurrencia sobre una instancia MySQL. La demo recrea su esquema con las restricciones. Para una base existente, no se debe confiar únicamente en `ddl-auto=update`: Hibernate puede informar un error al agregar una restricción sobre datos duplicados y continuar el inicio.

Antes de desplegar esta versión sobre datos existentes, realizar una copia de respaldo y auditar los conflictos:

```sql
SELECT profesional_id, fecha, horario, COUNT(*) AS cantidad
FROM consulta GROUP BY profesional_id, fecha, horario HAVING COUNT(*) > 1;

SELECT paciente_id, fecha, horario, COUNT(*) AS cantidad
FROM consulta GROUP BY paciente_id, fecha, horario HAVING COUNT(*) > 1;

SELECT id, fecha, horario FROM consulta
WHERE profesional_id IS NULL OR paciente_id IS NULL
   OR fecha IS NULL OR horario IS NULL OR horario < 0 OR horario > 23;

SHOW INDEX FROM consulta;
```

También revisar que todas las fechas históricas sean fechas reales en formato ISO canónico; no convertirlas ni eliminar registros automáticamente. Los conflictos deben resolverse con una decisión explícita sobre los datos. Una vez corregidos y con las escrituras detenidas, agregar solamente las restricciones que falten:

```sql
ALTER TABLE consulta ADD CONSTRAINT uk_consulta_profesional_turno
  UNIQUE (profesional_id, fecha, horario);
ALTER TABLE consulta ADD CONSTRAINT uk_consulta_paciente_turno
  UNIQUE (paciente_id, fecha, horario);
SHOW INDEX FROM consulta;
```

Verificar `Non_unique = 0` y las tres columnas de cada índice antes de habilitar reservas. Este procedimiento es documentación de despliegue, no un script ejecutado por la aplicación. No se borraron ni alteraron datos MySQL durante esta etapa.

## Pendiente de la etapa 4

La cancelación todavía elimina físicamente la consulta y el diagnóstico anticipado sigue permitido. Estados, conservación del historial y reglas de finalización/calificación pertenecen a la siguiente etapa. Al incorporar cancelación lógica habrá que adaptar las restricciones para liberar el horario sin perder el registro histórico.

Continúa el aviso de H2 al cerrar los contextos; no impidió que Maven terminara con `BUILD SUCCESS`. No se realizó una auditoría visual completa móvil ni un despliegue de producción.
