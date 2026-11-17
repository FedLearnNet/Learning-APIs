-- A cohort may link an external database instead of a schema, only for TRE runs.
alter table cohort add column if not exists tre_source_id varchar(255) unique;
alter table cohort add column if not exists external_database json;

alter table federated_learning_project add column if not exists tre_source json;
