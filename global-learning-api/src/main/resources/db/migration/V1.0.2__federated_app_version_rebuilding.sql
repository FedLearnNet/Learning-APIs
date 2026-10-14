alter table federated_app_versions
    add column rebuilding boolean default false not null;
