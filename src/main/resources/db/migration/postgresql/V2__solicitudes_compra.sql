-- Conservar secuencia, órdenes, eventos e inventarios anteriores.
ALTER TABLE IF EXISTS work_orders ALTER COLUMN vehicle_plate DROP NOT NULL;
ALTER TABLE IF EXISTS work_orders ADD COLUMN IF NOT EXISTS compra_repuestos boolean NOT NULL DEFAULT false;
ALTER TABLE IF EXISTS work_orders ADD COLUMN IF NOT EXISTS aceptacion_pendiente boolean NOT NULL DEFAULT false;
ALTER TABLE IF EXISTS work_orders ADD COLUMN IF NOT EXISTS actor_aceptacion varchar(160);
ALTER TABLE IF EXISTS work_orders ADD COLUMN IF NOT EXISTS motivo_aceptacion varchar(500);
ALTER TABLE IF EXISTS work_orders ADD COLUMN IF NOT EXISTS motivo_rechazo varchar(500);
ALTER TABLE IF EXISTS work_orders ADD COLUMN IF NOT EXISTS solicitud_id varchar(36);
DO $$ BEGIN
  IF to_regclass('work_orders') IS NOT NULL THEN
    CREATE UNIQUE INDEX IF NOT EXISTS uq_work_orders_solicitud ON work_orders(solicitud_id);
  END IF;
END $$;
