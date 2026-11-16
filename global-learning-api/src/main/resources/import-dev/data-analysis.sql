INSERT INTO data_analysis (id, name, created_at, updated_at, keycloak_id,
                           version)
VALUES (1, 'Test Model Experiment', NOW(), NOW(), 'test', 0);

ALTER TABLE data_analysis
    ALTER COLUMN id RESTART WITH 2;
