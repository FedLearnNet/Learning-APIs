INSERT INTO experiments (id, name, description, input, federated_app_version_id, created_at, updated_at, version)
VALUES (1, 'Experiment 1', 'Description for Experiment 1', 'Input Data 1', 2, NOW(), NOW(), 0),
       (2, 'Experiment 2', 'Description for Experiment 2', 'Input Data 2', 2, NOW(), NOW(), 0),
       (3, 'Experiment 3', 'Description for Experiment 3', 'Input Data 3', 3, NOW(), NOW(), 0);

INSERT INTO experiments_runs (id, status, name, error, hyperParams, experiment_id, created_at, updated_at, version)
VALUES (1, 'RUNNING', 'Name 1', NULL, '{"learning_rate": 0.01, "batch_size": 32, "epochs": 10}', 1, NOW(), NOW(), 0),
       (2, 'FINISHED', 'Name 2', NULL, '{"learning_rate": 0.02, "batch_size": 32, "epochs": 10}', 1, NOW(), NOW(), 0),
       (3, 'ERROR', 'Name 3', 'Error message', '{"learning_rate": 0.03, "batch_size": 32, "epochs": 10}', 1, NOW(),
        NOW(), 0),
       (4, 'RUNNING', 'Name 4', NULL, '{"learning_rate": 0.01, "batch_size": 40, "epochs": 10}', 2, NOW(), NOW(), 0),
       (5, 'FINISHED', 'Name 5', NULL, '{"learning_rate": 0.01, "batch_size": 32, "epochs": 10}', 2, NOW(), NOW(), 0),
       (6, 'ERROR', 'Name 6', 'Error message', '{"learning_rate": 0.01, "batch_size": 32, "epochs": 10}', 2, NOW(),
        NOW(), 0),
       (7, 'RUNNING', 'Name 7', NULL, '{"learning_rate": 0.01, "batch_size": 32, "epochs": 10}', 3, NOW(), NOW(), 0),
       (8, 'FINISHED', 'Name 8', NULL, '{"learning_rate": 0.01, "batch_size": 32, "epochs": 10}', 3, NOW(), NOW(), 0),
       (9, 'ERROR', 'Name 9', 'Error message', '{"learning_rate": 0.01, "batch_size": 32, "epochs": 10}', 3, NOW(),
        NOW(), 0);

INSERT INTO experiments_run_messages (id, created_at, updated_at, version, process, type, message, experiment_run_id)
VALUES (1, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "0", "x": "1", "xUnit": "ms"}', 1),
       (2, NOW(), NOW(), 0, 'Prozess 2', 'LOG',
        '{"severity": "INFO", "message": "Log-Nachricht 2", "caller": "ClassA.method1", "stackTrace": "Trace line 1", "group": "GroupA"}',
        1),
       (3, NOW(), NOW(), 0, 'Prozess 3', 'METRIC',
        '{"metric": "Metrik-Nachricht 3", "value": "150", "x": "15", "xUnit": "ms"}', 3),
       (4, NOW(), NOW(), 0, 'Prozess 4', 'LOG',
        '{"severity": "ERROR", "message": "Log-Nachricht 4", "caller": "ClassB.method2", "stackTrace": "Trace line 2", "group": "GroupB"}',
        2),
       (6, NOW(), NOW(), 0, 'Prozess 6', 'LOG',
        '{"severity": "WARN", "message": "Log-Nachricht 6", "caller": "ClassC.method3", "stackTrace": "Trace line 3", "group": "GroupA"}',
        1),
       (7, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "10", "x": "2", "xUnit": "epoch"}', 1),
       (8, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "30", "x": "3", "xUnit": "epoch"}', 1),
       (9, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "40", "x": "4", "xUnit": "epoch"}', 1),
       (10, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "60", "x": "5", "xUnit": "epoch"}', 1),
       (11, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "80", "x": "6", "xUnit": "epoch"}', 1),
       (12, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "90", "x": "7", "xUnit": "epoch"}', 1),
       (13, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "93", "x": "8", "xUnit": "epoch"}', 1),
       (14, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "94", "x": "9", "xUnit": "epoch"}', 1),
       (15, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "95", "x": "10", "xUnit": "epoch"}', 1),
       (16, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "95", "x": "11", "xUnit": "epoch"}', 1);



INSERT INTO test_runs (id, created_at, updated_at, version, status, error, input, hyperParams, output,
                       federated_app_version_id)
