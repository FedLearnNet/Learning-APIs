INSERT INTO queries
(id, created_at, updated_at, version,
 group_id, global_unique_id, keycloak_id, name, description,
 query_string)
VALUES (1, NOW(), NOW(), 0,
        '6cd039c0-363f-4aa9-a99b-c7f02e529945', '6cd039c0-363f-4aa9-a99b-c7f02e529945', 'test', 'Query 1',
        'Description for Query 1',
        '[
          {
            "ontologyId": "2dd810ca-0ab1-44cb-bd4f-c9fd509b9950",
            "operator": [
              {"operator": "BIGGER", "value": "100"},
              {"operator": "SMALLER_EQUAL", "value": "200"}
            ]
          },
          {
            "ontologyId": "2df01f58-5227-4baa-bfde-c7cfc997f2c4",
            "operator": [
              {"operator": "EQUAL", "value": "42"}
            ]
          }
        ]'),
       (2, NOW(), NOW(), 0,
        '7cd039c0-363f-4aa9-a99b-c7f02e529945', '7cd039c0-363f-4aa9-a99b-c7f02e529945', 'test', 'Query 2',
        'Description for Query 2',
        '[
          {
            "ontologyId": "63900-5",
            "operator": [
              {"operator": "BIGGER_EQUAL", "value": "300"}
            ]
          }
        ]'),
       (3, NOW(), NOW(), 0,
        '80f17acb-8041-4c3c-9b7b-370c489aa8cf', 'a7d79b12-5e2f-47c7-911c-9ad621f0836d', 'test', 'Test-FL',
        'Description for Query 2',
        '[{"ontologyId":"f5e88546-ce08-40e0-a645-bc40d54a72a5","dataTypeId":"8267d8b1-1be4-4035-a18d-248ee8e7f767","operator":[{"operator":"EXISTS","value":""}]}]');


INSERT INTO workflow (id, created_at, updated_at, version, keycloak_id, name, description, publish_status,
                      export_config)
VALUES (1, NOW(), NOW(), 0, 'test', 'Dev Workflow 1', 'Workflow placeholder for seeded dev project', 'PUBLISHED', NULL);


INSERT INTO projects (id, created_at, updated_at, version, certificationLevel, description, isAudited, name,
                      verified_on, query_id, export_config, workflow_id)
VALUES (1, NOW(), NOW(), 0, 1, 'Project Description 1', false, 'Project1', NOW(), 1,
        '{
          "appBased": false,
          "wideFormat": true,
          "joinFields": ["PATIENT_ID"],
          "duplicatePolicy": "KEEP_FIRST",
          "hyperParams": {},
          "globalAppVersionId": null,
          "features": [
            { "name": "feature_5001", "order": 1, "allowedDataIds": [{ "globalDataTypeId": "5001", "globalOntologyId": "2001" }], "targetDatatypeId": "5001" },
            { "name": "feature_5002", "order": 2, "allowedDataIds": [{ "globalDataTypeId": "5002", "globalOntologyId": "2002" }], "targetDatatypeId": "5002" }
          ]
        }', 1),
       (2, NOW(), NOW(), 0, 2, 'Project Description 2', false, 'Project2', NOW(), 2,
        '{
          "appBased": false,
          "wideFormat": true,
          "joinFields": ["PATIENT_ID"],
          "duplicatePolicy": "KEEP_FIRST",
          "hyperParams": {},
          "globalAppVersionId": null,
          "features": [
            { "name": "feature_5001", "order": 1, "allowedDataIds": [{ "globalDataTypeId": "5001", "globalOntologyId": "2001" }], "targetDatatypeId": "5001" },
            { "name": "feature_5002", "order": 2, "allowedDataIds": [{ "globalDataTypeId": "5002", "globalOntologyId": "2002" }], "targetDatatypeId": "5002" }
          ]
        }', NULL);


INSERT INTO project_memberships (id, created_at, updated_at, version, project_id, keycloak_id)
VALUES (1, NOW(), NOW(), 0, 1, 'test'),
       (2, NOW(), NOW(), 0, 2, 'test');

ALTER TABLE queries
    ALTER COLUMN id RESTART WITH 4;

ALTER TABLE workflow
    ALTER COLUMN id RESTART WITH 2;

ALTER TABLE workflow_node
    ALTER COLUMN id RESTART WITH 2;
ALTER TABLE workflow_connections
    ALTER COLUMN id RESTART WITH 2;

ALTER TABLE projects
    ALTER COLUMN id RESTART WITH 3;
ALTER TABLE project_memberships
    ALTER COLUMN id RESTART WITH 3;
