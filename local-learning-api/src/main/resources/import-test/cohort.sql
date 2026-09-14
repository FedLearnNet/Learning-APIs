INSERT INTO cohort(id, created_at, updated_at, version, name, keycloak_id, description)
VALUES (1, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'COHORT_001', 'admin', 'cohort1 description'),
       (2, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'COHORT_002', 'admin', 'cohort2 description'),
       (3, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'COHORT_003', 'admin', 'cohort3 description'),
       (4, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'COHORT_004', 'SYSTEM', 'cohort4 description'),
       (5, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'COHORT_TEST', 'admin', 'Comprehensive test cohort with all data types'),
       (6, '2026-04-08 16:32:51.535', '2026-04-08 16:32:51.535', 0, '2026-03daibetes_datainfo_complete_harmonized', 'test',
        'Root node of the schema 2026-03daibetes_datainfo_complete_harmonized');


INSERT INTO permission
(id, created_at, updated_at, version, user_id, query_retry_time, is_allowed_to_query, query_sample_threshold,
 auto_training_access, auto_statistics_access, valid_from, valid_until, cohort_id)
VALUES (1, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'test', 3, TRUE, 100, 'ALL', 'ALL', NULL, NULL, 1),
       (2, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'test', 2, FALSE, 50, 'ALL', 'NONE', NULL, NULL, 1),
       (3, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'test', 1, TRUE, 75, 'CERTIFIED_APPS', 'ALL', NULL, NULL, 2),
       (4, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'test', 4, FALSE, 60, 'ALL', 'ALL', NULL, NULL, 2),
       (5, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'test', 3, TRUE, 90, 'ALL', 'ALL', NULL, NULL, 3),
       (6, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'test', 2, FALSE, 55, 'CERTIFIED_APPS', 'NONE', NULL, NULL, 3),
       (7, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'test', 5, TRUE, 80, 'NONE', 'ALL', NULL, NULL, 4),
       (8, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'test', 1, FALSE, 65, 'CERTIFIED_APPS', 'ALL', NULL, NULL, 4);

ALTER TABLE cohort
    ALTER COLUMN id RESTART WITH 7;
ALTER TABLE permission
    ALTER COLUMN id RESTART WITH 9;
