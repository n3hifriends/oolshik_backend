-- Convert fixed-length CHAR(64) to variable-length VARCHAR(64)
ALTER TABLE payment_requests
ALTER COLUMN raw_sha256 TYPE varchar(64)
  USING btrim(raw_sha256);