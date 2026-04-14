UPDATE app_user
SET email = NULL
WHERE email IS NOT NULL
  AND btrim(email) = '';

WITH ranked_emails AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY lower(btrim(email))
               ORDER BY
                   CASE WHEN phone_number IS NOT NULL AND btrim(phone_number) <> '' THEN 0 ELSE 1 END,
                   CASE WHEN email_verified THEN 0 ELSE 1 END,
                   created_at,
                   id
           ) AS rn
    FROM app_user
    WHERE email IS NOT NULL
)
UPDATE app_user AS app_user
SET email = 'dedup+' || replace(app_user.id::text, '-', '') || '@invalid.local'
FROM ranked_emails
WHERE app_user.id = ranked_emails.id
  AND ranked_emails.rn > 1;

UPDATE app_user
SET email = lower(btrim(email))
WHERE email IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_app_user_email_lower_unique
    ON app_user ((lower(btrim(email))))
    WHERE email IS NOT NULL;
