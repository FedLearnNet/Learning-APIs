-- The platform runs the aggregator of a training across clinics itself, as one more attempt of the federated step
-- that holds the coordinator slot of the relay.
alter table execution_experiment
    add column platform_is_coordinator boolean not null default false;

alter table execution_federated_attempt
    add column relay_config jsonb;
