# business-api

Este repositorio es la pieza **business-api** del producto Agente Conversacional con IA para cobranza de Finanty: único escritor en base de datos, reglas de negocio y registro de gestiones. El agente de voz que consume esta API vive en [github.com/ITTeamDeveloper/finanty--agente-voice](https://github.com/ITTeamDeveloper/finanty--agente-voice).

## Stack

Java 21 + Spring Boot 4 (Web MVC, Data JPA, Validation). BD propia en **SQL Server** (perfil `sqlserver`) en desarrollo y producción; **H2** en memoria solo para las pruebas (`./mvnw test`).

## Contrato

El contrato OpenAPI 3.1 está en [`specs/openapi.yaml`](specs/openapi.yaml) y se escribe antes que el código. Describe todos los endpoints, sus esquemas, ejemplos y la cabecera `X-API-Key` como `securityScheme`. La prueba `ContratoOpenApiTest` verifica que cada operación expuesta por la API esté en el contrato (y viceversa) y que los catálogos coincidan con los enums del código.

## Compilar y ejecutar

Requiere JDK 21 o superior. Maven viene incluido con el wrapper (`mvnw` / `mvnw.cmd`), así que no hace falta tenerlo en el PATH.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=sqlserver      # desarrollo: BD de desarrollo (ver sección siguiente)
# o: ./mvnw package && java -jar target/business-api-0.1.0.jar --spring.profiles.active=sqlserver
# pruebas (siempre H2): ./mvnw test
```

Si `JAVA_HOME` apunta a otra versión, exporta `JAVA_HOME` hacia un JDK 21 antes de ejecutar el wrapper.

Queda en `http://localhost:8085` (`SERVER_PORT`). Sin perfil activo la API arranca con H2 en memoria y datos sintéticos; ese modo es solo un respaldo para trabajar sin red, no el camino de desarrollo.

## Desarrollo contra la BD de desarrollo

En desarrollo la API usa la **BD propia de desarrollo en SQL Server**, nunca H2 y nunca CNTSEC. El perfil `sqlserver` (`application-sqlserver.yml`) lee la conexión de variables de entorno y deja el esquema en manos de los scripts (`ddl-auto: none`, sin datos sintéticos). `GuardaEsquema` sigue abortando el arranque si alguien configura `create-drop` o `SQL_INIT_MODE=always` fuera de H2.

1. Crear el esquema una sola vez en la BD de desarrollo, en este orden:
   1. [`specs/sql/esquema_propio.sql`](specs/sql/esquema_propio.sql): las siete tablas propias (`cliente`, `deuda`, `promesa_pago`, `llamada`, `evento_llamada`, `resultado_llamada`, `rellamada`) con PK, FK e índices. Idempotente.
   2. (Obsoleto, opcional) `specs/sql/config_agente.sql`: solo si aún se usan los endpoints `/api/v1/configuracion-agente`; ese dominio se retirará porque agent-voice tendrá su propio PostgreSQL.
2. Definir las variables en el entorno (sin valores en el repositorio):

   ```bash
   export DB_URL='jdbc:sqlserver://<servidor>:1433;databaseName=<bd_propia_dev>;encrypt=true;trustServerCertificate=true'
   export DB_USER='<usuario>'
   export DB_PASSWORD='<contraseña>'
   export CNTSEC_USER='<usuario_cntsec>'
   export CNTSEC_PASSWORD='<contraseña_cntsec>'   # CNTSEC_HOST ya apunta al servidor de desarrollo
   export COBRANZA_API_KEY='<clave_compartida_con_el_agente>'
   ```

3. Arrancar con el perfil:

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=sqlserver
   # o con el jar: java -jar target/business-api-0.1.0.jar --spring.profiles.active=sqlserver
   ```

El script se comprueba en cada `./mvnw test`: `EsquemaPropioSqlServerTest` lo ejecuta en H2 en modo `MSSQLServer` y hace que Hibernate, con el dialecto de SQL Server, valide el esquema contra las entidades (`ddl-auto: validate`) y compare columna a columna. Con `docker compose` del workspace, el servicio `business-api` recibe estas mismas variables y `SPRING_PROFILES_ACTIVE=sqlserver`.

## Autenticación

Todas las rutas bajo `/api/` exigen la cabecera `X-API-Key`. La clave se configura con la variable de entorno `COBRANZA_API_KEY` (propiedad `cobranza.api-key`); si no coincide, la API responde `401`.

Prueba rápida:

```bash
curl -H "X-API-Key: $COBRANZA_API_KEY" http://localhost:8080/api/v1/clientes/45879632
```

## Variables de entorno

Toda clave, URL o parámetro se lee de variables de entorno con placeholders en `src/main/resources/application.yml`. Los valores por defecto sirven solo para desarrollo; nunca hay credenciales en el repositorio.

| Variable | Propiedad | Por defecto (desarrollo) | Uso |
|---|---|---|---|
| `COBRANZA_API_KEY` | `cobranza.api-key` | `dev-key-cambiar` | Clave compartida con el agente (`X-API-Key`) |
| `SERVER_PORT` | `server.port` | `8085` | Puerto HTTP |
| `DB_URL` | `spring.datasource.url` | H2 en memoria (solo respaldo sin perfil) | BD **propia** de la API en SQL Server con el perfil `sqlserver` (clientes, deudas, promesas, llamadas, eventos, resultados, rellamadas). Nunca CNTSEC. Las pruebas la ignoran: usan siempre H2 (perfil `test`) |
| `DB_USER` | `spring.datasource.username` | `sa` | Usuario de la BD propia (obligatorio con `sqlserver`) |
| `DB_PASSWORD` | `spring.datasource.password` | vacío | Contraseña de la BD propia (obligatoria con `sqlserver`) |
| `JPA_DDL_AUTO` / `SQL_INIT_MODE` | `spring.jpa.hibernate.ddl-auto` / `spring.sql.init.mode` | `create-drop` / `always` (sin perfil); `none` / `never` con `sqlserver` | Solo válidos con H2; `GuardaEsquema` aborta el arranque si se usan contra otra BD. El esquema de SQL Server lo crea `specs/sql/esquema_propio.sql` |
| `CNTSEC_HOST` | `cntsec.datasource.host` | `FNTSRVD4` (desarrollo) | Servidor SQL Server de CNTSEC. El perfil `prod` (`application-prod.yml`) cambia solo este valor |
| `CNTSEC_URL` | `cntsec.datasource.url` | `jdbc:sqlserver://${CNTSEC_HOST}:1433;databaseName=CNTSEC;...` | URL completa; solo hace falta para una BD distinta, por ejemplo sin red: `jdbc:h2:mem:cntsec;MODE=MSSQLServer;DB_CLOSE_DELAY=-1` |
| `CNTSEC_USER` / `CNTSEC_PASSWORD` | `cntsec.datasource.username` / `password` | vacío | Credenciales de CNTSEC; nunca en el repositorio |
| `CNTSEC_INIT_SCRIPT` | `cntsec.datasource.init-script` | vacío | `classpath:cntsec-h2.sql` crea la CNTSEC simulada con datos sintéticos (solo H2) |
| `COBRANZA_MAX_INTENTOS` | `cobranza.validacion.max-intentos` | `3` | Intentos de verificación de identidad por `callId` |
| `COBRANZA_DIGITOS_DOCUMENTO` | `cobranza.validacion.digitos-documento` | `4` | Dígitos iniciales del documento que debe decir el cliente |
| `COBRANZA_CUOTA_MINIMA` | `cobranza.politica.cuota-minima` | `50.00` | Cuota mínima de una oferta en cuotas |
| `COBRANZA_DIAS_MAX_PRIMER_PAGO` | `cobranza.politica.dias-max-primer-pago` | `15` | Plazo máximo para el primer pago de una promesa |
| `COBRANZA_VIGENCIA_OFERTA_DIAS` | `cobranza.politica.vigencia-oferta-dias` | `5` | Vigencia de la oferta calculada |
| `ZONA_HORARIA` | — (se lee en `main`) | `America/Lima` | Zona horaria de promesas y rellamadas |

Los tramos de descuento y la reducción por cuotas son listas y se mantienen en `application.yml` (ver [Política de descuentos](#política-de-descuentos)).

## Endpoints

| Método | Endpoint | Uso |
|---|---|---|
| GET | `/api/v1/clientes/{documento}` | Lee CNTSEC (`COBTM08`): documento, nombre, nombres, apellidos y `llamable` con `motivoNoLlamable` (`FALLECIDO`, `SIN_DEUDA_VIGENTE`, `NEGOCIACION_VIGENTE`). Sin datos de deuda ni teléfonos. `404` si no existe |
| POST | `/api/v1/clientes/verificar` | Valida identidad con `confirmaIdentidad` ("sí, soy yo") **y** `digitosDocumento` (primeros `COBRANZA_DIGITOS_DOCUMENTO` dígitos). `anioNacimiento` sigue aceptado por compatibilidad. Máx. `COBRANZA_MAX_INTENTOS` intentos por `callId` |
| GET | `/api/v1/clientes/{documento}/campanas?prioridad=1&tipo=` | Lee CNTSEC (`COBTT15`, `COBTM16`, `COBTM14`): por cada deuda vigente, la campaña de CobraTwo con esa `prioridad` (1 por defecto) que esté activa (estado 3 en la campaña y en la asignación; las fechas no filtran), con la entidad y la cartera de la deuda, con su descuento (`FORMULA`, `FIJO` o `LIBRE`) y `oferta`: monto preaprobado y porcentaje de descuento calculados como en CobraTwo (`getTM15NDEUPREAPR`: saldos tras pagos de `COBTT03` y fórmula de la campaña). Una deuda suele tener una por `tipo` (`COBRANZA_GENERAL`, `COBRANZA_DIGITAL`). Solo tras validar identidad. `404` si el cliente no existe |
| GET | `/api/v1/deudas/{documento}` | Deudas del cliente (producto, entidad de origen, saldo total, días de mora, estado) |
| POST | `/api/v1/ofertas/calcular` | Descuento y cuotas según política para `documento`, `deudaId` y `cuotas` |
| POST | `/api/v1/promesas` | Registra promesa de pago; el monto se recalcula en el servidor y la deuda pasa a `CON_PROMESA` |
| POST | `/api/v1/rellamadas` | Agenda rellamada con `fechaHora` futura; nace en estado `PENDIENTE` |
| GET | `/api/v1/rellamadas?pendientes=true&desde=&hasta=` | Rellamadas para que el discador reprograme. `pendientes` filtra por estado `PENDIENTE`; `desde`/`hasta` acotan `fechaHora` (ISO, inclusivo) |
| POST | `/api/v1/llamadas` | Inicio de llamada: `callId`, `documento`, `telefono`, `perfil` (`validador`, `seguimiento`, `negociador`), `campana` y `guionHash` opcionales. `201`; `409` si el `callId` ya existe |
| GET | `/api/v1/llamadas/{callId}` | La llamada con su último resultado (`null` si no cerró) y sus eventos en orden cronológico. `404` si no fue registrada |
| POST | `/api/v1/llamadas/{callId}/eventos` | Registra cada herramienta ejecutada: `herramienta`, `argumentos` (JSON), `respuesta` (JSON), `timestamp` opcional. `404` si la llamada no existe |
| POST | `/api/v1/llamadas/{callId}/resultado` | Tipificación final con `resumen` opcional. Campos aditivos opcionales: `guionHash`, `transcripcion` (texto largo) y `resumenEstructurado` (JSON). Si la llamada fue registrada, fija su fin |
| POST | `/api/v1/telefonos/validacion` | Resultado de la llamada de validación, guardado en `AGENTEIATT01` de CNTSEC: `documento`, `telefono`, `resultado`, `callId` y opcionales `inicioLlamada` (contestó), `finLlamada` (colgó), `duracionSegundos` y `resumen`. Devuelve `esCliente` y la duración. `201`; `409` si el `callId` ya fue registrado |

Tipificaciones aceptadas en el resultado de llamada: `PROMESA_PAGO`, `RELLAMADA`, `RECHAZO`, `TERCERO`, `NUMERO_EQUIVOCADO`, `TRANSFERIDO`, `SIN_VALIDAR`, `OTRO`, `CONTACTO_VALIDADO`, `NO_EXISTE`, `INFORMADO`, `PROMESA_SIMPLE`. El mapeo a las tipificaciones del discador vive en back-discador.

Resultados aceptados en la validación de teléfono: `CONTACTO_VALIDADO`, `TERCERO`, `NUMERO_EQUIVOCADO`, `SIN_VALIDAR`, `NO_EXISTE`.

Reglas que viven en código, no en el prompt del agente: sin identidad validada no se entregan datos de deuda, y los montos los calcula esta API. La fecha del primer pago de una promesa debe estar entre hoy y el plazo máximo de la política.

Flujo del validador en una llamada: el discador consulta `GET /clientes/{documento}` y solo origina la llamada si `llamable` es `true` → `POST /llamadas` al iniciar la sesión → el agente saluda con `nombre`, pregunta "¿hablo con…?" y pide los primeros 4 dígitos del documento → `POST /clientes/verificar` → cada herramienta se registra en `POST /llamadas/{callId}/eventos` → `POST /telefonos/validacion` con el resultado (`CONTACTO_VALIDADO`, `SIN_VALIDAR`, `TERCERO`, `NUMERO_EQUIVOCADO`, `NO_EXISTE`) → `POST /llamadas/{callId}/resultado` al cerrar. `GET /llamadas/{callId}` permite auditar la gestión completa. El validador no recibe deudas, días de mora ni teléfonos: el número marcado viene de la lista cargada en el discador.

## Configuración del agente (depósito versionado) — OBSOLETO

> **Se retirará.** agent-voice tendrá su propio PostgreSQL para prompts, guion y configuración, así que este dominio (`pe.cobranza.api.configuracionagente`), sus endpoints y `specs/sql/config_agente.sql` quedan obsoletos. No forman parte del camino de desarrollo ni del esquema `esquema_propio.sql`; se conservan hasta que agent-voice migre.

Los prompts, el guion y los `config.yaml` de cada perfil de agent-voice, más `global.yaml`, ya no viven en archivos del servidor del agente: se guardan en la **BD propia** de esta API, por **ámbito** (`global` o el nombre de un perfil: `validador`, `seguimiento`, `negociador` o los que se creen). Cada ámbito tiene versiones numeradas 1..n, inmutables (la publicada es la de mayor número) y a lo sumo un borrador. Una versión o borrador es un mapa `nombreArchivo -> contenido` con nombres permitidos `prompt.md`, `guion.json`, `config.yaml` y `global.yaml`, texto UTF-8 de hasta 256 KB por archivo. El ámbito cumple `^[a-z][a-z0-9_-]{0,31}$`. La consola de agent-voice escribe; agent-voice lee. Paquete `pe.cobranza.api.configuracionagente`.

| Método | Endpoint | Uso |
|---|---|---|
| GET | `/api/v1/configuracion-agente` | Ámbitos conocidos con `versionVigente` (`null` si nunca se publicó), `hayBorrador` y `publicadoEn` |
| GET | `/api/v1/configuracion-agente/{ambito}` | Versión publicada con sus archivos. `404` si el ámbito no tiene ninguna versión |
| POST | `/api/v1/configuracion-agente/{ambito}/publicar` | Crea la versión `n+1` con `archivos`, `autor` y `comentario`, y elimina el borrador del ámbito. `201`; `400` si `archivos` está vacío, un nombre no está permitido o un contenido supera 256 KB |
| GET | `/api/v1/configuracion-agente/{ambito}/borrador` | Borrador del ámbito. `404` si no hay |
| PUT | `/api/v1/configuracion-agente/{ambito}/borrador` | Crea o reemplaza por completo el borrador (`archivos`, `autor`, `comentario`) |
| DELETE | `/api/v1/configuracion-agente/{ambito}/borrador` | Elimina el borrador. `204`, idempotente |
| GET | `/api/v1/configuracion-agente/{ambito}/versiones` | Historial (`numero`, `autor`, `comentario`, `fecha`) de mayor a menor, sin archivos |
| GET | `/api/v1/configuracion-agente/{ambito}/versiones/{numero}` | Una versión concreta con sus archivos. `404` si no existe |

"Restaurar" una versión lo hace el cliente: lee `versiones/{numero}` y guarda el resultado como borrador con `PUT`.

Tablas (BD propia): `config_agente_version` (único `(ambito, numero)`) y `config_agente_version_archivo` (PK `(version_id, nombre)`); `config_agente_borrador` (PK `ambito`) y `config_agente_borrador_archivo` (PK `(ambito, nombre)`).

> **Persistencia.** Sin perfil (H2 en memoria) la configuración **se pierde al reiniciar la API**; agent-voice la vuelve a sembrar desde sus archivos cuando el ámbito no existe (`GET /{ambito}` responde `404`). Con el perfil `sqlserver` las tablas solo existen si se ejecutó [`specs/sql/config_agente.sql`](specs/sql/config_agente.sql) (opcional y obsoleto).

## Política de descuentos

Se define en `src/main/resources/application.yml`, bajo `cobranza.politica`:

- Tramos de descuento máximo por días de mora.
- Reducción en puntos porcentuales según número de cuotas (1, 3, 6 y 12).
- Cuota mínima (`COBRANZA_CUOTA_MINIMA`).
- Plazo máximo para el primer pago (`COBRANZA_DIAS_MAX_PRIMER_PAGO`) y vigencia de la oferta en días (`COBRANZA_VIGENCIA_OFERTA_DIAS`).

## CNTSEC (core CobraTwo)

La API tiene dos fuentes de datos:

- **BD propia** (`DB_URL`): clientes y deudas de negociación, promesas, llamadas, eventos, resultados y rellamadas. SQL Server con el perfil `sqlserver` tanto en desarrollo (BD de desarrollo) como en producción; H2 solo en las pruebas. Las validaciones de teléfono no van aquí sino en `AGENTEIATT01` de CNTSEC.
- **CNTSEC** (`CNTSEC_HOST`): la BD de CobraTwo. Por defecto el servidor de desarrollo `FNTSRVD4`; en producción se activa el perfil `prod` (`application-prod.yml`), que solo cambia el servidor (`--spring.profiles.active=prod`, o `prod,sqlserver` si la BD propia también va en SQL Server). Se consulta con SQL nativo (`pe.cobranza.api.cntsec`), con un pool de **solo lectura** salvo que se habilite el registro de gestión. Nunca se crean tablas ahí.

Tablas de CNTSEC que usa el validador (nomenclatura CobraTwo, sinónimo legible entre paréntesis):

| Tabla | Uso | Ids de catálogo `COBTG01` (configurables en `cntsec.catalogo`) |
|---|---|---|
| `COBTM08` (`TM_CLIENTECB`) | Cliente por documento: nombres, apellidos, fecha de nacimiento, situación | fallecido = 423 |
| `COBTM15` (`TM_DEUDACB`) | Solo para saber si hay deuda vigente | vigente = 417 |
| `COBTT02` (`TT_NEGOCIACIONCB`) | Si hay negociación vigente no se llama | vigente = 554 |
| `AGENTEIATT01` (`TT_VALIDACION_LLAMADA_IA`) | **Tabla propia del agente**, la única en la que la API escribe: cliente, teléfono, fecha en que contestó, fecha en que colgó, duración, resumen, es o no el cliente y fecha de registro. Se crea con `specs/sql/AGENTEIATT01.sql` (ya ejecutado en FNTSRVD4; pendiente en producción) | — |

No se escribe en `COBTT01` ni en ninguna otra tabla de CobraTwo.

Para desarrollo o pruebas sin acceso a la red: `CNTSEC_URL=jdbc:h2:mem:cntsec;MODE=MSSQLServer;DB_CLOSE_DELAY=-1` y `CNTSEC_INIT_SCRIPT=classpath:cntsec-h2.sql` levantan una CNTSEC simulada con los mismos documentos sintéticos de `data.sql` más un fallecido (`33445566`), uno sin deuda vigente (`55667788`) y uno con negociación vigente (`10293847`). Las pruebas usan esa copia.

## SQL Server (BD propia)

Un único camino: **desarrollo y producción = SQL Server con el perfil `sqlserver`; pruebas = H2**. El perfil lee `DB_URL`, `DB_USER` y `DB_PASSWORD` del entorno (no hay credenciales en el repositorio), Hibernate no toca el esquema (`ddl-auto: none`) y no se cargan datos sintéticos. `DB_URL` no debe apuntar a CNTSEC.

El esquema lo crea [`specs/sql/esquema_propio.sql`](specs/sql/esquema_propio.sql), idempotente y con tipos coherentes con las entidades JPA (`nvarchar`, `datetime2`, `nvarchar(max)` para transcripción, resumen estructurado y JSON de eventos, `numeric(12,2)` para montos):

| Tabla | Entidad | Índices además de la PK |
|---|---|---|
| `cliente` | `Cliente` | único `documento` |
| `deuda` | `Deuda` | `cliente_id` (FK a `cliente`) |
| `promesa_pago` | `PromesaPago` | `deuda_id` (FK a `deuda`), `call_id` |
| `llamada` | `Llamada` | único `call_id`; `documento, inicio` |
| `evento_llamada` | `EventoLlamada` | `call_id` |
| `resultado_llamada` | `ResultadoLlamada` | `call_id`; `documento, creado_en` |
| `rellamada` | `Rellamada` | `estado, fecha_hora`; `documento`; `call_id` |

`EsquemaPropioSqlServerTest` garantiza que el script y las entidades no se desalineen: lo ejecuta en H2 (`MODE=MSSQLServer`), valida con Hibernate y el dialecto de SQL Server (`ddl-auto: validate`) y comprueba que no falte ni sobre ninguna columna. Al añadir o cambiar una entidad hay que actualizar el script (y aplicarlo a la BD de desarrollo) o la prueba falla. Para producción se ejecuta el mismo script en la BD propia de producción y se combinan perfiles: `--spring.profiles.active=prod,sqlserver`.

## Datos sintéticos

Solo con H2 (pruebas y arranque sin perfil) se cargan desde `src/main/resources/data.sql`; en la BD de desarrollo las tablas `cliente` y `deuda` nacen vacías. Son datos ficticios: nunca uses datos reales de deudores en desarrollo (Ley 29733).

| Documento | Nombre | Año de nacimiento | Deudas |
|---|---|---|---|
| 45879632 | Carlos Ramírez Soto | 1985 | 1 (S/ 4,850, 820 días de mora) |
| 70123456 | María Quispe Huamán | 1992 | 1 (S/ 2,100, 450 días de mora) |
| 10293847 | Jorge Salazar Prado | 1978 | 2 (S/ 15,200, 1,250 días; S/ 6,400, 700 días) |

En H2 los datos están en memoria: al reiniciar la API se borra todo lo registrado y puedes repetir la prueba. Con el perfil `sqlserver` lo registrado persiste en la BD de desarrollo.

## Pruebas

```bash
./mvnw test
```

Las pruebas corren **siempre en H2**: la clase base `PruebaApi` activa el perfil `test` (`src/test/resources/application-test.yml`), que fija la BD propia y la CNTSEC simulada en memoria con valores sin placeholders, así que `./mvnw test` nunca toca una BD real aunque el entorno tenga `DB_URL` o `SPRING_PROFILES_ACTIVE=sqlserver`. Cubren con MockMvc sobre el contexto completo (datos sintéticos) los endpoints, la compatibilidad del cuerpo original del resultado, la regresión de la fase 1, el contrato OpenAPI contra las rutas y catálogos del código, y `EsquemaPropioSqlServerTest` valida `specs/sql/esquema_propio.sql` contra las entidades.

## Próximos endpoints

Pendientes de implementar (solo cambios aditivos):

- [ ] `GET /api/v1/cuotas/{documento}` — cuotas del cliente.
- [ ] `GET /api/v1/clientes/{documento}/contexto` — contexto consolidado del cliente para el agente.
- [ ] `GET /api/v1/conocimiento` — consulta a la base de conocimiento.
- [ ] Marcar una rellamada como `PROGRAMADA` cuando el discador la tome.
