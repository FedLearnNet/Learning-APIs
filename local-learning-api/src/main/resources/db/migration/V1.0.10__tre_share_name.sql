-- the platform may show the Keycloak name of the client for a TRE cohort
alter table permission add column tre_share_name boolean;
alter table permission_audit add column tre_share_name boolean;
