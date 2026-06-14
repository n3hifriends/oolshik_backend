ALTER TABLE public.admin_broadcast
    ADD COLUMN IF NOT EXISTS route_key    VARCHAR(30)  DEFAULT 'InAppInbox',
    ADD COLUMN IF NOT EXISTS route_target_id VARCHAR(100) DEFAULT NULL;
