-- The platform's own aggregator reports when it finished a step (after its model upload); the step only
-- completes once the clinics and the platform aggregator are finished.
alter table project_federated_experiments_steps
    add column platform_aggregator_finished boolean default false;
