-- Synthetic records only.
CREATE TABLE patient (
    id         VARCHAR(16) PRIMARY KEY,
    sex        VARCHAR(8) NOT NULL,
    birth_year INTEGER NOT NULL
);

CREATE TABLE diagnosis (
    patient_id   VARCHAR(16) REFERENCES patient (id),
    icd10        VARCHAR(8) NOT NULL,
    diagnosed_on DATE NOT NULL
);

CREATE TABLE measurement (
    patient_id  VARCHAR(16) REFERENCES patient (id),
    loinc       VARCHAR(16) NOT NULL,
    value       DECIMAL(8, 2) NOT NULL,
    unit        VARCHAR(8) NOT NULL,
    measured_on DATE    NOT NULL
);

INSERT INTO patient VALUES
    ('p01', 'female', 1958), ('p02', 'male', 1964), ('p03', 'female', 1971), ('p04', 'male', 1949),
    ('p05', 'female', 1983), ('p06', 'male', 1977), ('p07', 'female', 1990), ('p08', 'male', 1955),
    ('p09', 'female', 1966), ('p10', 'male', 1981);

INSERT INTO diagnosis VALUES
    ('p01', 'E11.9', '2019-03-12'), ('p01', 'I10', '2017-08-01'), ('p02', 'E11.9', '2020-11-23'),
    ('p03', 'E10.9', '2012-05-04'), ('p04', 'E11.9', '2015-02-17'), ('p04', 'N18.3', '2021-06-30'),
    ('p05', 'E66.9', '2022-01-09'), ('p06', 'E11.9', '2018-09-14'), ('p07', 'E10.9', '2009-12-01'),
    ('p08', 'I10', '2016-04-22'), ('p09', 'E11.9', '2023-03-03'), ('p10', 'E66.9', '2021-10-18');

-- 4548-4 HbA1c, 2345-7 glucose, 39156-5 BMI
INSERT INTO measurement VALUES
    ('p01', '4548-4', 7.4, '%', '2024-02-01'), ('p01', '39156-5', 31.2, 'kg/m2', '2024-02-01'),
    ('p02', '4548-4', 8.1, '%', '2024-03-15'), ('p02', '2345-7', 162, 'mg/dL', '2024-03-15'),
    ('p03', '4548-4', 6.9, '%', '2024-01-20'), ('p04', '4548-4', 7.8, '%', '2024-04-02'),
    ('p04', '2345-7', 149, 'mg/dL', '2024-04-02'), ('p05', '39156-5', 34.6, 'kg/m2', '2024-02-11'),
    ('p06', '4548-4', 6.5, '%', '2024-05-07'), ('p07', '4548-4', 7.1, '%', '2024-03-28'),
    ('p08', '2345-7', 101, 'mg/dL', '2024-01-05'), ('p09', '4548-4', 9.2, '%', '2024-06-12'),
    ('p10', '39156-5', 32.9, 'kg/m2', '2024-02-25');

-- what a TRE tool connects with
CREATE USER 'tre_reader'@'%' IDENTIFIED BY 'tre-reader';
GRANT SELECT ON clinic.* TO 'tre_reader'@'%';
