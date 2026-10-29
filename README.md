# business-api

Este repositorio es la pieza **business-api** del producto Agente Conversacional con IA para cobranza de Finanty: único escritor en base de datos, reglas de negocio y registro de gestiones. El agente de voz que consume esta API vive en [github.com/ITTeamDeveloper/finanty--agente-voice](https://github.com/ITTeamDeveloper/finanty--agente-voice).

## Stack

Java 21 + Spring Boot 4 (Web MVC, Data JPA, Validation). Base de datos H2 en memoria para desarrollo; perfil opcional para SQL Server.

## Contrato

El contrato OpenAPI 3.1 está en [`specs/openapi.yaml`](specs/openapi.yaml) y se escribe antes que el código. Describe todos los endpoints, sus esquemas, ejemplos y la cabecera `X-API-Key` como `securityScheme`. La prueba `ContratoOpenApiTest` verifica que cada operación expuesta por la API esté en el contrato (y viceversa) y que los catálogos coincidan con los enums del código.

## Compilar y ejecutar

Requiere JDK 21 o superior. Maven viene incluido con el wrapper (`mvnw` / `mvnw.cmd`), así que no hace falta tenerlo en el PATH.

```bash
./mvnw spring-boot:run
# o: ./mvnw package && java -jar target/business-api-0.1.0.jar
# pruebas: ./mvnw test
```

Si `JAVA_HOME` apunta a otra versión, exporta `JAVA_HOME` hacia un JDK 21 antes de ejecutar el wrapper.

Queda en `http://localhost:8080` con datos sintéticos en memoria (H2).

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
| `DB_URL` | `spring.datasource.url` | H2 en memoria | BD **propia** de la API (llamadas, eventos, resultados, validaciones, promesas, rellamadas). Nunca CNTSEC |
| `DB_USER` | `spring.datasource.username` | `sa` | Usuario de la BD propia (obligatorio con `sqlserver`) |
| `DB_PASSWORD` | `spring.datasource.password` | vacío | Contraseña de la BD propia (obligatoria con `sqlserver`) |
| `JPA_DDL_AUTO` / `SQL_INIT_MODE` | `spring.jpa.hibernate.ddl-auto` / `spring.sql.init.mode` | `create-drop` / `always` | Solo válidos con H2; `GuardaEsquema` aborta el arranque si se usan contra otra BD |
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

## Política de descuentos

Se define en `src/main/resources/application.yml`, bajo `cobranza.politica`:

- Tramos de descuento máximo por días de mora.
- Reducción en puntos porcentuales según número de cuotas (1, 3, 6 y 12).
- Cuota mínima (`COBRANZA_CUOTA_MINIMA`).
- Plazo máximo para el primer pago (`COBRANZA_DIAS_MAX_PRIMER_PAGO`) y vigencia de la oferta en días (`COBRANZA_VIGENCIA_OFERTA_DIAS`).

## CNTSEC (core CobraTwo)

La API tiene dos fuentes de datos:

- **BD propia** (`DB_URL`): llamadas, eventos, resultados, validaciones de teléfono, promesas y rellamadas. H2 en desarrollo; en producción una BD propia en SQL Server (perfil `sqlserver`).
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

Para alojar la BD propia en SQL Server activa el perfil `sqlserver`:

```bash
java -jar target/business-api-0.1.0.jar --spring.profiles.active=sqlserver
```

El perfil lee las variables de entorno `DB_URL`, `DB_USER` y `DB_PASSWORD`. Defínelas antes de arrancar; no hay credenciales en el repositorio. Con este perfil Hibernate no toca el esquema (`ddl-auto: none`) ni carga datos sintéticos; el esquema lo crea un script de BD e incluye las tablas `llamada`, `evento_llamada`, `validacion_telefono`, `rellamada` (con `estado`) y `resultado_llamada` (con `guion_hash`, `transcripcion` y `resumen_estructurado`). `DB_URL` no debe apuntar a CNTSEC.

## Datos sintéticos

Con el perfil por defecto se cargan desde `src/main/resources/data.sql`. Son datos ficticios: nunca uses datos reales de deudores en desarrollo (Ley 29733).

| Documento | Nombre | Año de nacimiento | Deudas |
|---|---|---|---|
| 45879632 | Carlos Ramírez Soto | 1985 | 1 (S/ 4,850, 820 días de mora) |
| 70123456 | María Quispe Huamán | 1992 | 1 (S/ 2,100, 450 días de mora) |
| 10293847 | Jorge Salazar Prado | 1978 | 2 (S/ 15,200, 1,250 días; S/ 6,400, 700 días) |

Los datos están en memoria H2: al reiniciar la API, las llamadas, promesas, rellamadas y resultados registrados se borran y puedes repetir la prueba.

## Pruebas

```bash
./mvnw test
```

Pruebas con MockMvc sobre el contexto completo (H2 con datos sintéticos): endpoints nuevos, compatibilidad del cuerpo original del resultado, regresión de los endpoints de la fase 1 y verificación del contrato OpenAPI contra las rutas y catálogos del código.

## Próximos endpoints

Pendientes de implementar (solo cambios aditivos):

- [ ] `GET /api/v1/cuotas/{documento}` — cuotas del cliente.
- [ ] `GET /api/v1/clientes/{documento}/contexto` — contexto consolidado del cliente para el agente.
- [ ] `GET /api/v1/conocimiento` — consulta a la base de conocimiento.
- [ ] Marcar una rellamada como `PROGRAMADA` cuando el discador la tome.
