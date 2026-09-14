INSERT INTO queries (id, created_at, updated_at, version, global_unique_query_id, status, status_message, query_string, keycloak_id)
VALUES (1, '2024-11-01 12:00:00', '2024-11-01 12:00:00', 0, '1e427196-9827-43fd-ae22-fc0f511694a9', 'COMPLETED', 'Status message for Query 1',
        '[{"ontologyId":"2001","operator":[{"operator":"EQUAL","value":"Diabetes Type 2"}]}]', NULL),
       (2, '2024-11-02 12:00:00', '2024-11-02 12:00:00', 0, '2e427196-9827-43fd-ae22-fc0f511694a9', 'COMPLETED', 'Status message for Query 2',
        '[{"ontologyId":"2002","operator":[{"operator":"BIGGER","value":"18"}]}]', NULL),
       (3, '2024-11-03 12:00:00', '2024-11-03 12:00:00', 0, '3e427196-9827-43fd-ae22-fc0f511694a9', 'COMPLETED', 'Status message for Query 3',
        '[{"ontologyId":"2001","operator":[{"operator":"CONTAINS","value":"cancer"}]}]', NULL),
       (4, '2024-11-04 12:00:00', '2024-11-04 12:00:00', 0, '4e427196-9827-43fd-ae22-fc0f511694a9', 'COMPLETED', 'Status message for Query 4',
        '[{"ontologyId":"2002","operator":[{"operator":"BIGGER_EQUAL","value":"30"},{"operator":"SMALLER_EQUAL","value":"50"}]}]', NULL),
       (5, '2024-11-05 12:00:00', '2024-11-05 12:00:00', 0, '5e427196-9827-43fd-ae22-fc0f511694a9', 'COMPLETED', 'Status message for Query 5',
        '[]', NULL),
       (6, '2024-11-05 12:00:00', '2024-11-05 12:00:00', 0, '6e427196-9827-43fd-ae22-fc0f511694a9', 'COMPLETED', 'Status message for Query 6',
        '[{"ontologyId":"2001","operator":[{"operator":"EQUAL","value":"hypertension"}]}]', NULL);

INSERT INTO query_patient(id, created_at, updated_at, version, patient_id, query_id)
VALUES (1, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 1, 1),
       (2, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 1, 2),
       (3, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 2, 1),
       (4, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 3, 1),
       (5, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 3, 4),
       (6, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 4, 1),
       (7, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 4, 4),
       (8, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 5, 4);

ALTER TABLE queries
    ALTER COLUMN id RESTART WITH 7;
ALTER TABLE query_patient
    ALTER COLUMN id RESTART WITH 9;
