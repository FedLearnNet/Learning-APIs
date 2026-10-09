-- Relay run state per workflow step: which relay client id was assigned to which clinic, and whether the
-- relay run was already stopped on the relay server.
alter table project_federated_experiments_steps
    add column relay_client_ids jsonb,
    add column relay_stopped boolean;
