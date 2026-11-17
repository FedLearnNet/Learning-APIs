-- Experiments of apps under development, each with a test run whose step searches a grid of hyper parameters.
INSERT INTO execution_experiment (id, created_at, updated_at, version, name, description, diagram_configs, keycloak_id,
                                  is_test, app_version_id)
VALUES (1, NOW(), NOW(), 0, 'Experiment 1', 'Description for Experiment 1', '[]', 'test', false, 2),
       (2, NOW(), NOW(), 0, 'Experiment 2', 'Description for Experiment 2', '[]', 'test', false, 2),
       (3, NOW(), NOW(), 0, 'Experiment 3', 'Description for Experiment 3', '[]', 'test', false, 3);

INSERT INTO execution_run (id, created_at, updated_at, version, public_id, name, purpose, mode, location, status,
                           started_at, keycloak_id, experiment_id)
VALUES (1, NOW(), NOW(), 0, gen_random_uuid(), 'Experiment 1', 'TEST', 'SINGLE', 'PLATFORM', 'RUNNING', NOW(), 'test', 1),
       (2, NOW(), NOW(), 0, gen_random_uuid(), 'Experiment 2', 'TEST', 'SINGLE', 'PLATFORM', 'RUNNING', NOW(), 'test', 2),
       (3, NOW(), NOW(), 0, gen_random_uuid(), 'Experiment 3', 'TEST', 'SINGLE', 'PLATFORM', 'RUNNING', NOW(), 'test', 3);

INSERT INTO execution_run_step (id, created_at, updated_at, version, public_id, run_id, name, started_at, status, search,
                                app_version_id)
VALUES (1, NOW(), NOW(), 0, gen_random_uuid(), 1, 'Experiment 1', NOW(), 'RUNNING', '{"strategy": "GRID", "space": {"learning_rate": {"type": "CHOICE", "values": [0.01, 0.02, 0.03]}}, "iterations": 10, "direction": "MAXIMIZE"}', 2),
       (2, NOW(), NOW(), 0, gen_random_uuid(), 2, 'Experiment 2', NOW(), 'RUNNING', '{"strategy": "GRID", "space": {"learning_rate": {"type": "CHOICE", "values": [0.01, 0.02, 0.03]}}, "iterations": 10, "direction": "MAXIMIZE"}', 2),
       (3, NOW(), NOW(), 0, gen_random_uuid(), 3, 'Experiment 3', NOW(), 'RUNNING', '{"strategy": "GRID", "space": {"learning_rate": {"type": "CHOICE", "values": [0.01, 0.02, 0.03]}}, "iterations": 10, "direction": "MAXIMIZE"}', 3);

INSERT INTO execution_attempt (id, created_at, updated_at, version, public_id, step_id, trial_index, params, status,
                               started_at, finished_at, failure)
VALUES (1, NOW(), NOW(), 0, gen_random_uuid(), 1, 0, '{"learning_rate": 0.01, "batch_size": 32, "epochs": 10}', 'RUNNING', NOW(), NULL, NULL),
       (2, NOW(), NOW(), 0, gen_random_uuid(), 1, 1, '{"learning_rate": 0.02, "batch_size": 32, "epochs": 10}', 'FINISHED', NOW(), NOW(), NULL),
       (3, NOW(), NOW(), 0, gen_random_uuid(), 1, 2, '{"learning_rate": 0.03, "batch_size": 32, "epochs": 10}', 'ERROR', NOW(), NOW(), '{"category": "TOOL", "message": "Error message"}'),
       (4, NOW(), NOW(), 0, gen_random_uuid(), 2, 0, '{"learning_rate": 0.01, "batch_size": 40, "epochs": 10}', 'RUNNING', NOW(), NULL, NULL),
       (5, NOW(), NOW(), 0, gen_random_uuid(), 2, 1, '{"learning_rate": 0.01, "batch_size": 32, "epochs": 10}', 'FINISHED', NOW(), NOW(), NULL),
       (6, NOW(), NOW(), 0, gen_random_uuid(), 2, 2, '{"learning_rate": 0.01, "batch_size": 32, "epochs": 10}', 'ERROR', NOW(), NOW(), '{"category": "TOOL", "message": "Error message"}'),
       (7, NOW(), NOW(), 0, gen_random_uuid(), 3, 0, '{"learning_rate": 0.01, "batch_size": 32, "epochs": 10}', 'RUNNING', NOW(), NULL, NULL),
       (8, NOW(), NOW(), 0, gen_random_uuid(), 3, 1, '{"learning_rate": 0.01, "batch_size": 32, "epochs": 10}', 'FINISHED', NOW(), NOW(), NULL),
       (9, NOW(), NOW(), 0, gen_random_uuid(), 3, 2, '{"learning_rate": 0.01, "batch_size": 32, "epochs": 10}', 'ERROR', NOW(), NOW(), '{"category": "TOOL", "message": "Error message"}');

