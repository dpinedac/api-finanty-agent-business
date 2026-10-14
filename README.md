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
| `SERVER_PORT` | `server.port` | `8080` | Puerto HTTP |
| `DB_URL` | `spring.datasource.url` | H2 en memoria | URL JDBC. Con el perfil `sqlserver` apunta a SQL Server |
| `DB_USER` | `spring.datasource.username` | `sa` | Usuario de BD (obligatorio con `sqlserver`) |
| `DB_PASSWORD` | `spring.datasource.password` | vacío | Contraseña de BD (obligatoria con `sqlserver`) |
| `COBRANZA_MAX_INTENTOS` | `cobranza.validacion.max-intentos` | `3` | Intentos de verificación de identidad por `callId` |
| `COBRANZA_CUOTA_MINIMA` | `cobranza.politica.cuota-minima` | `50.00` | Cuota mínima de una oferta en cuotas |
| `COBRANZA_DIAS_MAX_PRIMER_PAGO` | `cobranza.politica.dias-max-primer-pago` | `15` | Plazo máximo para el primer pago de una promesa |
| `COBRANZA_VIGENCIA_OFERTA_DIAS` | `cobranza.politica.vigencia-oferta-dias` | `5` | Vigencia de la oferta calculada |
| `ZONA_HORARIA` | — (se lee en `main`) | `America/Lima` | Zona horaria de promesas y rellamadas |

Los tramos de descuento y la reducción por cuotas son listas y se mantienen en `application.yml` (ver [Política de descuentos](#política-de-descuentos)).

## Endpoints

| Método | Endpoint | Uso |
|---|---|---|
| GET | `/api/v1/clientes/{documento}` | Documento y nombre para el saludo (sin datos de deuda). `404` si no existe |
| POST | `/api/v1/clientes/verificar` | Valida identidad con `documento` y `anioNacimiento` (máx. `COBRANZA_MAX_INTENTOS` intentos por `callId`, o por documento si no se envía) |
| GET | `/api/v1/deudas/{documento}` | Deudas del cliente (producto, entidad de origen, saldo total, días de mora, estado) |
| POST | `/api/v1/ofertas/calcular` | Descuento y cuotas según política para `documento`, `deudaId` y `cuotas` |
| POST | `/api/v1/promesas` | Registra promesa de pago; el monto se recalcula en el servidor y la deuda pasa a `CON_PROMESA` |
| POST | `/api/v1/rellamadas` | Agenda rellamada con `fechaHora` futura; nace en estado `PENDIENTE` |
| GET | `/api/v1/rellamadas?pendientes=true&desde=&hasta=` | Rellamadas para que el discador reprograme. `pendientes` filtra por estado `PENDIENTE`; `desde`/`hasta` acotan `fechaHora` (ISO, inclusivo) |
| POST | `/api/v1/llamadas` | Inicio de llamada: `callId`, `documento`, `telefono`, `perfil` (`validador`, `seguimiento`, `negociador`), `campana` y `guionHash` opcionales. `201`; `409` si el `callId` ya existe |
| GET | `/api/v1/llamadas/{callId}` | La llamada con su último resultado (`null` si no cerró) y sus eventos en orden cronológico. `404` si no fue registrada |
| POST | `/api/v1/llamadas/{callId}/eventos` | Registra cada herramienta ejecutada: `herramienta`, `argumentos` (JSON), `respuesta` (JSON), `timestamp` opcional. `404` si la llamada no existe |
| POST | `/api/v1/llamadas/{callId}/resultado` | Tipificación final con `resumen` opcional. Campos aditivos opcionales: `guionHash`, `transcripcion` (texto largo) y `resumenEstructurado` (JSON). Si la llamada fue registrada, fija su fin |
| POST | `/api/v1/telefonos/validacion` | Resultado de validar el teléfono marcado: `documento`, `telefono`, `resultado` y `callId`. `201` |

Tipificaciones aceptadas en el resultado de llamada: `PROMESA_PAGO`, `RELLAMADA`, `RECHAZO`, `TERCERO`, `NUMERO_EQUIVOCADO`, `TRANSFERIDO`, `SIN_VALIDAR`, `OTRO`, `CONTACTO_VALIDADO`, `NO_EXISTE`, `INFORMADO`, `PROMESA_SIMPLE`. El mapeo a las tipificaciones del discador vive en back-discador.

Resultados aceptados en la validación de teléfono: `CONTACTO_VALIDADO`, `TERCERO`, `NUMERO_EQUIVOCADO`, `SIN_VALIDAR`, `NO_EXISTE`.

Reglas que viven en código, no en el prompt del agente: sin identidad validada no se entregan datos de deuda, y los montos los calcula esta API. La fecha del primer pago de una promesa debe estar entre hoy y el plazo máximo de la política.

Flujo del validador en una llamada: `POST /llamadas` al iniciar la sesión → `GET /clientes/{documento}` para saludar → cada herramienta se registra en `POST /llamadas/{callId}/eventos` → `POST /telefonos/validacion` con el resultado de la validación → `POST /llamadas/{callId}/resultado` al cerrar. `GET /llamadas/{callId}` permite auditar la gestión completa.

## Política de descuentos

Se define en `src/main/resources/application.yml`, bajo `cobranza.politica`:

- Tramos de descuento máximo por días de mora.
- Reducción en puntos porcentuales según número de cuotas (1, 3, 6 y 12).
- Cuota mínima (`COBRANZA_CUOTA_MINIMA`).
- Plazo máximo para el primer pago (`COBRANZA_DIAS_MAX_PRIMER_PAGO`) y vigencia de la oferta en días (`COBRANZA_VIGENCIA_OFERTA_DIAS`).

## SQL Server

Para usar SQL Server activa el perfil `sqlserver`:

```bash
java -jar target/business-api-0.1.0.jar --spring.profiles.active=sqlserver
```

El perfil lee las variables de entorno `DB_URL`, `DB_USER` y `DB_PASSWORD`. Defínelas antes de arrancar; no hay credenciales en el repositorio. Con este perfil Hibernate solo valida el esquema (`ddl-auto: validate`) y no carga datos sintéticos. El esquema debe incluir las tablas nuevas `llamada`, `evento_llamada` y `validacion_telefono`, la columna `estado` en `rellamada` y las columnas `guion_hash`, `transcripcion` y `resumen_estructurado` en `resultado_llamada`.

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
