alter table pipeline
    add column run_mode varchar(32) not null default 'ALL';

alter table pipeline_steps
    drop constraint pipeline_steps_name_check;

alter table pipeline_steps
    add constraint pipeline_steps_name_check check (name in (
        'PREFLIGHT', 'CLONE_REPO', 'BUILD_IMAGE', 'FETCH_CONFIG', 'FETCH_FILES',
        'SAST', 'PULL_IMAGE', 'PUSH_IMAGE', 'SCAN_IMAGE', 'MALWARE_CHECK',
        'RUN_PYTEST', 'COLLECT_SUMMARY'
    ));
