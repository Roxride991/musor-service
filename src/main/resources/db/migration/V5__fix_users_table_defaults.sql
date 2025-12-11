-- V5__fix_users_table_defaults.sql
-- 1. Исправляем размер avatar_url (500 вместо 255)
ALTER TABLE users
  ALTER COLUMN avatar_url TYPE VARCHAR(500);

-- 2. Добавляем DEFAULT значения
ALTER TABLE users
  ALTER COLUMN banned SET DEFAULT false,
  ALTER COLUMN phone_verified SET DEFAULT false;

-- 3. Обновляем существующие NULL-значения на false
UPDATE users SET banned = false WHERE banned IS NULL;
UPDATE users SET phone_verified = false WHERE phone_verified IS NULL;

-- 4. Добавляем остальные недостающие колонки (обязательно!)
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_verification_method VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_verification_date TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS registration_ip VARCHAR(45);
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;