-- CNTSEC SIMULADA EN H2 (modo MSSQLServer) para pruebas y desarrollo sin red.
-- Solo las tablas y columnas de CobraTwo que usa la API. DATOS SINTÉTICOS: nunca datos reales (Ley 29733).
-- Se activa con CNTSEC_URL=jdbc:h2:mem:cntsec;MODE=MSSQLServer;DB_CLOSE_DELAY=-1 y CNTSEC_INIT_SCRIPT=classpath:cntsec-h2.sql

CREATE TABLE IF NOT EXISTS COBTM08 (            -- TM_CLIENTECB
    TM08NCLIID  INT PRIMARY KEY,
    TG01NCLITDC INT NOT NULL,                   -- tipo de documento (14 = DNI)
    TM08SCLIDOC VARCHAR(20) UNIQUE,
    TM08SCLINOM VARCHAR(150),
    TM08SCLIAPP VARCHAR(50),
    TM08SCLIAPM VARCHAR(50),
    TG01NCLITIP INT,                            -- 309 natural, 308 jurídico
    TG01NCLIEST INT,
    TG01NCLISIT INT,                            -- 422 deudor, 423 fallecido
    TM08DCLINAC DATETIME
);

CREATE TABLE IF NOT EXISTS COBTM15 (            -- TM_DEUDACB
    TM15NDEUID  INT PRIMARY KEY,
    TM08NCLIID  INT,
    TM15NDEUOPE BIGINT,
    TG01NMONID  INT,                            -- 411 soles, 410 dólares
    TG01NDEUSIT INT,                            -- 417 vigente, 418 cancelado
    TM15NDEUCAPINI DECIMAL(18,2),
    TM15NDEUINI DECIMAL(18,2),
    TM15NDEUINTINI DECIMAL(18,2),               -- interés inicial
    TM15NDEUINTMOR DECIMAL(18,2),               -- interés moratorio
    TM15NDEUINTCOM DECIMAL(18,2),               -- interés compensatorio
    TM15NDEUINTLEG DECIMAL(18,2),               -- interés legal
    TM07NCOSID  INT                             -- cartera (COBTM07)
);

