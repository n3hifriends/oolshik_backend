-- Vxxx__add_firebase_uid.sql (Flyway/Liquibase)
ALTER TABLE app_user
    ADD COLUMN firebase_uid VARCHAR(128);

ALTER TABLE app_user
    ADD CONSTRAINT uq_app_user_firebase_uid UNIQUE (firebase_uid);