-- Depósito versionado de la configuración del agente IA (prompts, guion, config.yaml, global.yaml) por ámbito.
-- BD PROPIA de business-api en SQL Server (perfil `sqlserver`), NUNCA CNTSEC. Ejecutar una vez por servidor.
-- Con el perfil sqlserver Hibernate no toca el esquema (ddl-auto: none); en desarrollo H2 lo crea solo (create-drop).
-- Los nombres de tabla y columna coinciden con las entidades de pe.cobranza.api.configuracionagente.

-- Versiones publicadas e inmutables: la vigente de un ámbito es la de mayor número.
IF OBJECT_ID('dbo.config_agente_version') IS NULL
BEGIN
    CREATE TABLE dbo.config_agente_version (
        id          BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_config_agente_version PRIMARY KEY,
        ambito      VARCHAR(32)   NOT NULL,   -- global | validador | seguimiento | negociador | otro perfil (^[a-z][a-z0-9_-]{0,31}$)
        numero      INT           NOT NULL,   -- 1..n dentro del ámbito
        autor       NVARCHAR(100) NOT NULL,   -- usuario de la consola que publicó
        comentario  NVARCHAR(500) NULL,       -- qué cambió en esta versión
        fecha       DATETIME2     NOT NULL,   -- fecha de publicación
        CONSTRAINT uq_config_agente_version UNIQUE (ambito, numero)
    );
END;

-- Archivos de cada versión: nombreArchivo -> contenido (texto UTF-8, hasta 256 KB; lo valida la API).
IF OBJECT_ID('dbo.config_agente_version_archivo') IS NULL
BEGIN
    CREATE TABLE dbo.config_agente_version_archivo (
        version_id  BIGINT        NOT NULL CONSTRAINT FK_config_agente_version_archivo
                                  REFERENCES dbo.config_agente_version (id) ON DELETE CASCADE,
        nombre      VARCHAR(32)   NOT NULL,   -- prompt.md | guion.json | config.yaml | global.yaml
        contenido   NVARCHAR(MAX) NOT NULL,
        CONSTRAINT PK_config_agente_version_archivo PRIMARY KEY (version_id, nombre)
    );
END;

-- Borrador: a lo sumo uno por ámbito (la clave es el ámbito). Se elimina al publicar.
IF OBJECT_ID('dbo.config_agente_borrador') IS NULL
BEGIN
    CREATE TABLE dbo.config_agente_borrador (
        ambito      VARCHAR(32)   NOT NULL CONSTRAINT PK_config_agente_borrador PRIMARY KEY,
        autor       NVARCHAR(100) NOT NULL,
        comentario  NVARCHAR(500) NULL,
        fecha       DATETIME2     NOT NULL    -- último guardado
    );
END;

IF OBJECT_ID('dbo.config_agente_borrador_archivo') IS NULL
BEGIN
    CREATE TABLE dbo.config_agente_borrador_archivo (
        ambito      VARCHAR(32)   NOT NULL CONSTRAINT FK_config_agente_borrador_archivo
                                  REFERENCES dbo.config_agente_borrador (ambito) ON DELETE CASCADE,
        nombre      VARCHAR(32)   NOT NULL,
        contenido   NVARCHAR(MAX) NOT NULL,
        CONSTRAINT PK_config_agente_borrador_archivo PRIMARY KEY (ambito, nombre)
    );
END;
