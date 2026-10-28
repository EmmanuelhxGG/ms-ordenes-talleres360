-- Amplía el estado permitido sin borrar órdenes, referencias ni secuencias.
DO $$ BEGIN
  IF to_regclass('work_orders') IS NOT NULL THEN
    ALTER TABLE work_orders DROP CONSTRAINT IF EXISTS work_orders_status_check;
    ALTER TABLE work_orders ADD CONSTRAINT work_orders_status_check
      CHECK (status IN ('RECIBIDA', 'ACEPTADA', 'EN_PREPARACION', 'EN_REPARACION',
                        'LISTA_PARA_ENTREGA', 'ENTREGADA', 'CANCELADA'));
  END IF;
END $$;