INSERT INTO execution_message (id, created_at, updated_at, version, process, type, message, attempt_id)
VALUES (1, NOW(), NOW(), 0, 'Prozess 1', 'METRIC', '{"type": "METRIC", "metric": "Metrik-Nachricht 1", "value": "0", "x": "1", "xUnit": "ms", "runId": 1}', 1),
       (2, NOW(), NOW(), 0, 'Prozess 2', 'LOG', '{"type": "LOG", "severity": "INFO", "message": "Log-Nachricht 2", "caller": "ClassA.method1", "stackTrace": "Trace line 1", "group": "GroupA", "runId": 1}', 1),
       (3, NOW(), NOW(), 0, 'Prozess 3', 'METRIC', '{"type": "METRIC", "metric": "Metrik-Nachricht 3", "value": "150", "x": "15", "xUnit": "ms", "runId": 3}', 3),
       (4, NOW(), NOW(), 0, 'Prozess 4', 'LOG', '{"type": "LOG", "severity": "ERROR", "message": "Log-Nachricht 4", "caller": "ClassB.method2", "stackTrace": "Trace line 2", "group": "GroupB", "runId": 2}', 2),
       (6, NOW(), NOW(), 0, 'Prozess 6', 'LOG', '{"type": "LOG", "severity": "WARN", "message": "Log-Nachricht 6", "caller": "ClassC.method3", "stackTrace": "Trace line 3", "group": "GroupA", "runId": 1}', 1),
       (7, NOW(), NOW(), 0, 'Prozess 1', 'METRIC', '{"type": "METRIC", "metric": "Metrik-Nachricht 1", "value": "10", "x": "2", "xUnit": "epoch", "runId": 1}', 1),
       (8, NOW(), NOW(), 0, 'Prozess 1', 'METRIC', '{"type": "METRIC", "metric": "Metrik-Nachricht 1", "value": "30", "x": "3", "xUnit": "epoch", "runId": 1}', 1),
       (9, NOW(), NOW(), 0, 'Prozess 1', 'METRIC', '{"type": "METRIC", "metric": "Metrik-Nachricht 1", "value": "40", "x": "4", "xUnit": "epoch", "runId": 1}', 1),
       (10, NOW(), NOW(), 0, 'Prozess 1', 'METRIC', '{"type": "METRIC", "metric": "Metrik-Nachricht 1", "value": "60", "x": "5", "xUnit": "epoch", "runId": 1}', 1),
       (11, NOW(), NOW(), 0, 'Prozess 1', 'METRIC', '{"type": "METRIC", "metric": "Metrik-Nachricht 1", "value": "80", "x": "6", "xUnit": "epoch", "runId": 1}', 1),
       (12, NOW(), NOW(), 0, 'Prozess 1', 'METRIC', '{"type": "METRIC", "metric": "Metrik-Nachricht 1", "value": "90", "x": "7", "xUnit": "epoch", "runId": 1}', 1),
       (13, NOW(), NOW(), 0, 'Prozess 1', 'METRIC', '{"type": "METRIC", "metric": "Metrik-Nachricht 1", "value": "93", "x": "8", "xUnit": "epoch", "runId": 1}', 1),
       (14, NOW(), NOW(), 0, 'Prozess 1', 'METRIC', '{"type": "METRIC", "metric": "Metrik-Nachricht 1", "value": "94", "x": "9", "xUnit": "epoch", "runId": 1}', 1),
       (15, NOW(), NOW(), 0, 'Prozess 1', 'METRIC', '{"type": "METRIC", "metric": "Metrik-Nachricht 1", "value": "95", "x": "10", "xUnit": "epoch", "runId": 1}', 1),
       (16, NOW(), NOW(), 0, 'Prozess 1', 'METRIC', '{"type": "METRIC", "metric": "Metrik-Nachricht 1", "value": "95", "x": "11", "xUnit": "epoch", "runId": 1}', 1);

-- Trainings of projects across clinics, waiting for clinics to accept them.
INSERT INTO execution_experiment (id, created_at, updated_at, version, name, description, diagram_configs, keycloak_id,
                                  is_test, project_id, global_unique_id, status, acceptance_count, acceptance_clinic_count,
                                  model_can_be_public)
VALUES (4, NOW(), NOW(), 0, 'Experiment Alpha', 'Test-Experiment', '[]', 'test', false, 1, '54bc4d77-a641-4e17-9c07-85a8c79c39a0', 'INIT', 10, 3, true),
       (5, NOW(), NOW(), 0, 'Experiment Beta', 'Test-Experiment', '[]', 'test', false, 1, '64bc4d77-a641-4e17-9c07-85a8c79c39a0', 'INIT', 10, 3, true),
       (6, NOW(), NOW(), 0, 'Experiment Delta', 'Test-Experiment', '[]', 'test', false, 2, '74bc4d77-a641-4e17-9c07-85a8c79c39a0', 'INIT', 10, 3, true);

INSERT INTO execution_run (id, created_at, updated_at, version, public_id, name, purpose, mode, location, status,
                           keycloak_id, experiment_id)
VALUES (4, NOW(), NOW(), 0, gen_random_uuid(), 'Experiment Alpha', 'TRAINING', 'FEDERATED', 'LOCAL', 'PENDING', 'test', 4),
       (5, NOW(), NOW(), 0, gen_random_uuid(), 'Experiment Beta', 'TRAINING', 'FEDERATED', 'LOCAL', 'PENDING', 'test', 5),
       (6, NOW(), NOW(), 0, gen_random_uuid(), 'Experiment Delta', 'TRAINING', 'FEDERATED', 'LOCAL', 'PENDING', 'test', 6);

ALTER TABLE execution_experiment ALTER COLUMN id RESTART WITH 7;
ALTER TABLE execution_run ALTER COLUMN id RESTART WITH 7;
ALTER TABLE execution_run_step ALTER COLUMN id RESTART WITH 4;
ALTER TABLE execution_attempt ALTER COLUMN id RESTART WITH 10;
ALTER TABLE execution_message ALTER COLUMN id RESTART WITH 17;