CREATE TABLE IF NOT EXISTS COBTM01 (            -- entidad de origen
    TM01NENTID  INT PRIMARY KEY,
    TM01SENTDES VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS COBTM07 (            -- cartera
    TM07NCOSID  INT PRIMARY KEY,
    TM01NENTID  INT,
    TM07SCOSDES VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS COBTT03 (            -- TT_PAGOCB
    TT03NPAGID  INT PRIMARY KEY,
    TM15NDEUID  INT,
    TG01NMONID  INT,                            -- moneda del pago
    TT03DPAGFEC DATE,
    TT03NPAGIMP DECIMAL(14,2),
    TG01NPAGEST INT                             -- 3 válido
);

CREATE TABLE IF NOT EXISTS TMP_TIPOCAMBIOSUNAT (
    iIdTipCmb    INT PRIMARY KEY,
    dFecha       DATE,
    dcTipoCambio NUMERIC(5,3)
);

CREATE TABLE IF NOT EXISTS COBTT02 (            -- TT_NEGOCIACIONCB
    TT02NNEGID  INT PRIMARY KEY,
    TG01NNEGTIP INT,
    TM15NDEUID  INT,
    TG01NNEGSIT INT                             -- 554 vigente
);

CREATE TABLE IF NOT EXISTS COBTM14 (            -- TM_DESCUENTOCB
    TM14NDESID    INT PRIMARY KEY,
    TG01NDESTIP   INT,                          -- 413 fórmula, 414 monto fijo, 415 libre
    TM14SDESDES   VARCHAR(50),
    TM14NDESCUO   INT,                          -- cuotas máximas
    TM14SDESFRM   VARCHAR(100),                 -- fórmula, o el monto a pagar si el tipo es fijo
    TG01NDESEST   INT,
    TM14NDESPCENT DECIMAL(18,2)
);

CREATE TABLE IF NOT EXISTS COBTM16 (            -- TM_CAMPANACB
    TM16NCPNID  INT PRIMARY KEY,
    TM16SCPNNOM VARCHAR(50),
    TM16SCPNDES VARCHAR(150),
    TM14NDESID  INT,
    TM16DCPNNI  DATE,
    TM16DCPNFIN DATE,                           -- informativa: la vigencia la da el estado
    TG01NCPNEST INT,                            -- 3 activo, 4 inactivo
    TG01NCPNTIP INT NOT NULL                    -- 1885 cobranza general, 1886 cobranza digital
);

CREATE TABLE IF NOT EXISTS COBTT15 (            -- TT_CAMPANA_DEUDACB
    TT15NDEUCAMIID     BIGINT PRIMARY KEY,
    TM15NDEUID         INT NOT NULL,
    TM16NCPNID         INT NOT NULL,
    TT15NPRIO          INT NOT NULL,            -- prioridad de la campaña dentro de la deuda (1 = principal)
    TG01NGENCAMPDEUEST INT NOT NULL             -- 3 activo, 4 inactivo
);

CREATE TABLE IF NOT EXISTS AGENTEIATT01 (       -- TT_VALIDACION_LLAMADA_IA, tabla propia del agente (specs/sql/AGENTEIATT01.sql)
    TT01NVALLLAID    INT IDENTITY PRIMARY KEY,
    TT01SCALLID      VARCHAR(64) NOT NULL UNIQUE,
    TM08NCLIID       INT,
    TT01SCLIDOC      VARCHAR(20) NOT NULL,
    TT01STELEFONO    VARCHAR(20) NOT NULL,
    TT01DLLAMADA     DATETIME NOT NULL,
    TT01DCOLGADO     DATETIME,
    TT01NDURACIONSEG INT,
    TT01BESCLIENTE   BIT NOT NULL,
    TT01SRESULTADO   VARCHAR(30) NOT NULL,
    TT01SRESUMEN     VARCHAR(2000),
    TT01DREGISTRO    DATETIME NOT NULL
);

MERGE INTO COBTM08 KEY (TM08NCLIID) VALUES
 (1, 14, '45879632', 'Carlos',  'Ramírez', 'Soto',   309, 3, 422, '1985-03-10'),
 (2, 14, '70123456', 'María',   'Quispe',  'Huamán', 309, 3, 422, '1992-07-21'),
 (3, 14, '10293847', 'Jorge',   'Salazar', 'Prado',  309, 3, 422, '1978-11-02'),
 (4, 14, '33445566', 'Rosa',    'Vega',    'Luna',   309, 3, 423, '1950-01-15'),
 (5, 14, '55667788', 'Pedro',   'Castro',  'Díaz',   309, 3, 422, '1980-05-05');

MERGE INTO COBTM01 KEY (TM01NENTID) VALUES
 (1, 'Banco Ejemplo');

MERGE INTO COBTM07 KEY (TM07NCOSID) VALUES
 (11, 1, 'Cartera 2019');

-- La deuda 102 no tiene cartera: entidad y cartera llegan en null.
MERGE INTO COBTM15 KEY (TM15NDEUID) VALUES
 (101, 1, 90000001, 411, 417, 3200.00, 4850.00, 1000.00, 400.00, 250.00, 100.00, 11),
 (102, 2, 90000002, 410, 417, 1500.00, 2100.00, 400.00, 150.00, 50.00, 0.00, NULL),
 (103, 3, 90000003, 411, 417, 9800.00, 15200.00, 3000.00, 1500.00, 900.00, 0.00, 11),
 (104, 3, 90000004, 411, 417, 5000.00, 6400.00, 900.00, 300.00, 200.00, 0.00, 11),
 (105, 4, 90000005, 411, 417, 800.00, 1200.00, 250.00, 100.00, 50.00, 0.00, 11),
 (106, 5, 90000006, 411, 418, 700.00, 900.00, 150.00, 30.00, 20.00, 0.00, 11);

-- Deuda 101 (soles): pagos válidos por 2000 (uno en dólares: 100 x 3.500) y uno anulado que no cuenta.
--   interesTotal 1650, deudaActual 4850+100-2000 = 2950, capitalActual 3200-(2000-1750) = 2950.
-- Deuda 102 (dólares): un pago de 340 soles el día sin tipo de cambio -> 340 / 3.2 (por defecto) = 106.25.
MERGE INTO TMP_TIPOCAMBIOSUNAT KEY (iIdTipCmb) VALUES
 (1, '2026-10-05', 3.500),
 (2, '2026-10-06', 3.400);

MERGE INTO COBTT03 KEY (TT03NPAGID) VALUES
 (9001, 101, 411, '2026-09-01', 1650.00, 3),
 (9002, 101, 410, '2026-10-05', 100.00, 3),
 (9003, 101, 411, '2026-10-06', 999.00, 4),
 (9004, 102, 411, '2026-09-15', 340.00, 3);

MERGE INTO COBTT02 KEY (TT02NNEGID) VALUES
 (501, 549, 104, 554);

MERGE INTO COBTM14 KEY (TM14NDESID) VALUES
 (901, 413, 'C_OFF_01_45%',     1, ' (fSaldoCapital*(1-0.45))', 3, 0.45),
 (902, 414, 'PAGO_UNICO_S/720', 1, '720',                       3, NULL),
 (903, 413, 'C_OFF_06_30%',     6, ' (fSaldoCapital*(1-0.30))', 3, 0.30),
 (904, 415, 'C_DEUDA_TOTAL',   48, ' (fTotalDeuda*(1))',        3, 1.00);

MERGE INTO COBTM16 KEY (TM16NCPNID) VALUES
 (801, 'C_OFF_01_45%',     'Cancelación con 45% de descuento', 901, '2020-01-01', NULL,         3, 1885),
 (802, 'PAGO_UNICO_S/720', 'Pago único de S/ 720',             902, '2020-01-01', '2099-12-31', 3, 1886),
 (803, 'C_OFF_06_30%',     'Seis cuotas con 30%',              903, '2020-01-01', NULL,         3, 1885),
 (804, 'C_FECHA_PASADA',   'Activa con fecha de fin pasada',   901, '2020-01-01', '2020-12-31', 3, 1886),
 (805, 'C_INACTIVA',       'Campaña inactiva',                 901, '2020-01-01', NULL,         4, 1885),
 (806, 'C_DEUDA_TOTAL',    'Deuda total',                      904, '2020-01-01', NULL,         3, 1885);

-- Deuda 101 (Carlos): prioridad 1 general (801) y digitales (802 y 804, esta con fecha de fin pasada: sale igual,
-- la vigencia la da el estado 3); prioridad 2 (803); y dos que no deben salir: campaña inactiva (805) y
-- asignación inactiva (806).
-- Deuda 102 (María): solo prioridad 1 general. Deuda 106 (Pedro) está cancelada: su campaña no sale.
MERGE INTO COBTT15 KEY (TT15NDEUCAMIID) VALUES
 (7001, 101, 801, 1, 3),
 (7002, 101, 802, 1, 3),
 (7003, 101, 803, 2, 3),
 (7004, 101, 804, 1, 3),
 (7005, 101, 805, 1, 3),
 (7006, 101, 806, 1, 4),
 (7007, 102, 806, 1, 3),
 (7008, 106, 801, 1, 3);
