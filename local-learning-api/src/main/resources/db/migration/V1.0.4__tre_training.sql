alter table execution_run drop constraint execution_run_mode_check;
alter table execution_run add constraint execution_run_mode_check check (mode in ('SINGLE', 'TRE', 'FEDERATED'));

alter table federated_learning_request rename to learning_request;

alter table federated_learning_project add column execution_mode varchar(20) not null default 'FEDERATED'
    check (execution_mode in ('SINGLE', 'TRE', 'FEDERATED'));

alter table permission rename column auto_training_access to federated_auto_training_access;
alter table permission add column federated_training_access varchar(255) check (federated_training_access in ('ENABLED', 'DISABLED'));
alter table permission add column tre_auto_training_access varchar(255) check (tre_auto_training_access in ('ALL', 'NONE', 'CERTIFIED_APPS'));
alter table permission add column tre_training_access varchar(255) check (tre_training_access in ('ENABLED', 'DISABLED'));

alter table permission_audit rename column auto_training_access to federated_auto_training_access;
alter table permission_audit add column federated_training_access varchar(255) check (federated_training_access in ('ENABLED', 'DISABLED'));
alter table permission_audit add column tre_auto_training_access varchar(255) check (tre_auto_training_access in ('ALL', 'NONE', 'CERTIFIED_APPS'));
alter table permission_audit add column tre_training_access varchar(255) check (tre_training_access in ('ENABLED', 'DISABLED'));

-- What a run-metrics request asks for: metrics, or the outputs of a TRE run.
alter table request_run_metrics add column if not exists type varchar(20) not null default 'METRICS';
