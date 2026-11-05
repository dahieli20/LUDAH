-- NULL en auditorías anteriores: no se conocen los límites originales.
ALTER TABLE audit_records ADD COLUMN clean_max DOUBLE PRECISION;
ALTER TABLE audit_records ADD COLUMN frontier_max DOUBLE PRECISION;
