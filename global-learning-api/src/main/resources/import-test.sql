INSERT INTO projects (id, created_at, updated_at, version, certificationLevel, description, isAudited, name,
                      verified_on)
VALUES (1, NOW(), NOW(), 0, 1, 'Project Description 1', false, 'Project1', NOW()),
       (2, NOW(), NOW(), 0, 1, 'DELETE Project TEST', false, 'TODO DELETE Project', NOW());


INSERT INTO queries (id, created_at, updated_at, version, keycloak_id, name, description, query_string, result,
                     has_result, has_fired, global_unique_id, group_id)
VALUES (1, '2024-11-01 12:00:00', '2024-11-01 12:00:00', 0, 'test', 'Query 1', 'Description for Query 1',
        '', 150, true, true, '26dac9fe-af16-4293-93ab-320ba3e57e8a', '26dac9fe-af16-4293-93ab-320ba3e57e8a');


INSERT INTO project_memberships (id, created_at, updated_at, version, project_id, keycloak_id)
VALUES (1, NOW(), NOW(), 0, 1, 'test'),
       (2, NOW(), NOW(), 0, 2, 'test');


INSERT INTO federated_apps (id, created_at, updated_at, version, name, slug, type, source_url, icon,
                            unique_app_id)
VALUES (1, NOW(), NOW(), 0, 'App 1', 'app-1', 'ANALYSIS', 'http://sourceurl1.com',
        'https://material.angular.io/assets/img/examples/shiba1.jpg', 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5e'),
       (2, NOW(), NOW(), 0, 'App 2', 'app-2', 'ANALYSIS', 'http://sourceurl2.com',
        'https://material.angular.io/assets/img/examples/shiba1.jpg', 'a7bb4791-d90c-41d6-a1f4-2ea338fff522'),
       (3, NOW(), NOW(), 0, 'App 3', 'app-3', 'POST_PROCESSING', 'http://sourceurl3.com',
        'https://material.angular.io/assets/img/examples/shiba1.jpg', '59f0c475-16b8-4280-90e5-fbd54dd0500a');

INSERT INTO federated_app_versions (id, created_at, updated_at, version, major_version, minor_version, patch_version,
                                    changelog, publish_status, federated_app_id, certification_level, image_name,
                                    short_description, long_description)
VALUES (1, NOW(), NOW(), 0, 1, 0, 0, 'Changelog 1.0.0', 'PUBLISHED', 1, 0, 'image1.png', 'Short description 1',
        'Long description 1'),
       (2, NOW(), NOW(), 0, 1, 1, 0, 'Changelog 1.1.0', 'UNPUBLISHED', 1, 0, 'image2.png', 'Short description 2',
        'Long description 2'),
       (3, NOW(), NOW(), 0, 1, 2, 0, 'Changelog 1.2.0', 'UNPUBLISHED', 1, 0, 'image3.png', 'Short description 3',
        'Long description 3');

INSERT INTO federated_app_authors (id, created_at, updated_at, version, federated_app_id, keycloak_id)
VALUES (1, NOW(), NOW(), 0, 1, 'test'),
       (2, NOW(), NOW(), 0, 2, 'test'),
       (3, NOW(), NOW(), 0, 3, 'test');


-- Insert test data into store_ratings
INSERT INTO store_ratings (id, created_at, updated_at, version, rating, review_text, app_id,
                           keycloak_id)
VALUES (1, NOW(), NOW(), 0, 4.5, 'Great app!', 1, 'test'),
       (2, NOW(), NOW(), 0, 4.0, 'Good app!', 1, 'keycloak2'),
       (3, NOW(), NOW(), 0, 3.5, 'Average app', 1, 'keycloak3'),
       (4, NOW(), NOW(), 0, 4.2, 'Nice app', 1, 'test');

INSERT INTO federated_app_tags (id, created_at, updated_at, version, name, privacy)
VALUES (1, NOW(), NOW(), 0, 'Tag1', false),
       (2, NOW(), NOW(), 0, 'Tag2', false);

-- Insert test workflows
INSERT INTO workflow (id, created_at, updated_at, version, keycloak_id, name, description, publish_status)
VALUES (1, NOW(), NOW(), 0, 'admin', 'Test Workflow 1', 'Test workflow for admin', 'PUBLISHED'),
       (10, NOW(), NOW(), 0, 'admin', 'Test Workflow 10', 'Test workflow to be deleted', 'UNPUBLISHED');

-- Create embeddings table for pgvector (LangChain4j)
-- This is normally created by LangChain4j but we define it here to avoid errors in tests
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE IF NOT EXISTS embeddings (
    embedding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    text TEXT,
    embedding vector(1024),
    metadata JSONB
);

ALTER TABLE queries
    ALTER COLUMN id RESTART WITH 2;
ALTER TABLE workflow
    ALTER COLUMN id RESTART WITH 11;
ALTER TABLE projects
    ALTER COLUMN id RESTART WITH 3;
ALTER TABLE project_memberships
    ALTER COLUMN id RESTART WITH 3;
ALTER TABLE federated_apps
    ALTER COLUMN id RESTART WITH 4;
ALTER TABLE federated_app_versions
    ALTER COLUMN id RESTART WITH 4;
ALTER TABLE federated_app_authors
    ALTER COLUMN id RESTART WITH 4;
ALTER TABLE store_ratings
    ALTER COLUMN id RESTART WITH 5;
ALTER TABLE federated_app_tags
    ALTER COLUMN id RESTART WITH 3;
