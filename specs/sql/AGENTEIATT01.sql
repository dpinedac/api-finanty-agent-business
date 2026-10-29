-- Tabla del agente IA en CNTSEC: resultado de la validación de identidad de cada llamada.
-- Nomenclatura CobraTwo (tabla codificada + sinónimo legible). Ejecutar una vez por servidor (desarrollo: FNTSRVD4; producción: el de application-prod.yml).
-- La API solo INSERTA aquí; nunca crea ni altera el esquema.

IF OBJECT_ID('dbo.AGENTEIATT01') IS NULL
BEGIN
    CREATE TABLE dbo.AGENTEIATT01 (
        TT01NVALLLAID    INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_AGENTEIATT01 PRIMARY KEY,
        TT01SCALLID      VARCHAR(64)   NOT NULL,   -- uuid de la llamada en el discador (correlación)
        TM08NCLIID       INT           NULL,       -- cliente en COBTM08 si el documento existe
        TT01SCLIDOC      VARCHAR(20)   NOT NULL,   -- documento del cliente llamado
        TT01STELEFONO    VARCHAR(20)   NOT NULL,   -- teléfono marcado (de la lista del discador)
        TT01DLLAMADA     DATETIME      NOT NULL,   -- fecha y hora en que contestó
        TT01DCOLGADO     DATETIME      NULL,       -- fecha y hora en que colgó
        TT01NDURACIONSEG INT           NULL,       -- duración en segundos desde que contestó hasta que colgó
        TT01BESCLIENTE   BIT           NOT NULL,   -- 1 = sí es el cliente (identidad validada), 0 = no
        TT01SRESULTADO   VARCHAR(30)   NOT NULL,   -- CONTACTO_VALIDADO | SIN_VALIDAR | TERCERO | NUMERO_EQUIVOCADO | NO_EXISTE
        TT01SRESUMEN     VARCHAR(2000) NULL,       -- resumen de la llamada generado por el agente
        TT01DREGISTRO    DATETIME      NOT NULL CONSTRAINT DF_AGENTEIATT01_REG DEFAULT GETDATE()
    );
    CREATE UNIQUE INDEX UQ_AGENTEIATT01_CALLID ON dbo.AGENTEIATT01 (TT01SCALLID);
    CREATE INDEX IX_AGENTEIATT01_DOC ON dbo.AGENTEIATT01 (TT01SCLIDOC, TT01DLLAMADA);
END;

IF OBJECT_ID('dbo.TT_VALIDACION_LLAMADA_IA', 'SN') IS NULL
    CREATE SYNONYM dbo.TT_VALIDACION_LLAMADA_IA FOR dbo.AGENTEIATT01;
