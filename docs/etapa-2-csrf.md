# Etapa 2 — Operaciones POST y protección CSRF

Fecha: 18 de octubre de 2026. Base: `4e6fbce` (etapa 1).

## Cambios

- Se habilitó la protección CSRF predeterminada de Spring Security, sin excluir rutas.
- Diez operaciones que modificaban datos mediante GET ahora requieren POST: reserva, cancelación y diagnóstico; alta, baja y cambio de rol; creación, eliminación y edición de nombre/importe de obras sociales.
- Se adaptaron los formularios actuales y las plantillas antiguas de consulta e historia clínica. Todos usan `th:action`, que incorpora el token oculto mediante la integración Spring/Thymeleaf.
- El cierre de sesión usa un formulario POST con token. Visitar `/logout` por GET no cierra la sesión.
- Login, registro, calificación y perfiles conservan POST y quedan protegidos, incluidos los formularios multipart.
- El diagnóstico viaja en el cuerpo del formulario. Se eliminó su impresión en consola y se redirige al historial únicamente con el identificador del paciente. La cancelación también redirige después de guardar.
- Se conservaron los controles de pertenencia de la etapa 1 y el ancho de los botones administrativos en la regla de estilos móvil.

## Verificación automática

Comando desde `health-service-app`:

```powershell
.\mvnw.cmd -B '-Dspring.profiles.active=demo' test
```

Resultado local: **82 pruebas, 0 fallos, 0 errores y 0 omitidas**.

| Suite | Casos | Verificación |
| --- | ---: | --- |
| `CsrfFlujosTests` | 59 | 16 operaciones sin token y con token inválido; 11 GET rechazados con 405; 14 POST válidos y sus efectos persistidos; login/logout con token renderizado; tokens de los formularios |
| `ConsultaPermisosTests` | 21 | Permisos y pertenencia de consultas, ahora con token válido para que CSRF no oculte un fallo de autorización |
| `HealthServiceAppApplicationTests` | 2 | Inicio del contexto y página pública |

Las solicitudes rechazadas devuelven 403 y conservan el estado comprobado de los datos. Los casos de diagnóstico verifican el texto persistido, la redirección sin diagnóstico y la ausencia del texto ficticio en la salida capturada. Los casos de perfiles usan solicitudes multipart. Las pruebas usan datos ficticios y transacciones con rollback.

## Verificación en navegador

Demo local con perfil `demo`, escuchando en `127.0.0.1:8181`:

1. Inicio de sesión como paciente y reserva completa para Laura Fernandez, 19/10/2026 a las 9:00. El turno apareció en Mis consultas.
2. Envío del perfil de paciente sin cambiar sus valores: redirección correcta al inicio, sin rechazo CSRF.
3. Cierre de sesión mediante el nuevo botón y acceso como profesional.
4. Guardado de una observación explícitamente ficticia: apareció en el historial y la URL quedó en `/consulta/consultas?idPaciente=...`, sin su contenido. La búsqueda del marcador en `demo.log` no encontró coincidencias.
5. Acceso con la cuenta administrativa documentada, creación de la cobertura `PRUEBA ETAPA 2` y modificación del importe de 100 a 150. El listado reflejó ambos cambios.

Las capturas `diagnostico-guardado.png` y `cobertura-editada.png`, el registro de pruebas y los informes Surefire se conservaron fuera del repositorio, en la carpeta hermana `vitalidapp-etapa-2`. La cancelación, las bajas/altas y el cambio de rol se verificaron con pruebas de integración; no se repitieron manualmente en este recorrido. No se realizó una auditoría visual completa de tamaños móviles.

## Límites y siguientes etapas

- No se modificaron las validaciones de fecha, horario ni duplicación de reservas: corresponden a la etapa 3.
- El diagnóstico de un turno futuro continúa permitido y la cancelación continúa eliminando la consulta; los estados y la conservación del historial quedan pendientes de la etapa correspondiente.
- Continúa el aviso de H2 `Database is already closed [90121-214]` durante el cierre del contexto de pruebas. Maven terminó correctamente; es el aviso ya observado en la etapa 0.
- Estas comprobaciones corresponden al perfil demo con H2. No equivalen a una prueba sobre MySQL ni a un despliegue de producción.
