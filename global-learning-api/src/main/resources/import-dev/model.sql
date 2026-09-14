INSERT INTO models (id, name, short_description, long_description, publish_status, created_at, updated_at, version,
                    federated_app_version_id, unique_model_id)
VALUES (1, 'Model 1', 'Short Description 1', 'Long Description 1', 'PRIVATE', NOW(), NOW(), 0, 1,
        'a3f7c891-5d2e-4b8a-9c3f-1e6d4a2b8c7d'),
       (2, 'Model 2', 'Short Description 3', 'Long Description 3', 'PUBLISHED', NOW(), NOW(), 0, 2,
        '7b9e2f3a-8c1d-4e5f-a6b7-9d3c2e1f4a8b'),
       (3, 'Model 3', 'Short Description 4', 'Long Description 4', 'PRIVATE', NOW(), NOW(), 0, 4,
        '2c8d4e1f-6a7b-4c9e-8f3d-5a1b7c9e2f4d'),
       (4, 'Model 4', 'Short Description 5', 'Long Description 5', 'RESTRICTED', NOW(), NOW(), 0, 5,
        'f4d8c2e1-9b3a-4f7e-6d5c-8a2b1e9f3c7d');
INSERT INTO model_versions (id, major_version, minor_version, patch_version, publish_status, model_id, experiment_id,
                            created_at, updated_at, version, changelog)
VALUES (1, 1, 0, 0, 'PRIVATE', 1, 1, NOW(), NOW(), 0, 'Changes'),
       (2, 2, 0, 0, 'PUBLISHED', 1, 2, NOW(), NOW(), 0, 'Changes'),
       (3, 3, 1, 0, 'PUBLISHED', 2, 3, NOW(), NOW(), 0, 'Changes'),
       (4, 4, 0, 0, 'PRIVATE', 2, 3, NOW(), NOW(), 0, 'Changes');


INSERT INTO model_subs (id, status, image_name, experiment_run_id, model_version_id,
                        created_at, updated_at, version)
VALUES (1, 'INITIALIZED', 'image1.jpg', 1, 2, NOW(), NOW(), 0),
       (2, 'TRAINED', 'image2.jpg', 2, 1, NOW(), NOW(), 0),
       (3, 'TRAINED', 'unpast', 3, 3, NOW(), NOW(), 0);


INSERT INTO model_accesses (id, access, model_id, group_name, created_at, updated_at, keycloak_id, version)
VALUES (1, 'CREATED', 1, 'Group 1', NOW(), NOW(), 'test', 0),
       (2, 'CREATED', 2, 'Group 2', NOW(), NOW(), 'test', 0),
       (3, 'CREATED', 3, 'Group 3', NOW(), NOW(), 'test', 0),
       (4, 'CREATED', 4, 'Group 4', NOW(), NOW(), 'test', 0);


ALTER TABLE models ALTER COLUMN id RESTART WITH 5;

ALTER TABLE model_subs ALTER COLUMN id RESTART WITH 4;
ALTER TABLE model_versions ALTER COLUMN id RESTART WITH 5;
ALTER TABLE model_accesses ALTER COLUMN id RESTART WITH 5;