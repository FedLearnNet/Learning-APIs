-- Keycloak name of a client that shares it for TRE
alter table query_result add column client_name varchar(255);
