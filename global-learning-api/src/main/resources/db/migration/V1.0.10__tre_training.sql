alter table execution_run drop constraint execution_run_mode_check;
alter table execution_run add constraint execution_run_mode_check check (mode in ('SINGLE', 'TRE', 'FEDERATED'));

alter table query_result add column supported_modes jsonb;
alter table query_result add column connection_id varchar(255);

-- What a run-metrics request asks for: metrics, or the outputs of a TRE run.
alter table run_metrics_request add column if not exists type varchar(20) not null default 'METRICS';
