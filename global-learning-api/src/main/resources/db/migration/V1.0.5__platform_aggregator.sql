-- The aggregator location moved from project to experiment level, and the platform's own aggregator
-- needs its relay credentials per step.
alter table project_federated_experiments
    add column platform_is_coordinator boolean default false;

alter table project_federated_experiments_steps
    add column platform_relay_info jsonb;

alter table projects
    drop column platform_is_coordinator;
