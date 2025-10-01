-- Ensure PostGIS
CREATE EXTENSION IF NOT EXISTS postgis;

-- 1) Add geography column to store Karyakarta location at accept-time
ALTER TABLE help_request
    ADD COLUMN IF NOT EXISTS helper_accept_location geography(Point,4326);

-- 2) (Optional) Timestamp when accepted
ALTER TABLE help_request
    ADD COLUMN IF NOT EXISTS helper_accepted_at timestamptz;

-- 3) Index for geo queries from helper’s start point (future nav/ETA)
CREATE INDEX IF NOT EXISTS idx_help_request_helper_accept_location
    ON help_request
    USING GIST (helper_accept_location);