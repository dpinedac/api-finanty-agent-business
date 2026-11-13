-- Esquema de la BD PROPIA de business-api en SQL Server (perfil `sqlserver`). NUNCA ejecutar en CNTSEC.
-- Orden de ejecución: 1) este archivo (esquema_propio.sql). El script config_agente.sql (depósito de
-- configuración del agente) queda OBSOLETO: agent-voice tendrá su propio PostgreSQL y ese dominio se retirará;
-- solo hace falta si todavía se usan los endpoints /api/v1/configuracion-agente.
-- Idempotente (IF OBJECT_ID ... IS NULL), al estilo de AGENTEIATT01.sql. Ejecutar una vez por servidor.
--
-- Con el perfil sqlserver Hibernate no toca el esquema (ddl-auto: none) y GuardaEsquema aborta si alguien
-- configura create-drop fuera de H2. Por eso las tablas y columnas de aquí deben coincidir una a una con las
-- entidades JPA de pe.cobranza.api (nombres en snake_case por CamelCaseToUnderscoresNamingStrategy).
-- La prueba EsquemaPropioSqlServerTest ejecuta este script en H2 (MODE=MSSQLServer) y valida el esquema con
-- Hibernate (ddl-auto validate). Diferencias conocidas que la prueba salta de forma acotada:
--   * H2 no tiene control de flujo T-SQL: la prueba quita las guardas `IF OBJECT_ID(...) IS NULL`, `BEGIN` y `END;`
--     y ejecuta el resto tal cual. Todo lo demás debe ser aceptado por H2 sin cambios.
-- Tipos: nvarchar para texto, datetime2 para fecha y hora, nvarchar(max) para JSON y textos largos,
-- numeric(12,2) para montos; coinciden con lo que Hibernate genera para estas entidades en SQL Server.
-- Los catálogos (perfil, estado, tipificacion) los valida la API con sus enums (400 si el valor no existe);
-- no se ponen CHECK para no tener que alterar la tabla cada vez que el catálogo crece de forma aditiva.
-- Datos sintéticos de data.sql: solo en H2 (pruebas y arranque sin perfil); aquí las tablas nacen vacías.

-- ---------------------------------------------------------------------------------------------------------
-- cliente / deuda / promesa_pago: datos de negociación (entidades Cliente, Deuda, PromesaPago)
-- ---------------------------------------------------------------------------------------------------------
IF OBJECT_ID('dbo.cliente') IS NULL
BEGIN
    CREATE TABLE dbo.cliente (
        id              BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_cliente PRIMARY KEY,
        documento       NVARCHAR(15)  NOT NULL CONSTRAINT UQ_cliente_documento UNIQUE,
        nombres         NVARCHAR(255) NULL,
        apellidos       NVARCHAR(255) NULL,
        telefono        NVARCHAR(255) NULL,
        anio_nacimiento INT           NULL      -- dato de control para validar identidad
    );
END;

IF OBJECT_ID('dbo.deuda') IS NULL
BEGIN
    CREATE TABLE dbo.deuda (
        id              BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_deuda PRIMARY KEY,
        cliente_id      BIGINT        NOT NULL CONSTRAINT FK_deuda_cliente REFERENCES dbo.cliente (id),
        producto        NVARCHAR(255) NULL,
        entidad_origen  NVARCHAR(255) NULL,
        saldo_capital   NUMERIC(12,2) NULL,
        saldo_total     NUMERIC(12,2) NULL,
        dias_mora       INT           NOT NULL,
        estado          NVARCHAR(255) NULL      -- VIGENTE | CON_PROMESA | PAGADA
    );
    CREATE INDEX IX_deuda_cliente ON dbo.deuda (cliente_id);
END;

IF OBJECT_ID('dbo.promesa_pago') IS NULL
BEGIN
    CREATE TABLE dbo.promesa_pago (
        id                   BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_promesa_pago PRIMARY KEY,
        deuda_id             BIGINT        NOT NULL CONSTRAINT FK_promesa_pago_deuda REFERENCES dbo.deuda (id),
        cuotas               INT           NOT NULL,
        porcentaje_descuento INT           NOT NULL,
        monto_total          NUMERIC(12,2) NULL,
        monto_cuota          NUMERIC(12,2) NULL,
        fecha_primer_pago    DATE          NULL,
        call_id              NVARCHAR(255) NULL,  -- correlación con la llamada del discador
        creada_en            DATETIME2     NULL
    );
    CREATE INDEX IX_promesa_pago_deuda ON dbo.promesa_pago (deuda_id);
    CREATE INDEX IX_promesa_pago_call_id ON dbo.promesa_pago (call_id);
END;