VALUES (1, NOW() - INTERVAL '1 day', NOW(), 0, 'FINISHED', NULL, 'Input 1',
        '{"learning_rate": 0.01, "batch_size": 32, "epochs": 10}',
        '{"text": "text", "image": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABgAAAAYCAYAAADgdz34AAAABHNCSVQICAgIfAhkiAAAAAlwSFlzAAAApgAAAKYB3X3/OAAAABl0RVh0U29mdHdhcmUAd3d3Lmlua3NjYXBlLm9yZ5vuPBoAAANCSURBVEiJtZZPbBtFFMZ/M7ubXdtdb1xSFyeilBapySVU8h8OoFaooFSqiihIVIpQBKci6KEg9Q6H9kovIHoCIVQJJCKE1ENFjnAgcaSGC6rEnxBwA04Tx43t2FnvDAfjkNibxgHxnWb2e/u992bee7tCa00YFsffekFY+nUzFtjW0LrvjRXrCDIAaPLlW0nHL0SsZtVoaF98mLrx3pdhOqLtYPHChahZcYYO7KvPFxvRl5XPp1sN3adWiD1ZAqD6XYK1b/dvE5IWryTt2udLFedwc1+9kLp+vbbpoDh+6TklxBeAi9TL0taeWpdmZzQDry0AcO+jQ12RyohqqoYoo8RDwJrU+qXkjWtfi8Xxt58BdQuwQs9qC/afLwCw8tnQbqYAPsgxE1S6F3EAIXux2oQFKm0ihMsOF71dHYx+f3NND68ghCu1YIoePPQN1pGRABkJ6Bus96CutRZMydTl+TvuiRW1m3n0eDl0vRPcEysqdXn+jsQPsrHMquGeXEaY4Yk4wxWcY5V/9scqOMOVUFthatyTy8QyqwZ+kDURKoMWxNKr2EeqVKcTNOajqKoBgOE28U4tdQl5p5bwCw7BWquaZSzAPlwjlithJtp3pTImSqQRrb2Z8PHGigD4RZuNX6JYj6wj7O4TFLbCO/Mn/m8R+h6rYSUb3ekokRY6f/YukArN979jcW+V/S8g0eT/N3VN3kTqWbQ428m9/8k0P/1aIhF36PccEl6EhOcAUCrXKZXXWS3XKd2vc/TRBG9O5ELC17MmWubD2nKhUKZa26Ba2+D3P+4/MNCFwg59oWVeYhkzgN/JDR8deKBoD7Y+ljEjGZ0sosXVTvbc6RHirr2reNy1OXd6pJsQ+gqjk8VWFYmHrwBzW/n+uMPFiRwHB2I7ih8ciHFxIkd/3Omk5tCDV1t+2nNu5sxxpDFNx+huNhVT3/zMDz8usXC3ddaHBj1GHj/As08fwTS7Kt1HBTmyN29vdwAw+/wbwLVOJ3uAD1wi/dUH7Qei66PfyuRj4Ik9is+hglfbkbfR3cnZm7chlUWLdwmprtCohX4HUtlOcQjLYCu+fzGJH2QRKvP3UNz8bWk1qMxjGTOMThZ3kvgLI5AzFfo379UAAAAASUVORK5CYII=", "html": "<p><strong>Example HTML:</strong> This is a <span style=''color:red;''>roter</span> Text.</p>"}',
        1),
       (2, NOW() - INTERVAL '2 day', NOW(), 0, 'RUNNING', NULL, 'Input 2',
        '{"learning_rate": 0.02, "batch_size": 64, "epochs": 20}', NULL, 2),
       (3, NOW() - INTERVAL '3 day', NOW(), 0, 'ERROR', 'Fehlerbeschreibung 3', 'Input 3',
        '{"learning_rate": 0.03, "batch_size": 128, "epochs": 30}', NULL, 3),
       (4, NOW() - INTERVAL '4 day', NOW(), 0, 'INITIALIZED', NULL, 'Input 4',
        '{"learning_rate": 0.04, "batch_size": 32, "epochs": 40}',
        '{"image": "test"}', 4),
       (5, NOW() - INTERVAL '5 day', NOW(), 0, 'PENDING', NULL, 'Input 5',
        '{"learning_rate": 0.05, "batch_size": 64, "epochs": 50}', NULL, 5),
       (6, NOW() - INTERVAL '6 day', NOW(), 0, 'STARTED', NULL, 'Input 6',
        '{"learning_rate": 0.06, "batch_size": 128, "epochs": 60}', '{"image": "test"}', 1),
       (7, NOW() - INTERVAL '7 day', NOW(), 0, 'RUNNING', NULL, 'Input 7',
        '{"learning_rate": 0.07, "batch_size": 32, "epochs": 70}', NULL, 2),
       (8, NOW() - INTERVAL '8 day', NOW(), 0, 'FINISHED', NULL, 'Input 8',
        '{"learning_rate": 0.08, "batch_size": 64, "epochs": 80}', NULL, 3),
       (9, NOW() - INTERVAL '9 day', NOW(), 0, 'ERROR', 'Fehlerbeschreibung 9', 'Input 9',
        '{"learning_rate": 0.09, "batch_size": 128, "epochs": 90}', NULL, 4);


