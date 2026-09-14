CREATE
EXTENSION IF NOT EXISTS vector;

INSERT INTO federated_app_tags (id, created_at, updated_at, version, name, privacy)
VALUES (1, NOW(), NOW(), 0, 'Tag1', false),
       (2, NOW(), NOW(), 0, 'Tag2', false),
       (3, NOW(), NOW(), 0, 'Tag3', false),
       (4, NOW(), NOW(), 0, 'Tag4', false),
       (5, NOW(), NOW(), 0, 'Tag5', false);



INSERT INTO federated_apps (id, created_at, updated_at, version, name, slug, type, source_url, icon, old_fc_version,
                            supports_federated_learning, unique_app_id)
VALUES (1, NOW(), NOW(), 0, 'US-diabetes-130', 'us-d-130', 'EXTRACTOR',
        'https://gitlab.cosy.bio/cosybio/federated-learning/federated_db/apps/import-diabetes-130-us-hospitals-for-years-1999-2008',
        NULL, false, false, 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d'),
       (2, NOW(), NOW(), 0, 'FC-RF', 'fc-rf', 'ANALYSIS', 'https://github.com/FeatureCloud/fc-fed-random-forest',
        NULL, true, true, 'b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e'),
       (3, '2026-06-08 14:16:51.17', '2026-06-08 14:16:51.17', 0, 'FL-Test Mean Vector',
        'fl-test-mean-vector', 'ANALYSIS', NULL, NULL, NULL, true, '0a23c716-a27c-4b42-bee9-a5a29e6b988e');



INSERT INTO store_ratings (id, created_at, updated_at, version, rating, review_text, app_id,
                           keycloak_id)
VALUES (1, NOW(), NOW(), 0, 4.5, 'Great app!', 1, 'test'),
       (2, NOW(), NOW(), 0, 4.0, 'Good app!', 1, 'keycloak2'),
       (3, NOW(), NOW(), 0, 3.5, 'Average app', 1, 'keycloak3'),
       (4, NOW(), NOW(), 0, 4.2, 'Nice app', 1, 'test'),
       (5, NOW(), NOW(), 0, 4.8, 'Excellent app', 2, 'keycloak5');

INSERT INTO federated_app_authors (id, created_at, updated_at, version, federated_app_id, keycloak_id)
VALUES (1, NOW(), NOW(), 0, 1, 'test'),
       (2, NOW(), NOW(), 0, 2, 'test'),
       (3, NOW(), NOW(), 0, 3, 'test');


INSERT INTO federated_app_versions (id, created_at, updated_at, version, major_version, minor_version, patch_version,
                                    changelog, publish_status, federated_app_id, certification_level, image_name,
                                    short_description, long_description)
VALUES (1, NOW(), NOW(), 0, 1, 0, 0, 'Changelog 1.0.0', 'PUBLISHED', 1, 0, 'image1.png', 'basic GNN',
        'Long description 1'),
       (2, NOW(), NOW(), 0, 1, 1, 0, 'Changelog 1.1.0', 'PUBLISHED', 2, 0, 'unpast', 'Short description 2',
        'Long description 2'),
       (3, NOW(), NOW(), 0, 1, 2, 0, 'Changelog 1.2.0', 'UNPUBLISHED', 1, 0, 'image3.png', 'Short description 3',
        'Long description 3'),
       (4, NOW(), NOW(), 0, 2, 0, 0, 'Changelog 2.0.0', 'PUBLISHED', 2, 0, 'featurecloud.ai/basic_rf', 'RF App',
        'Long description 4'),
       (5, NOW(), NOW(), 0, 2, 1, 0, 'Changelog 2.1.0', 'UNPUBLISHED', 2, 0, 'image5.png', 'Short description 5',
        'Long description 5'),
       (6, '2026-06-08 14:16:51.179', '2026-06-08 14:16:51.179', 0, 0, 0, 0, NULL, 'UNPUBLISHED',
        3, 0, NULL, 'FL-Test MeanVectorAggregator', '');


INSERT INTO federated_app_input_configs
(id, created_at, updated_at, version, name, description, type, required, min_value, max_value, shape,
 federated_app_version_id)
VALUES (1, NOW(), NOW(), 0, 'InputConfig 1', 'Beschreibung für InputConfig 1', 'CSV', TRUE, NULL, NULL, NULL, 1),
       (2, NOW(), NOW(), 0, 'data', 'Beschreibung für InputConfig 2', 'TSV', TRUE, NULL, NULL, NULL, 2),
       (3, NOW(), NOW(), 0, 'InputConfig 3', 'Beschreibung für InputConfig 3', 'CSV', TRUE, NULL, NULL, NULL, 3),
       (4, NOW(), NOW(), 0, 'train', 'Training data', 'CSV', TRUE, NULL, NULL, NULL, 4),
       (5, NOW(), NOW(), 0, 'test', 'Test datas', 'CSV', TRUE, NULL, NULL, NULL, 4);



INSERT INTO federated_app_hyperparam_configs (id, created_at, updated_at, version, name, description, type,
                                              defaultValue, options, min_value, max_value, pattern,
                                              federated_app_version_id)
VALUES (1, NOW(), NOW(), 0, 'Hyper ParamConfig 1', 'Beschreibung für HyperParamConfig 1', 'CATEGORICAL', 'max',
        '["max", "min", "avg"]', null, null, null, 3),
       (2, NOW(), NOW(), 0, 'HyperParamConfig 2', 'Beschreibung für HyperParamConfig 2', 'STRING', 'default2', '', null,
        null, 'default', 3),
       (3, NOW(), NOW(), 0, 'Hyper ParamConfig 3', 'Beschreibung für HyperParamConfig 3', 'INTEGER', '1', '', '1', '10',
        null, 3),
       (4, NOW(), NOW(), 0, 'HyperParamConfig 4', 'Beschreibung für HyperParamConfig 4', 'FLOAT', '0.9', '', '0', '1',
        null, 3),
       (9, NOW(), NOW(), 0, 'fc_random_forest.input.train', 'FC CONFIG TRAIN', 'STRING', 'train.csv', '', null, null,
        null, 4),
       (10, NOW(), NOW(), 0, 'fc_random_forest.input.test', 'FC CONFIG TEST', 'STRING', 'test.csv', '', null, null,
        null, 4),
       (11, NOW(), NOW(), 0, 'fc_random_forest.output.pred', 'FC CONFIG OUTPUT PRED', 'STRING', 'pred.csv', '', null,
        null, null, 4),
       (12, NOW(), NOW(), 0, 'fc_random_forest.output.proba', 'FC CONFIG OUTPUT PROBA', 'STRING', 'proba.csv', '', null,
        null, null, 4),
       (13, NOW(), NOW(), 0, 'fc_random_forest.output.test', 'FC CONFIG OUTPUT TEST', 'STRING', 'test.csv', '', null,
        null, null, 4),
       (14, NOW(), NOW(), 0, 'fc_random_forest.format.sep', 'FC CONFIG FORMAT SEP', 'STRING', ',', '', null, null, null,
        4),
       (15, NOW(), NOW(), 0, 'fc_random_forest.format.label', 'FC CONFIG FORMAT LABEL', 'STRING', '10', '', null, null,
        null, 4),
       (16, NOW(), NOW(), 0, 'fc_random_forest.split.mode', 'FC CONFIG SPLIT MODE', 'STRING', 'file', '', null, null,
        null, 4),
       (17, NOW(), NOW(), 0, 'fc_random_forest.split.dir', 'FC CONFIG SPLIT DIR', 'STRING', '.', '', null, null, null,
        4),
       (18, NOW(), NOW(), 0, 'fc_random_forest.estimators', 'FC CONFIG ESTIMATORS', 'INTEGER', '100', '', null, null,
        null, 4),
       (19, NOW(), NOW(), 0, 'fc_random_forest.mode', 'FC CONFIG MODE', 'STRING', 'classification', '', null, null,
        null, 4),
       (20, NOW(), NOW(), 0, 'fc_random_forest.random_state', 'FC CONFIG RANDOM STATE', 'INTEGER', '42', '', null, null,
        null, 4);

INSERT INTO federated_app_output_configs
(id, created_at, updated_at, version, name, description, type, min_value, max_value, shape, federated_app_version_id)
VALUES (1, NOW(), NOW(), 0, 'OutputConfig 1', 'Beschreibung für OutputConfig 1', 'JSON', NULL, NULL, '[None]', 1),
       (2, NOW(), NOW(), 0, 'OutputConfig 2', 'Beschreibung für OutputConfig 2', 'HTML', NULL, NULL, '', 3);


ALTER TABLE federated_app_tags
    ALTER COLUMN id RESTART WITH 6;
ALTER TABLE federated_apps
    ALTER COLUMN id RESTART WITH 4;
ALTER TABLE federated_app_versions
    ALTER COLUMN id RESTART WITH 7;

ALTER TABLE store_ratings
    ALTER COLUMN id RESTART WITH 6;
ALTER TABLE federated_app_authors
    ALTER COLUMN id RESTART WITH 6;

ALTER TABLE federated_app_hyperparam_configs
    ALTER COLUMN id RESTART WITH 31;
ALTER TABLE federated_app_input_configs
    ALTER COLUMN id RESTART WITH 10;
ALTER TABLE federated_app_output_configs
    ALTER COLUMN id RESTART WITH 10;
