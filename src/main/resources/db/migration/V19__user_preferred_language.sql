ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS preferred_language VARCHAR(16);

UPDATE app_user
SET preferred_language = CASE
    WHEN preferred_language IS NOT NULL AND btrim(preferred_language) <> '' THEN preferred_language
    WHEN lower(coalesce(languages, '')) LIKE '%mr%' THEN 'mr-IN'
    ELSE 'en-IN'
END;

ALTER TABLE app_user
    ALTER COLUMN preferred_language SET DEFAULT 'en-IN';

UPDATE app_user
SET preferred_language = 'en-IN'
WHERE preferred_language IS NULL OR btrim(preferred_language) = '';

ALTER TABLE app_user
    ALTER COLUMN preferred_language SET NOT NULL;

