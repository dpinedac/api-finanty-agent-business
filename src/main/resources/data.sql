-- DATOS SINTÉTICOS: nunca uses datos reales de deudores en desarrollo (Ley 29733)
INSERT INTO cliente (documento, nombres, apellidos, telefono, anio_nacimiento) VALUES
 ('45879632', 'Carlos',  'Ramírez Soto',   '987654321', 1985),
 ('70123456', 'María',   'Quispe Huamán',  '912345678', 1992),
 ('10293847', 'Jorge',   'Salazar Prado',  '934567812', 1978);

INSERT INTO deuda (cliente_id, producto, entidad_origen, saldo_capital, saldo_total, dias_mora, estado) VALUES
 (1, 'Tarjeta de crédito', 'Banco Andino',      3200.00, 4850.00, 820,  'VIGENTE'),
 (2, 'Préstamo personal',  'Financiera Pacífico', 1500.00, 2100.00, 450,  'VIGENTE'),
 (3, 'Tarjeta de crédito', 'Banco Andino',      9800.00, 15200.00, 1250, 'VIGENTE'),
 (3, 'Préstamo vehicular', 'Caja Sur',           5000.00, 6400.00, 700,  'VIGENTE');
