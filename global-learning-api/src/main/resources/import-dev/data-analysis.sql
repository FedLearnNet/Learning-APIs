INSERT INTO data_analysis (id, name, created_at, updated_at, keycloak_id,
                           version)
VALUES (1, 'Test Model Experiment', NOW(), NOW(), 'test', 0);

/*
INSERT INTO data_analysis_predictions (id, keycloak_id, created_at, updated_at, version, status,
                                       last_log, last_error, raw_logs, result, inputs, hyper_params, container_id,
                                       sub_model_id, app_version_id, data_analysis_id)
VALUES (1, 'test', NOW(), NOW(), 0, 'RUNNING',
        'Model execution started...', NULL, 'Container initialized. Loading model weights...',
        '{"prediction": 0.8734, "label": "positive"}', '{"age": 45, "bmi": 28.3, "glucose": 112}',
        '{"learningRate": 0.001, "epochs": 50}', 'container-abc-123', NULL,
        1, 1);

ALTER TABLE data_analysis
    ALTER COLUMN id RESTART WITH 2;
*/
ALTER TABLE data_analysis_predictions
    ALTER COLUMN id RESTART WITH 2;