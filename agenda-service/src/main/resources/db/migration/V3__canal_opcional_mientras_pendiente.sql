-- El recordatorio de 24h (docs/TAREAS.md #18) se guarda en estado PENDIENTE
-- horas antes de enviarse: el canal solo se decide cuando RecordatorioScheduler
-- lo dispara y CanalNotificacion.enviar() lo fija. Antes de esto, toda fila de
-- programacion nacía y se enviaba en la misma transacción, así que el NOT NULL
-- original nunca se probó con una fila realmente pendiente.
ALTER TABLE notificaciones.programacion ALTER COLUMN canal DROP NOT NULL;
