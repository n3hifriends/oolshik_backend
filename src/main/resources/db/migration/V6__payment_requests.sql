-- Enable PostGIS if not already
CREATE EXTENSION IF NOT EXISTS postgis;

-- Immutable QR-scan snapshot + UPI intent payload
CREATE TABLE IF NOT EXISTS payment_requests (
    id                UUID PRIMARY KEY,
    task_id           UUID NOT NULL,                        -- link to help_request.id (no FK to avoid deploy coupling; add later if desired)
    scanned_by_user   UUID NOT NULL,                        -- app_user.id of Karyakarta
    raw_payload       TEXT NOT NULL,                        -- entire QR string (upi:// or EMV TLV)
    raw_sha256        CHAR(64) NOT NULL,                    -- sha256(raw_payload)
    format            VARCHAR(16) NOT NULL,                 -- 'upi-uri' | 'emv' | 'unknown'

    payee_vpa         VARCHAR(255),
    payee_name        VARCHAR(255),
    mcc               VARCHAR(8),
    merchant_id       VARCHAR(128),

    amount_requested  NUMERIC(12,2),                        -- amount parsed or user-entered (nullable)
    currency          VARCHAR(8) DEFAULT 'INR' NOT NULL,
    note              VARCHAR(256),

    scan_location     geometry(Point, 4326),                -- PostGIS Point (lon/lat) SRID 4326
    scan_lat          NUMERIC(10,7),
    scan_lon          NUMERIC(10,7),

    status            VARCHAR(24) NOT NULL DEFAULT 'PENDING',  -- PENDING | INITIATED | PAID_MARKED | DISPUTED | CANCELLED
    created_at        TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMP WITHOUT TIME ZONE,

    created_by_ip     INET,
    app_version       VARCHAR(32),
    device_id         VARCHAR(64)
    );

CREATE INDEX IF NOT EXISTS idx_payment_requests_task ON payment_requests(task_id);
CREATE INDEX IF NOT EXISTS idx_payment_requests_scanner ON payment_requests(scanned_by_user);
CREATE INDEX IF NOT EXISTS idx_payment_requests_status ON payment_requests(status);
CREATE INDEX IF NOT EXISTS idx_payment_requests_gix ON payment_requests USING GIST (scan_location);