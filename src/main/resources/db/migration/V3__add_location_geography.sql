-- 1) Ensure PostGIS is available
CREATE EXTENSION IF NOT EXISTS postgis;

-- 2) Add geography(Point,4326) column if missing
ALTER TABLE help_request
    ADD COLUMN IF NOT EXISTS location geography(Point,4326);

-- 3) Backfill from legacy latitude/longitude (only where location is NULL)
UPDATE help_request
SET location = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
WHERE location IS NULL
  AND latitude IS NOT NULL
  AND longitude IS NOT NULL;

-- 4) Spatial index for fast ST_DWithin / ST_Distance queries
CREATE INDEX IF NOT EXISTS idx_help_request_location
    ON help_request
    USING GIST (location);

-- 5) (Optional) Reasonable default for radius if you rely on it
-- ALTER TABLE help_request ALTER COLUMN radius_meters SET DEFAULT 1000;

-- Note:
-- Keep latitude/longitude for now to avoid breaking older code paths.
-- You can drop them in a later migration after the app is fully switched.