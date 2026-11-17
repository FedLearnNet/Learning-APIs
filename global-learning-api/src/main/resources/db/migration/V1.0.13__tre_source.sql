-- TRE on an external database a client offers, instead of the query's data.
alter table projects add column if not exists tre_source json;
