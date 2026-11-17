// Synthetic records only, the same as in the other databases. MERGE keeps a second run harmless.
// Community edition has no read-only roles, a TRE tool connects as neo4j.
CREATE CONSTRAINT patient_id IF NOT EXISTS FOR (p:Patient) REQUIRE p.id IS UNIQUE;

UNWIND [
  ['p01', 'female', 1958], ['p02', 'male', 1964], ['p03', 'female', 1971], ['p04', 'male', 1949],
  ['p05', 'female', 1983], ['p06', 'male', 1977], ['p07', 'female', 1990], ['p08', 'male', 1955],
  ['p09', 'female', 1966], ['p10', 'male', 1981]
] AS row
MERGE (p:Patient {id: row[0]})
SET p.sex = row[1], p.birthYear = row[2];

UNWIND [
  ['p01', 'E11.9', '2019-03-12'], ['p01', 'I10', '2017-08-01'], ['p02', 'E11.9', '2020-11-23'],
  ['p03', 'E10.9', '2012-05-04'], ['p04', 'E11.9', '2015-02-17'], ['p04', 'N18.3', '2021-06-30'],
  ['p05', 'E66.9', '2022-01-09'], ['p06', 'E11.9', '2018-09-14'], ['p07', 'E10.9', '2009-12-01'],
  ['p08', 'I10', '2016-04-22'], ['p09', 'E11.9', '2023-03-03'], ['p10', 'E66.9', '2021-10-18']
] AS row
MATCH (p:Patient {id: row[0]})
MERGE (d:Diagnosis {icd10: row[1]})
MERGE (p)-[:HAS_DIAGNOSIS {diagnosedOn: date(row[2])}]->(d);

// 4548-4 HbA1c, 2345-7 glucose, 39156-5 BMI
UNWIND [
  ['p01', '4548-4', 7.4, '%', '2024-02-01'], ['p01', '39156-5', 31.2, 'kg/m2', '2024-02-01'],
  ['p02', '4548-4', 8.1, '%', '2024-03-15'], ['p02', '2345-7', 162.0, 'mg/dL', '2024-03-15'],
  ['p03', '4548-4', 6.9, '%', '2024-01-20'], ['p04', '4548-4', 7.8, '%', '2024-04-02'],
  ['p04', '2345-7', 149.0, 'mg/dL', '2024-04-02'], ['p05', '39156-5', 34.6, 'kg/m2', '2024-02-11'],
  ['p06', '4548-4', 6.5, '%', '2024-05-07'], ['p07', '4548-4', 7.1, '%', '2024-03-28'],
  ['p08', '2345-7', 101.0, 'mg/dL', '2024-01-05'], ['p09', '4548-4', 9.2, '%', '2024-06-12'],
  ['p10', '39156-5', 32.9, 'kg/m2', '2024-02-25']
] AS row
MATCH (p:Patient {id: row[0]})
MERGE (p)-[:HAS_MEASUREMENT]->(:Measurement {loinc: row[1], value: row[2], unit: row[3], measuredOn: date(row[4])});
