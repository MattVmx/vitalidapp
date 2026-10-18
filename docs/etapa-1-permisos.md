# Etapa 1 — Permisos por consulta

Implementada y verificada localmente el 17 de octubre de 2026, sobre el commit base `30ce1c6cda6b66c10f48f94630d9f8f1ee13ba0f`.

## Regla de acceso

- El paciente puede listar, buscar, cancelar y calificar únicamente sus consultas.
- El profesional puede listar sus pacientes y ver únicamente sus propias consultas con cada paciente. Compartir un paciente con otro profesional no habilita leer sus diagnósticos.
- Solo el profesional asignado puede guardar el diagnóstico de la consulta. Si `idPaciente` no corresponde a esa consulta, la operación se rechaza antes de guardar.
- La identidad se obtiene de Spring Security y se verifica contra la cuenta actual de la base de datos: debe estar activa y conservar el rol requerido. Los identificadores de formularios y el perfil guardado en sesión no conceden permisos.
- Un recurso ajeno o inexistente devuelve la misma respuesta 403, sin revelar su existencia. Sin sesión, las rutas protegidas mantienen la redirección al ingreso.
- El administrador conserva su área de gestión, sin obtener acceso clínico por este cambio.

## Cambios

`ConsultaService` valida identidad y titularidad antes de leer o modificar consultas. `ConsultaRepository` incorpora consultas filtradas por paciente/profesional. `ConsultaControlador` entrega el identificador del paciente al control del diagnóstico y declara explícitamente el rol requerido para calificar.

No se cambiaron el esquema, los datos iniciales ni las credenciales de demostración.

## Verificación

Primero se ejecutaron dos pruebas contra el código original: ambas fallaron, reproduciendo la lectura y modificación de diagnósticos de otro profesional. Después de corregir el código, la suite completa pasó:

- 21 casos nuevos de integración con MockMvc, Spring Security y H2.
- 2 pruebas existentes de arranque y página inicial.
- Total: 23 pruebas, 0 fallos, 0 errores, 0 omitidas.
- `git diff --check`: sin problemas de espacios.

Los casos nuevos usan dos pacientes, dos profesionales y tres consultas, incluyendo un paciente compartido. Comprueban accesos autorizados y rechazados, filtrado del HTML y modelo, persistencia después de rechazos, ausencia de cambios en la calificación/promedio ajenos, identificadores inconsistentes o inexistentes, cuenta desactivada, perfil de sesión distinto del usuario autenticado, roles incorrectos y ausencia de sesión. Los datos de cada prueba se revierten al finalizar; no se añaden a la demo.

En el navegador se verificó el ingreso del profesional, su listado vacío tras reiniciar la demo, la pantalla 403 para un identificador de la sesión anterior y la vuelta al listado. La comprobación de accesos cruzados con datos existentes corresponde a las pruebas de integración, no a esa captura.

Ejecutar desde `health-service-app` en PowerShell:

```powershell
.\mvnw.cmd -B '-Dspring.profiles.active=demo' test
```

## Prueba manual básica

1. Abrir http://127.0.0.1:8181. La demo local quedó ejecutándose con datos nuevos después del reinicio.
2. Ingresar como `paciente@vitalidapp.com`, contraseña pública de demo `123456`, y reservar con Laura Fernandez.
3. Consultar el turno en Mis consultas.
4. Cerrar sesión e ingresar como `profesional@vitalidapp.com`, misma contraseña de demo. El paciente debe aparecer y su consulta debe poder abrirse.
5. Para repetir de forma aislada la matriz de dos pacientes y dos profesionales, ejecutar la suite anterior. No requiere crear usuarios manualmente.

## Límites de esta etapa

Siguen pendientes para las etapas acordadas: operaciones GET que modifican datos, CSRF, diagnóstico en URL/consola (etapa 2), validaciones de reserva y concurrencia (etapa 3), diagnósticos anticipados y borrado físico al cancelar (etapa 4). El aviso de cierre de H2 detectado en la etapa 0 también continúa: no produjo fallos en los tests ni impidió iniciar la demo.

Esta etapa no certifica toda la seguridad de la aplicación ni prueba MySQL o un despliegue remoto. La etapa 2 no se inició.
