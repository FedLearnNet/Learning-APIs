INSERT INTO workflow (id, created_at, updated_at, version, publish_status, keycloak_id)
VALUES (1, '2025-10-25 22:30:22.09', '2025-10-25 22:30:23.192', 2, 'UNPUBLISHED', 'SYSTEM');

INSERT INTO workflow_node (id, old_fc_version, created_at, global_app_id, global_app_version_id, global_model_sub_id,
                           updated_at, version, workflow_id, hyperparams, imagename, node_id, "position")
VALUES (1, NULL, '2025-10-25 22:30:22.513', 6, 10, NULL, '2025-10-25 22:30:22.513', 0, 1,
        '{"binarization_method":"kmeans","p_value":0.05,"clustering_method":"WGCNA","deep_split":2,"dynamic_tree_cut":0.5,"ceiling":3,"bidirectional":"true","seed":42,"similarity_cutoff_mode":"automatic"}',
        'gitlab.cosy.bio:5050/cosybio/posymed/apps/posymed-unpast/model:latest', '9ec73a8d-e545-4881-a705-81a10f2a86ac',
        '{"x":550,"y":200}');


INSERT INTO federated_learning_request
(id, created_at, updated_at, version, platform_user_id, global_request_id, name, description, channel_id, status)
VALUES (1, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'PLATFORM_USER_001', 1, 'Approved Request new App',
        'Description for Request 1', '4162416241624162416241624162416241624162416241624162416241624162', 'APPROVED'),
       (2, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'PLATFORM_USER_002', 2, 'Approved Request FC App',
        'Description for Request 2', '4162416241624162416241624162416241624162416241624162416241624162', 'APPROVED'),
       (3, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'PLATFORM_USER_003', 3, 'Approved Request with FC Workflow',
        'Description for Request 3', '4162416241624162416241624162416241624162416241624162416241624162', 'APPROVED'),
       (4, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'PLATFORM_USER_004', 4, 'New Request',
        'Description for Request 4', '4162416241624162416241624162416241624162416241624162416241624162', 'PENDING'),
       (5, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'PLATFORM_USER_005', 5, 'Rejected Request',
        'Description for Request 5', '4162416241624162416241624162416241624162416241624162416241624162', 'REJECTED'),
       (6, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'PLATFORM_USER_002', 6, 'Approved Request FC App Failed',
        'Description for Request 2', '4162416241624162416241624162416241624162416241624162416241624162', 'APPROVED'),
       (7, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'test', 9, 'Name for new Test',
        'Description for new Request Test', '13e208bfdb6748eb3b6ba73169d98749cb30fd587a14d9146a05e763c1f12d7e',
        'PENDING');

INSERT INTO federated_learning_project
(id, created_at, updated_at, version, verified_on, certificationLevel, name, description, export_config,
 coordinator_has_data, platform_is_coordinator, is_coordinator, query_id, request_id, workflow_id)
VALUES
  (1, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, '2025-04-12 10:00:00', 0, 'Project 1',
   'Description for Project 1',
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
   }',
   true, false, true, 1, 1, NULL),

  (2, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, '2025-04-12 10:00:00', 1, 'Project 2',
   'Description for Project 2',
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
   }',
   false, false, true, 2, 2, NULL),

  (3, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, '2025-04-12 10:00:00', 2, 'Project 3',
   'Description for Project 3',
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
   }',
   true, false, true, 3, 3, NULL),

  (4, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, '2025-04-12 10:00:00', 3, 'Project 4',
   'Description for Project 4',
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
   }',
   false, true, true, 4, 4, NULL),

  (5, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, '2025-04-12 10:00:00', 4, 'Project 5',
   'Description for Project 5',
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
   }',
   true, false, true, 5, 5, NULL),

  (6, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, '2025-04-12 10:00:00', 4, 'Project 6',
   'Description for Project 6',
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
   }',
   true, false, true, 6, 6, NULL),

  (7, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, '2025-04-12 10:00:00', 4, 'New Test Project',
   'Description for new Test Project',
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
   }',
   false, false, NULL, 1, 7, 1);


INSERT INTO patient_learning(id, created_at, updated_at, version, patient_id, request_id)
VALUES (1, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 1, 1),
       (2, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 1, 2),
       (3, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 2, 2),
       (4, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 1, 3),
       (5, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 2, 3),
       (6, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 3, 3),
       (7, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 4, 3),
       (8, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 5, 3);


ALTER TABLE workflow
    ALTER COLUMN id RESTART WITH 2;
ALTER TABLE workflow_node
    ALTER COLUMN id RESTART WITH 2;

ALTER TABLE federated_learning_request
    ALTER COLUMN id RESTART WITH 8;
ALTER TABLE federated_learning_project
    ALTER COLUMN id RESTART WITH 8;

ALTER TABLE patient_learning
    ALTER COLUMN id RESTART WITH 9;


ALTER TABLE federated_learning_experiments
    ALTER COLUMN id RESTART WITH 5;
ALTER TABLE federated_learning_experiments_steps
    ALTER COLUMN id RESTART WITH 6;
