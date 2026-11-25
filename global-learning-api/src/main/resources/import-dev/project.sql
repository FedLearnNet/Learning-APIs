INSERT INTO queries
(id, created_at, updated_at, version,
 group_id, global_unique_id, keycloak_id, name, description,
 query_string)
VALUES (1, NOW(), NOW(), 0,
        '6cd039c0-363f-4aa9-a99b-c7f02e529945', '6cd039c0-363f-4aa9-a99b-c7f02e529945', 'test', 'Weight Loss Query',
        'Weight loss of more than 5 kg over a 6 month period',
        '{
  "connectingOperator": "AND",
  "conditions": [
    {
      "ontologyId": "57cba292-ab9a-59b3-bd21-d17a327881d9",
      "dataTypeId": "4dfb9a71-0d97-5d50-a40d-d77f1fe665f3",
      "operator": "SMALLER",
      "value": -5,
      "valueMode": "DELTA",
      "deltaInterval": [
        {
          "count": 6,
          "unit": "MONTHS"
        }
      ]
    },
    {
      "ontologyId": "77fd29aa-47ab-5eb8-a1b6-21ae28be7e68",
      "dataTypeId": "725625ba-93e4-5b08-9d0a-31aa22320e59",
      "operator": "EQUAL",
      "value": "3141-9"
    }
  ],
  "temporalRelations": [
    {
      "sourceCondition": 1,
      "targetCondition": 0,
      "relationship": "SAME_GROUP"
    }
  ]
}'),
       (2, NOW(), NOW(), 0,
        '7cd039c0-363f-4aa9-a99b-c7f02e529945', '7cd039c0-363f-4aa9-a99b-c7f02e529945', 'test', 'Heart-rate delta family combined with sex / region',
        '(HR delta 3 min < -20 AND female) OR (HR delta (any gap) > 20 AND LAZIO) OR (HR delta 3 min >= 20 AND region != LOMBARDIA)',
        '{
  "connectingOperator": "OR",
  "conditions": [
    {
      "connectingOperator": "AND",
      "conditions": [
        {
          "connectingOperator": "AND",
          "conditions": [
            {
              "ontologyId": "6442751d-e654-5b16-9e81-0110587ff4eb",
              "dataTypeId": "725625ba-93e4-5b08-9d0a-31aa22320e59",
              "operator": "EQUAL",
              "value": "9999-9"
            },
            {
              "ontologyId": "edb0b362-ce63-5d7d-8014-d67f9e48ab6d",
              "dataTypeId": "4dfb9a71-0d97-5d50-a40d-d77f1fe665f3",
              "operator": "SMALLER",
              "value": -20,
              "valueMode": "DELTA",
              "deltaInterval": [
                {
                  "count": 3,
                  "unit": "MINUTES"
                }
              ]
            }
          ],
          "temporalRelations": [
            {
              "sourceCondition": 0,
              "targetCondition": 1,
              "relationship": "SAME_GROUP"
            }
          ]
        },
        {
          "ontologyId": "a1cbbfb8-a214-55c7-beb5-2c64fb1e1db9",
          "dataTypeId": "98233adb-7f45-5ab6-b14e-9dcd5bc02f9d",
          "operator": "EQUAL",
          "value": "Female (finding)"
        }
      ]
    },
    {
      "connectingOperator": "AND",
      "conditions": [
        {
          "connectingOperator": "AND",
          "conditions": [
            {
              "ontologyId": "6442751d-e654-5b16-9e81-0110587ff4eb",
              "dataTypeId": "725625ba-93e4-5b08-9d0a-31aa22320e59",
              "operator": "EQUAL",
              "value": "9999-9"
            },
            {
              "ontologyId": "edb0b362-ce63-5d7d-8014-d67f9e48ab6d",
              "dataTypeId": "4dfb9a71-0d97-5d50-a40d-d77f1fe665f3",
              "operator": "BIGGER",
              "value": 20,
              "valueMode": "DELTA"
            }
          ],
          "temporalRelations": [
            {
              "sourceCondition": 0,
              "targetCondition": 1,
              "relationship": "SAME_GROUP"
            }
          ]
        },
        {
          "ontologyId": "8b37934e-2531-5069-b5c6-7c4e1eaba313",
          "dataTypeId": "4dfb9a71-0d97-5d50-a40d-d77f1fe665f3",
          "operator": "EQUAL",
          "value": "LAZIO"
        }
      ]
    },
    {
      "connectingOperator": "AND",
      "conditions": [
        {
          "connectingOperator": "AND",
          "conditions": [
            {
              "ontologyId": "6442751d-e654-5b16-9e81-0110587ff4eb",
              "dataTypeId": "725625ba-93e4-5b08-9d0a-31aa22320e59",
              "operator": "EQUAL",
              "value": "9999-9"
            },
            {
              "ontologyId": "edb0b362-ce63-5d7d-8014-d67f9e48ab6d",
              "dataTypeId": "4dfb9a71-0d97-5d50-a40d-d77f1fe665f3",
              "operator": "BIGGER_EQUAL",
              "value": 20,
              "valueMode": "DELTA",
              "deltaInterval": [
                {
                  "count": 3,
                  "unit": "MINUTES"
                }
              ]
            }
          ],
          "temporalRelations": [
            {
              "sourceCondition": 0,
              "targetCondition": 1,
              "relationship": "SAME_GROUP"
            }
          ]
        },
        {
          "ontologyId": "8b37934e-2531-5069-b5c6-7c4e1eaba313",
          "dataTypeId": "4dfb9a71-0d97-5d50-a40d-d77f1fe665f3",
          "operator": "NOT_EQUAL",
          "value": "LOMBARDIA"
        }
      ]
    }
  ]
}');


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
