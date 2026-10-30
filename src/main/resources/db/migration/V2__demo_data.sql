-- Loja e produtos de demonstração (dados fictícios; CNPJ de exemplo).
INSERT INTO stores (id, name, cnpj, state_registration, uf, ibge_city_code, city, csc_id, csc, nfce_series, next_nfce_number, version)
VALUES ('00000000-0000-0000-0000-000000000001', 'Padaria Demo', '11222333000181', '110042490114', 'SP', '3550308',
        'Sao Paulo', '1', 'DEMO-CSC-0000-0000-0000-000000000000', 1, 1, 0);

INSERT INTO products (id, store_id, sku, name, gtin, ncm, cfop, csosn, unit, price, stock, active, version) VALUES
('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'PAO-FR', 'Pão francês (kg)', NULL, '19059090', '5102', '102', 'KG', 18.90, 50, TRUE, 0),
('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'CAFE-200', 'Café expresso 200 ml', NULL, '21011110', '5102', '102', 'UN', 6.50, 500, TRUE, 0),
('10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'BOLO-FAT', 'Fatia de bolo de cenoura', NULL, '19059090', '5102', '102', 'UN', 9.00, 40, TRUE, 0),
('10000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'SUCO-LAR', 'Suco de laranja 300 ml', NULL, '20091200', '5102', '102', 'UN', 8.00, 80, TRUE, 0);
