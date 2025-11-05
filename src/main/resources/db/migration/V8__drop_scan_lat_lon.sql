-- Remove redundant latitude/longitude columns now that scan_location (geography) is used
ALTER TABLE payment_requests
DROP COLUMN IF EXISTS scan_lat,
  DROP COLUMN IF EXISTS scan_lon;

-- Ensure scan_location is stored as geography(Point,4326)
ALTER TABLE payment_requests
ALTER COLUMN scan_location
  TYPE geography(Point,4326)
  USING scan_location::geography;