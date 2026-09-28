ALTER TABLE payments
  ALTER COLUMN currency TYPE VARCHAR(3)
  USING BTRIM(currency);