INSERT INTO test_run_messages (id, created_at, updated_at, version, process, type, message, test_run_id)
VALUES (1, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "0", "x": "1", "xUnit": "ms"}', 1),
       (2, NOW(), NOW(), 0, 'Prozess 2', 'LOG',
        '{"severity": "INFO", "message": "Log-Nachricht 2", "caller": "ClassA.method1", "stackTrace": "Trace line 1", "group": "GroupA"}',
        1),
       (3, NOW(), NOW(), 0, 'Prozess 3', 'METRIC',
        '{"metric": "Metrik-Nachricht 3", "value": "150", "x": "15", "xUnit": "ms"}', 3),
       (4, NOW(), NOW(), 0, 'Prozess 4', 'LOG',
        '{"severity": "ERROR", "message": "Log-Nachricht 4", "caller": "ClassB.method2", "stackTrace": "Trace line 2", "group": "GroupB"}',
        1),
       (5, NOW(), NOW(), 0, 'Prozess 5', 'METRIC',
        '{"metric": "Metrik-Nachricht 5", "value": "200", "x": "20", "xUnit": "ms"}', 5),
       (6, NOW(), NOW(), 0, 'Prozess 6', 'LOG',
        '{"severity": "WARN", "message": "Log-Nachricht 6", "caller": "ClassC.method3", "stackTrace": "Trace line 3", "group": "GroupA"}',
        1),
       (7, NOW(), NOW(), 0, 'Prozess 7', 'METRIC',
        '{"metric": "Metrik-Nachricht 7", "value": "250", "x": "25", "xUnit": "ms"}', 7),
       (8, NOW(), NOW(), 0, 'Prozess 8', 'LOG',
        '{"severity": "INFO", "message": "Log-Nachricht 8", "caller": "ClassD.method4", "stackTrace": "Trace line 4", "group": "GroupB"}',
        8),
       (9, NOW(), NOW(), 0, 'Prozess 9', 'METRIC',
        '{"metric": "Metrik-Nachricht 9", "value": "300", "x": "30", "xUnit": "ms"}', 9),
       (10, NOW(), NOW(), 0, 'Prozess 10', 'LOG',
        '{"severity": "ERROR", "message": "Log-Nachricht 10", "caller": "ClassE.method5", "stackTrace": "Trace line 5", "group": "GroupC"}',
        9),
       (11, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "10", "x": "2", "xUnit": "epoch"}', 1),
       (12, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "30", "x": "3", "xUnit": "epoch"}', 1),
       (13, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "40", "x": "4", "xUnit": "epoch"}', 1),
       (14, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "60", "x": "5", "xUnit": "epoch"}', 1),
       (15, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "80", "x": "6", "xUnit": "epoch"}', 1),
       (16, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "90", "x": "7", "xUnit": "epoch"}', 1),
       (17, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "93", "x": "8", "xUnit": "epoch"}', 1),
       (18, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "94", "x": "9", "xUnit": "epoch"}', 1),
       (19, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "95", "x": "10", "xUnit": "epoch"}', 1),
       (20, NOW(), NOW(), 0, 'Prozess 1', 'METRIC',
        '{"metric": "Metrik-Nachricht 1", "value": "95", "x": "11", "xUnit": "epoch"}', 1);


INSERT INTO project_federated_experiments (id, created_at, updated_at, version, global_unique_id, name, description, project_version, acceptance_count,
                                           acceptance_clinic_count, experiment_status, relay_server_address, clinic_is_coordinator,
                                           coordinator_id, started_at, finished_at, diagram_config, project_id)
VALUES (1, NOW(), NOW(), 0, '54bc4d77-a641-4e17-9c07-85a8c79c39a0', 'Experiment Alpha', 'Test-Experiment', NULL, 10, 3, 'INIT', NULL,  FALSE, NULL, NULL, NULL, NULL, 1),
       (2, NOW(), NOW(), 0, '64bc4d77-a641-4e17-9c07-85a8c79c39a0', 'Experiment Beta', 'Test-Experiment', NULL, 10, 3, 'INIT', NULL,  FALSE, NULL, NULL, NULL, NULL, 1),
       (3, NOW(), NOW(), 0, '74bc4d77-a641-4e17-9c07-85a8c79c39a0', 'Experiment Delta', 'Test-Experiment', NULL, 10, 3, 'INIT', NULL,  FALSE, NULL, NULL, NULL, NULL, 2);

ALTER TABLE experiments ALTER COLUMN id RESTART WITH 4;
ALTER TABLE experiments_runs ALTER COLUMN id RESTART WITH 10;
ALTER TABLE experiments_run_messages ALTER COLUMN id RESTART WITH 17;


ALTER TABLE test_runs ALTER COLUMN id RESTART WITH 11;
ALTER TABLE test_run_messages ALTER COLUMN id RESTART WITH 21;

ALTER TABLE project_federated_experiments ALTER COLUMN id RESTART WITH 4;

