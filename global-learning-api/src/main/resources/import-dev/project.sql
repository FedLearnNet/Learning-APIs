INSERT INTO queries
(id, created_at, updated_at, version,
 group_id, global_unique_id, keycloak_id, name, description,
 query_string)
VALUES (1, NOW(), NOW(), 0,
        '6cd039c0-363f-4aa9-a99b-c7f02e529945', '6cd039c0-363f-4aa9-a99b-c7f02e529945', 'test', 'Weight Loss Query',
        'Weight loss of more than 5 kg over a 6 month period',
        '{
  "connecting_operator": "$and",
  "conditions": [
    {
      "ontology_id": "57cba292-ab9a-59b3-bd21-d17a327881d9",
      "data_type_id": "4dfb9a71-0d97-5d50-a40d-d77f1fe665f3",
      "operator": "<",
      "value_mode": "delta 6 months",
      "time_aggregation_method": "None",
      "value_aggregation_method": "None",
      "value": -5,
      "datatype": "Float"
    },
    {
      "ontology_id": "77fd29aa-47ab-5eb8-a1b6-21ae28be7e68",
      "data_type_id": "725625ba-93e4-5b08-9d0a-31aa22320e59",
      "operator": "=",
      "value_mode": "absolute",
      "time_aggregation_method": "None",
      "value_aggregation_method": "None",
      "value": "3141-9"
    }
  ],
  "temporal_relations": [
    {
      "source_condition": 1,
      "target_condition": 0,
      "relationship": "same group"
    }
  ]
}'),
       (2, NOW(), NOW(), 0,
        '7cd039c0-363f-4aa9-a99b-c7f02e529945', '7cd039c0-363f-4aa9-a99b-c7f02e529945', 'test', 'Heart-rate delta family combined with sex / region',
        '(HR delta 3 min < -20 AND female) OR (HR delta (any gap) > 20 AND LAZIO) OR (HR delta 3 min >= 20 AND region != LOMBARDIA)',
        '{
  "connecting_operator": "$or",
  "conditions": [
    {
      "connecting_operator": "$and",
      "conditions": [
        {
          "connecting_operator": "$and",
          "conditions": [
            {
              "ontology_id": "6442751d-e654-5b16-9e81-0110587ff4eb",
              "data_type_id": "725625ba-93e4-5b08-9d0a-31aa22320e59",
              "operator": "=",
              "value_mode": "absolute",
              "time_aggregation_method": "None",
              "value_aggregation_method": "None",
              "value": "9999-9"
            },
            {
              "ontology_id": "edb0b362-ce63-5d7d-8014-d67f9e48ab6d",
              "data_type_id": "4dfb9a71-0d97-5d50-a40d-d77f1fe665f3",
              "operator": "<",
              "value_mode": "delta 3 minutes",
              "time_aggregation_method": "None",
              "value_aggregation_method": "None",
              "value": -20,
              "datatype": "Float"
            }
          ],
          "temporal_relations": [
            {
              "source_condition": 0,
              "target_condition": 1,
              "relationship": "same group"
            }
          ]
        },
        {
          "ontology_id": "a1cbbfb8-a214-55c7-beb5-2c64fb1e1db9",
          "data_type_id": "98233adb-7f45-5ab6-b14e-9dcd5bc02f9d",
          "operator": "=",
          "value_mode": "absolute",
          "time_aggregation_method": "None",
          "value_aggregation_method": "None",
          "value": "Female (finding)"
        }
      ]
    },
    {
      "connecting_operator": "$and",
      "conditions": [
        {
          "connecting_operator": "$and",
          "conditions": [
            {
              "ontology_id": "6442751d-e654-5b16-9e81-0110587ff4eb",
              "data_type_id": "725625ba-93e4-5b08-9d0a-31aa22320e59",
              "operator": "=",
              "value_mode": "absolute",
              "time_aggregation_method": "None",
              "value_aggregation_method": "None",
              "value": "9999-9"
            },
            {
              "ontology_id": "edb0b362-ce63-5d7d-8014-d67f9e48ab6d",
              "data_type_id": "4dfb9a71-0d97-5d50-a40d-d77f1fe665f3",
              "operator": ">",
              "value_mode": "delta",
              "time_aggregation_method": "None",
              "value_aggregation_method": "None",
              "value": 20,
              "datatype": "Float"
            }
          ],
          "temporal_relations": [
            {
              "source_condition": 0,
              "target_condition": 1,
              "relationship": "same group"
            }
          ]
        },
        {
          "ontology_id": "8b37934e-2531-5069-b5c6-7c4e1eaba313",
          "data_type_id": "4dfb9a71-0d97-5d50-a40d-d77f1fe665f3",
          "operator": "=",
          "value_mode": "absolute",
          "time_aggregation_method": "None",
          "value_aggregation_method": "None",
          "value": "LAZIO"
        }
      ]
    },
    {
      "connecting_operator": "$and",
      "conditions": [
        {
          "connecting_operator": "$and",
          "conditions": [
            {
              "ontology_id": "6442751d-e654-5b16-9e81-0110587ff4eb",
              "data_type_id": "725625ba-93e4-5b08-9d0a-31aa22320e59",
              "operator": "=",
              "value_mode": "absolute",
              "time_aggregation_method": "None",
              "value_aggregation_method": "None",
              "value": "9999-9"
            },
            {
              "ontology_id": "edb0b362-ce63-5d7d-8014-d67f9e48ab6d",
              "data_type_id": "4dfb9a71-0d97-5d50-a40d-d77f1fe665f3",
              "operator": ">=",
              "value_mode": "delta 3 minutes",
              "time_aggregation_method": "None",
              "value_aggregation_method": "None",
              "value": 20,
              "datatype": "Float"
            }
          ],
          "temporal_relations": [
            {
              "source_condition": 0,
              "target_condition": 1,
              "relationship": "same group"
            }
          ]
        },
        {
          "ontology_id": "8b37934e-2531-5069-b5c6-7c4e1eaba313",
          "data_type_id": "4dfb9a71-0d97-5d50-a40d-d77f1fe665f3",
          "operator": "!=",
          "value_mode": "absolute",
          "time_aggregation_method": "None",
          "value_aggregation_method": "None",
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
