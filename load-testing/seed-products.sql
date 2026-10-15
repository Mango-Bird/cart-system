-- Opt-in, test database only; run AFTER Hibernate has created products.
-- Product IDs remain database-generated. Re-running preserves existing rows.
INSERT INTO products (name, price, stock)
SELECT 'loadtest-keyboard', 49.90, 1000
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'loadtest-keyboard');
INSERT INTO products (name, price, stock)
SELECT 'loadtest-mouse', 19.90, 1000
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'loadtest-mouse');
