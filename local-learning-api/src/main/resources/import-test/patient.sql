INSERT INTO patient_meta(id, created_at, updated_at, version, external_patient_id, cohort_id)
VALUES (1, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'PATIENT_001', 1),
       (2, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'PATIENT_002', 1),
       (3, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'PATIENT_003', 2),
       (4, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'PATIENT_004', 2),
       (5, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'PATIENT_005', 3),
       (6, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'PATIENT_006', 3),
       (7, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'PATIENT_007', 4),
       (8, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'PATIENT_008', 4),
       (9, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'PATIENT_009', 5),
       (10, '2025-04-12 10:00:00', '2025-04-12 10:00:00', 0, 'PATIENT_010', 5);

-- Patient Data Entries
-- Patient 1 (Cohort 1): Age and Diagnosis entries
INSERT INTO patient_data (id, created_at, updated_at, version, patient_id, schema_node_id, value_int, value_string, visit_id, visit_timestamp)
VALUES
-- Patient 1 time-series data for Age (schema_node_id=2, INT type)
(1, '2025-01-01 10:00:00', '2025-01-01 10:00:00', 0, 1, 2, 25, NULL, 'VISIT_001', '2025-01-01 10:00:00'),
(2, '2025-02-01 10:00:00', '2025-02-01 10:00:00', 0, 1, 2, 26, NULL, 'VISIT_002', '2025-02-01 10:00:00'),
(3, '2025-03-01 10:00:00', '2025-03-01 10:00:00', 0, 1, 2, 27, NULL, 'VISIT_003', '2025-03-01 10:00:00'),
-- Patient 1 Diagnosis (schema_node_id=3, STRING type (diagnosis))
(4, '2025-01-01 10:00:00', '2025-01-01 10:00:00', 0, 1, 3, NULL, 'Diabetes Type 2', 'VISIT_001', '2025-01-01 10:00:00'),
(5, '2025-02-01 10:00:00', '2025-02-01 10:00:00', 0, 1, 3, NULL, 'Hypertension', 'VISIT_002', '2025-02-01 10:00:00'),

-- Patient 2 (Cohort 1): Age and Diagnosis entries
(6, '2025-01-15 09:00:00', '2025-01-15 09:00:00', 0, 2, 2, 30, NULL, 'VISIT_004', '2025-01-15 09:00:00'),
(7, '2025-02-15 09:00:00', '2025-02-15 09:00:00', 0, 2, 2, 31, NULL, 'VISIT_005', '2025-02-15 09:00:00'),
(8, '2025-01-15 09:00:00', '2025-01-15 09:00:00', 0, 2, 3, NULL, 'Asthma respiratory disease', 'VISIT_004', '2025-01-15 09:00:00'),

-- Patient 3 (Cohort 2): Height and Weight entries
(9, '2025-01-10 14:00:00', '2025-01-10 14:00:00', 0, 3, 5, 175, NULL, 'VISIT_006', '2025-01-10 14:00:00'),
(10, '2025-01-10 14:00:00', '2025-01-10 14:00:00', 0, 3, 6, 70, NULL, 'VISIT_006', '2025-01-10 14:00:00'),

-- Patient 4 (Cohort 2): Height and Weight time-series
(11, '2025-01-20 08:30:00', '2025-01-20 08:30:00', 0, 4, 5, 180, NULL, 'VISIT_007', '2025-01-20 08:30:00'),
(12, '2025-01-20 08:30:00', '2025-01-20 08:30:00', 0, 4, 6, 85, NULL, 'VISIT_007', '2025-01-20 08:30:00'),
(13, '2025-02-20 08:30:00', '2025-02-20 08:30:00', 0, 4, 6, 82, NULL, 'VISIT_008', '2025-02-20 08:30:00'),

-- Patient 5 (Cohort 3): BMI entries
(14, '2025-01-05 12:00:00', '2025-01-05 12:00:00', 0, 5, 8, 22, NULL, 'VISIT_009', '2025-01-05 12:00:00'),
(15, '2025-02-05 12:00:00', '2025-02-05 12:00:00', 0, 5, 8, 23, NULL, 'VISIT_010', '2025-02-05 12:00:00'),

-- Patient 6 (Cohort 3): BMI entries
(16, '2025-01-25 15:00:00', '2025-01-25 15:00:00', 0, 6, 8, 24, NULL, 'VISIT_012', '2025-01-25 15:00:00'),

-- Patient 8 (Cohort 4): Gender entry
(17, '2025-01-15 14:00:00', '2025-01-15 14:00:00', 0, 8, 10, NULL, 'Female', 'VISIT_013', '2025-01-15 14:00:00'),

-- Patient 9 (Cohort 5): Has diabetes information via Ontology 2008 and dataype 1001 (String)
(18, '2025-01-01 10:00:00', '2025-01-01 10:00:00', 0, 9, 18, NULL, 'Diabetes Type 1', 'VISIT_014', '2025-01-01 10:00:00'),

-- Patient 10 (Cohort 5): Has diabetes information via Ontology 2008 and dataype 1008 (Categorical)
(19, '2025-01-01 10:00:00', '2025-01-01 10:00:00', 0, 10, 21, NULL, 'Diabetes Type 2', 'VISIT_015', '2025-01-01 10:00:00');

ALTER TABLE patient_meta
    ALTER COLUMN id RESTART WITH 11;
ALTER TABLE patient_data
    ALTER COLUMN id RESTART WITH 20;



-- 2026-03daibetes_datainfo_complete_harmonized --

