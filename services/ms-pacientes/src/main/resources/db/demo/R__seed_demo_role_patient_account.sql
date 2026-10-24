-- La cuenta demo PATIENT solo se vincula en entornos que incluyen db/demo.
UPDATE patient SET user_account_public_id = UUID_TO_BIN('00000000-0000-4000-8000-000000000913')
WHERE public_id = UUID_TO_BIN('10000000-0000-4000-8000-000000000001')
  AND (user_account_public_id IS NULL OR user_account_public_id = UUID_TO_BIN('20000000-0000-4000-8000-000000000001'));
