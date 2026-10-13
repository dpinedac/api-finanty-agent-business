# business-api

Este repositorio es la pieza **business-api** del producto Agente Conversacional con IA para cobranza de Finanty: único escritor en base de datos, reglas de negocio y registro de gestiones. El agente de voz que consume esta API vive en [github.com/ITTeamDeveloper/finanty--agente-voice](https://github.com/ITTeamDeveloper/finanty--agente-voice).

## Stack

Java 21 + Spring Boot 4 (Web MVC, Data JPA, Validation). Base de datos H2 en memoria para desarrollo; perfil opcional para SQL Server.

## Compilar y ejecutar

Requiere JDK 21 o superior y Maven 3.9+.

```bash
mvn spring-boot:run
# o: mvn package && java -jar target/business-api-0.1.0.jar
```

Queda en `http://localhost:8080` con datos sintéticos en memoria (H2).

## Autenticación

Todas las rutas bajo `/api/` exigen la cabecera `X-API-Key`. La clave se configura con la variable de entorno `COBRANZA_API_KEY` (propiedad `cobranza.api-key`); si no coincide, la API responde `401`.

Prueba rápida:

```bash
curl -H "X-API-Key: $COBRANZA_API_KEY" http://localhost:8080/api/v1/clientes/45879632
```

## Endpoints

| Método | Endpoint | Uso |
|---|---|---|
| GET | `/api/v1/clientes/{documento}` | Documento y nombre para el saludo (sin datos de deuda). `404` si no existe |
| POST | `/api/v1/clientes/verificar` | Valida identidad con `documento` y `anioNacimiento` (máx. 3 intentos por `callId`, o por documento si no se envía) |
| GET | `/api/v1/deudas/{documento}` | Deudas del cliente (producto, entidad de origen, saldo total, días de mora, estado) |
| POST | `/api/v1/ofertas/calcular` | Descuento y cuotas según política para `documento`, `deudaId` y `cuotas` |
| POST | `/api/v1/promesas` | Registra promesa de pago; el monto se recalcula en el servidor y la deuda pasa a `CON_PROMESA` |
| POST | `/api/v1/rellamadas` | Agenda rellamada con `fechaHora` futura (la leerá el discador) |
| POST | `/api/v1/llamadas/{callId}/resultado` | Tipificación final de la llamada con resumen opcional |

Tipificaciones aceptadas en el resultado de llamada: `PROMESA_PAGO`, `RELLAMADA`, `RECHAZO`, `TERCERO`, `NUMERO_EQUIVOCADO`, `TRANSFERIDO`, `SIN_VALIDAR`, `OTRO`.

Reglas que viven en código, no en el prompt del agente: sin identidad validada no se entregan datos de deuda, y los montos los calcula esta API. La fecha del primer pago de una promesa debe estar entre hoy y el plazo máximo de la política.

## Política de descuentos

Se define en `src/main/resources/application.yml`, bajo `cobranza.politica`:

- Tramos de descuento máximo por días de mora.
- Reducción en puntos porcentuales según número de cuotas (1, 3, 6 y 12).
- Cuota mínima.
- Plazo máximo para el primer pago y vigencia de la oferta en días.

## SQL Server

Para usar SQL Server activa el perfil `sqlserver`:

```bash
java -jar target/business-api-0.1.0.jar --spring.profiles.active=sqlserver
```

El perfil lee las variables de entorno `DB_URL`, `DB_USER` y `DB_PASSWORD`. Defínelas antes de arrancar; no hay credenciales en el repositorio. Con este perfil Hibernate solo valida el esquema (`ddl-auto: validate`) y no carga datos sintéticos.

## Datos sintéticos

Con el perfil por defecto se cargan desde `src/main/resources/data.sql`. Son datos ficticios: nunca uses datos reales de deudores en desarrollo (Ley 29733).

| Documento | Nombre | Año de nacimiento | Deudas |
|---|---|---|---|
| 45879632 | Carlos Ramírez Soto | 1985 | 1 (S/ 4,850, 820 días de mora) |
| 70123456 | María Quispe Huamán | 1992 | 1 (S/ 2,100, 450 días de mora) |
| 10293847 | Jorge Salazar Prado | 1978 | 2 (S/ 15,200, 1,250 días; S/ 6,400, 700 días) |

Los datos están en memoria H2: al reiniciar la API, las promesas, rellamadas y resultados registrados se borran y puedes repetir la prueba.

## Próximos endpoints

Pendientes de implementar (solo cambios aditivos):

- [ ] `POST /api/v1/llamadas` — inicio de llamada con perfil y teléfono.
- [ ] `POST /api/v1/llamadas/{callId}/eventos` — registro de eventos de la conversación.
- [ ] `POST /api/v1/telefonos/validacion` — validación de teléfono.
- [ ] `GET /api/v1/cuotas/{documento}` — cuotas del cliente.
- [ ] `GET /api/v1/clientes/{documento}/contexto` — contexto consolidado del cliente para el agente.
- [ ] `GET /api/v1/conocimiento` — consulta a la base de conocimiento.