-- promesa_campana: promesa sobre la campaña de CobraTwo de la deuda (entidad PromesaCampana). La deuda y la
-- campaña viven en CNTSEC; aquí solo van sus identificadores y los montos calculados al registrar.
IF OBJECT_ID('dbo.promesa_campana') IS NULL
BEGIN
    CREATE TABLE dbo.promesa_campana (
        id                BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_promesa_campana PRIMARY KEY,
        documento         NVARCHAR(15)  NOT NULL,
        deuda_id          BIGINT        NOT NULL,  -- TM15NDEUID de CobraTwo
        operacion         BIGINT        NULL,
        campana_id        INT           NOT NULL,  -- TM16NCPNID de CobraTwo
        moneda            NVARCHAR(10)  NOT NULL,  -- SOLES | DOLARES | OTRA
        cuotas            INT           NOT NULL,
        monto_total       NUMERIC(12,2) NOT NULL,
        monto_cuota       NUMERIC(12,2) NOT NULL,
        fecha_primer_pago DATE          NOT NULL,
        call_id           NVARCHAR(255) NULL,      -- correlación con la llamada del discador
        creada_en         DATETIME2     NULL
    );
    CREATE INDEX IX_promesa_campana_documento ON dbo.promesa_campana (documento);
    CREATE INDEX IX_promesa_campana_call_id ON dbo.promesa_campana (call_id);
END;

-- ---------------------------------------------------------------------------------------------------------
-- llamada / evento_llamada / resultado_llamada: ciclo de vida de la llamada con agente IA
-- (entidades Llamada, EventoLlamada, ResultadoLlamada). Se correlacionan por call_id, sin FK entre ellas:
-- el resultado puede llegar sin inicio registrado (compatibilidad con la fase 1).
-- ---------------------------------------------------------------------------------------------------------
IF OBJECT_ID('dbo.llamada') IS NULL
BEGIN
    CREATE TABLE dbo.llamada (
        id          BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_llamada PRIMARY KEY,
        call_id     NVARCHAR(64)  NOT NULL CONSTRAINT UQ_llamada_call_id UNIQUE,  -- uuid_llamada del discador
        documento   NVARCHAR(15)  NOT NULL,
        telefono    NVARCHAR(20)  NOT NULL,
        perfil      NVARCHAR(20)  NOT NULL,   -- VALIDADOR | SEGUIMIENTO | NEGOCIADOR
        campana     NVARCHAR(100) NULL,
        guion_hash  NVARCHAR(64)  NULL,
        inicio      DATETIME2     NOT NULL,
        fin         DATETIME2     NULL        -- se fija al registrar el resultado
    );
    CREATE INDEX IX_llamada_documento ON dbo.llamada (documento, inicio);
END;

IF OBJECT_ID('dbo.evento_llamada') IS NULL
BEGIN
    CREATE TABLE dbo.evento_llamada (
        id           BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_evento_llamada PRIMARY KEY,
        call_id      NVARCHAR(64)  NOT NULL,
        herramienta  NVARCHAR(100) NOT NULL,
        argumentos   NVARCHAR(MAX) NULL,      -- JSON tal como lo envió el agente
        respuesta    NVARCHAR(MAX) NULL,      -- JSON tal como lo envió el agente
        ocurrido_en  DATETIME2     NOT NULL
    );
    CREATE INDEX ix_evento_llamada_call_id ON dbo.evento_llamada (call_id);  -- mismo nombre que en la entidad
END;

IF OBJECT_ID('dbo.resultado_llamada') IS NULL
BEGIN
    CREATE TABLE dbo.resultado_llamada (
        id                   BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_resultado_llamada PRIMARY KEY,
        call_id              NVARCHAR(255)  NULL,
        documento            NVARCHAR(255)  NULL,
        tipificacion         NVARCHAR(255)  NULL,  -- catálogo Tipificacion (12 valores, crece de forma aditiva)
        resumen              NVARCHAR(2000) NULL,
        guion_hash           NVARCHAR(64)   NULL,
        transcripcion        NVARCHAR(MAX)  NULL,  -- conversación completa
        resumen_estructurado NVARCHAR(MAX)  NULL,  -- JSON definido por cada perfil
        creado_en            DATETIME2      NULL
    );
    CREATE INDEX IX_resultado_llamada_call_id ON dbo.resultado_llamada (call_id);
    CREATE INDEX IX_resultado_llamada_documento ON dbo.resultado_llamada (documento, creado_en);
END;

-- ---------------------------------------------------------------------------------------------------------
-- rellamada: agendadas por el agente y leídas por back-discador (entidad Rellamada)
-- ---------------------------------------------------------------------------------------------------------
IF OBJECT_ID('dbo.rellamada') IS NULL
BEGIN
    CREATE TABLE dbo.rellamada (
        id          BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_rellamada PRIMARY KEY,
        documento   NVARCHAR(255) NULL,
        fecha_hora  DATETIME2     NULL,
        motivo      NVARCHAR(255) NULL,
        call_id     NVARCHAR(255) NULL,
        estado      NVARCHAR(20)  NOT NULL,   -- PENDIENTE | PROGRAMADA
        creada_en   DATETIME2     NULL
    );
    CREATE INDEX IX_rellamada_estado_fecha ON dbo.rellamada (estado, fecha_hora);
    CREATE INDEX IX_rellamada_documento ON dbo.rellamada (documento);
    CREATE INDEX IX_rellamada_call_id ON dbo.rellamada (call_id);
END;
