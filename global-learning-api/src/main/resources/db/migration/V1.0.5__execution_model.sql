-- Execution model: runs with steps and attempts (execution_*) replace the tables of experiments, test runs,
-- federated test runs, project trainings and data analysis runs. Those stay as legacy_* with their data, not
-- converted; the links of model versions, sub models, metrics requests and data analysis files to them move to
-- legacy_* columns and tables.

-- Foreign keys of and to the replaced tables
alter table data_analysis_chat_message drop constraint fkpxr8lo52kv0dpomd5r8t3difc;
alter table data_analysis_chat_human_in_the_loop_request drop constraint fkeqn6s04q6r79o579a2qhkafx9;
alter table test_runs drop constraint fkbp3arl9wwkud22omj1n0tduj9;
alter table test_run_messages drop constraint fkg92c2cnwar2000uhlpsu5ln73;
alter table run_metrics_request drop constraint fknsr6rutb0rnc566as4m16gawv;
alter table project_local_experiments_steps drop constraint fkfsvk2k2r9yswfg5k0bhexowhq;
alter table project_local_experiments_steps drop constraint fkfqdx6t3qt5vn0ut3unbiu9ns7;
alter table project_local_experiments_step_results drop constraint fkkbyyf9xcmu5pqntwmcfhga3i3;
alter table project_local_experiments_step_results drop constraint fkauo3xv6sa64sreph9w131sede;
alter table project_local_experiments_step_results drop constraint fka212jwdcyanrq3hfkrc61qyqd;
alter table project_local_experiments_step_messages drop constraint fk4g91v8kp7ll287eulisp5yanq;
alter table project_local_experiments drop constraint fkiete81gn1dfj1lq3evhq619ma;
alter table project_local_experiments drop constraint fk4f772ptbb2s6572m2exh05hcg;
alter table project_federated_experiments_steps drop constraint fklhhakq0u9xg8gvg73qlx8yxq4;
alter table project_federated_experiments_steps drop constraint fkj6aqgiaw3o8w6gg183rvm9cna;
alter table project_federated_experiments_run_metrics drop constraint fk2mjf2nlcwggicvoydonwu28jy;
alter table project_federated_experiments_participants drop constraint fkldmi4ecw7lwl5if2s5acjf4d9;
alter table project_federated_experiments drop constraint fkoywncrvhcvygk86ofve1jn45y;
alter table project_federated_experiments drop constraint fkforlmea3kujhdppfdoin2nei0;
alter table project_federated_experiments drop constraint fkchwcph3789tur4swjai95jyrd;
alter table project_federated_experiments drop constraint fk27ufydjqo8hsjuemje6v158kb;
alter table model_versions drop constraint fksaneeiunlr0wq0eff4g6iqhd;
alter table model_versions drop constraint fkc0pxapy66b0rynwmi4rb2g8nk;
alter table model_subs drop constraint fki3538y5fl9f5sn66grl5lvtgv;
alter table model_subs drop constraint fkdsln2tmd256oi12lmdlwx5y0u;
alter table federated_test_runs drop constraint fkv4184jl4b2krqkts2ltaveer;
alter table federated_run_participants drop constraint fkt65ryd3r31upj1a25q4wy6a7y;
alter table federated_round_messages drop constraint fkougncrbuscaiahr7d0b8pomqp;
alter table federated_participant_messages drop constraint fkone5hvslxryqratu0ge4e39ua;
alter table experiments_runs drop constraint fkkqgl9r2v70btrxcurwrj1ae2u;
alter table experiments_run_messages drop constraint fk1msia94o2en8yjhf54xt6ieg8;
alter table experiments drop constraint fk8v47lyov8s1fwq2kmst07gq5;
alter table data_analysis_workflow_runs drop constraint fkigudjswekuudbxyd0jk0dvqkw;
alter table data_analysis_workflow_runs drop constraint fkb8gycso5w9mrhw15pcexw9cbq;
alter table data_analysis_workflow_runs drop constraint fkaa9ddpy7fechhloou22s9aoxk;
alter table data_analysis_workflow_run_steps drop constraint fklelic5fes4guoiiqxufp91m5d;
alter table data_analysis_workflow_run_steps drop constraint fk1166dy5494pmkfpxq1vdrwmkm;
alter table data_analysis_workflow_run_step_messages drop constraint fktlrjr9aa8nq4snwknr64oo8xu;
alter table data_analysis_workflow_run_step_messages drop constraint fkiuawqs53vjibbleromjl0xury;
alter table data_analysis_predictions drop constraint fkmb3qw9r3rfxx2vbc3p0yfolfe;
alter table data_analysis_predictions drop constraint fk6st2gsp2ew387qto0j2bq14kk;
alter table data_analysis_predictions drop constraint fk6budiy1kssk0ycqm5ajy9erjg;
alter table data_analysis_files drop constraint fkn49odm23bve50nxdlwna78xx1;
alter table data_analysis_files drop constraint fkjtp21xmhqbpd02ryvrgv62g9i;

-- The replaced tables, kept with their data
alter table data_analysis_predictions rename to legacy_data_analysis_predictions;
alter table data_analysis_workflow_run_step_messages rename to legacy_data_analysis_workflow_run_step_messages;
alter table data_analysis_workflow_run_steps rename to legacy_data_analysis_workflow_run_steps;
alter table data_analysis_workflow_runs rename to legacy_data_analysis_workflow_runs;
alter table experiments rename to legacy_experiments;
alter table experiments_run_messages rename to legacy_experiments_run_messages;
alter table experiments_runs rename to legacy_experiments_runs;
alter table federated_participant_messages rename to legacy_federated_participant_messages;
alter table federated_round_messages rename to legacy_federated_round_messages;
alter table federated_run_participants rename to legacy_federated_run_participants;
alter table federated_test_runs rename to legacy_federated_test_runs;
alter table project_federated_experiments rename to legacy_project_federated_experiments;
alter table project_federated_experiments_participants rename to legacy_project_federated_experiments_participants;
alter table project_federated_experiments_run_metrics rename to legacy_project_federated_experiments_run_metrics;
alter table project_federated_experiments_steps rename to legacy_project_federated_experiments_steps;
alter table project_local_experiments rename to legacy_project_local_experiments;
alter table project_local_experiments_step_messages rename to legacy_project_local_experiments_step_messages;
alter table project_local_experiments_step_results rename to legacy_project_local_experiments_step_results;
alter table project_local_experiments_steps rename to legacy_project_local_experiments_steps;
alter table test_run_messages rename to legacy_test_run_messages;
alter table test_runs rename to legacy_test_runs;

-- Links to the replaced runs
alter table model_subs
    add column legacy_experiment_run_id bigint,
    add column legacy_federated_experiment_id bigint;
update model_subs
set legacy_experiment_run_id = experiment_run_id, legacy_federated_experiment_id = federated_experiment_id,
    experiment_run_id = null, federated_experiment_id = null;

alter table model_versions
    add column legacy_experiment_id bigint,
    add column legacy_federated_experiment_id bigint;
update model_versions
set legacy_experiment_id = experiment_id, legacy_federated_experiment_id = federated_experiment_id,
    experiment_id = null, federated_experiment_id = null;

-- Metrics requests of the replaced trainings
create table legacy_run_metrics_request as select * from run_metrics_request;
create table legacy_run_metrics_response as select * from run_metrics_response;
delete from run_metrics_response;
delete from run_metrics_request;

-- The execution model
create table execution_attempt (
    exit_code integer,
    progress real,
    trial_index integer not null,
    created_at timestamp(6) without time zone not null,
    finished_at timestamp(6) with time zone,
    id bigint generated by default as identity not null,
    last_log_message_id bigint,
    score_message_id bigint,
    started_at timestamp(6) with time zone,
    step_id bigint not null,
    updated_at timestamp(6) without time zone not null,
    version bigint not null default 0,
    public_id uuid not null,
    container_id character varying(255),
    result text,
    status character varying(255) not null,
    failure jsonb,
    meta jsonb,
    params jsonb
);
create table execution_experiment (
    is_test boolean not null,
    model_can_be_public boolean,
    model_need_to_be_public boolean,
    acceptance_clinic_count bigint,
    acceptance_count bigint,
    app_version_id bigint,
    created_at timestamp(6) without time zone not null,
    id bigint generated by default as identity not null,
    project_id bigint,
    updated_at timestamp(6) without time zone not null,
    version bigint not null default 0,
    workflow_id bigint,
    channel_id character varying(64),
    description text,
    global_unique_id character varying(255),
    keycloak_id character varying(255),
    name character varying(255) not null,
    relay_server_address character varying(255),
    status character varying(255),
    diagram_configs jsonb,
    project_version jsonb
);
create table execution_federated_attempt (
    current_round integer,
    messages_received integer,
    messages_sent integer,
    attempt_id bigint not null,
    created_at timestamp(6) without time zone not null,
    id bigint generated by default as identity not null,
    participant_id bigint,
    updated_at timestamp(6) without time zone not null,
    version bigint not null default 0,
    waiting_for jsonb
);
create table execution_federation (
    current_round integer,
    relay_stopped boolean,
    start_aggregator boolean,
    total_rounds integer,
    created_at timestamp(6) without time zone not null,
    id bigint generated by default as identity not null,
    step_id bigint not null,
    updated_at timestamp(6) without time zone not null,
    version bigint not null default 0,
    config jsonb,
    relay_client_ids jsonb
);
create table execution_file (
    attempt_id bigint not null,
    created_at timestamp(6) without time zone not null,
    file_id bigint not null,
    id bigint generated by default as identity not null,
    updated_at timestamp(6) without time zone not null,
    version bigint not null default 0,
    direction character varying(255) not null,
    name character varying(255) not null
);
create table execution_message (
    attempt_id bigint not null,
    created_at timestamp(6) without time zone not null,
    id bigint generated by default as identity not null,
    updated_at timestamp(6) without time zone not null,
    version bigint not null default 0,
    process character varying(255),
    type character varying(255) not null,
    message jsonb not null
);
create table execution_participant (
    coordinator boolean not null,
    model_can_be_public boolean,
    created_at timestamp(6) without time zone not null,
    id bigint generated by default as identity not null,
    run_id bigint not null,
    updated_at timestamp(6) without time zone not null,
    version bigint not null default 0,
    current_node_id character varying(255),
    participant_id character varying(255) not null,
    role character varying(255),
    status character varying(255),
    step_status character varying(255),
    input_file_paths jsonb
);
create table execution_run (
    created_at timestamp(6) without time zone not null,
    experiment_id bigint,
    finished_at timestamp(6) with time zone,
    id bigint generated by default as identity not null,
    started_at timestamp(6) with time zone,
    test_engine_id bigint,
    updated_at timestamp(6) without time zone not null,
    version bigint not null default 0,
    workflow_id bigint,
    public_id uuid not null,
    engine_mode character varying(32),
    keycloak_id character varying(255),
    location character varying(255) not null,
    mode character varying(255) not null,
    name character varying(255),
    purpose character varying(255) not null,
    status character varying(255) not null,
    failure jsonb
);
create table execution_run_step (
    app_version_id bigint,
    created_at timestamp(6) without time zone not null,
    finished_at timestamp(6) with time zone,
    id bigint generated by default as identity not null,
    run_id bigint not null,
    selected_attempt_id bigint,
    started_at timestamp(6) with time zone,
    sub_model_id bigint,
    updated_at timestamp(6) without time zone not null,
    version bigint not null default 0,
    workflow_node_id bigint,
    public_id uuid not null,
    name character varying(255),
    status character varying(255) not null,
    failure jsonb,
    input_file_paths jsonb,
    inputs jsonb,
    search jsonb
);

alter table data_analysis_files rename column prediction_id to legacy_prediction_id;
alter table data_analysis_files rename column workflow_step_id to legacy_workflow_step_id;
alter table data_analysis_files add column attempt_id bigint;

create unique index execution_attempt_pkey on execution_attempt (id);
create unique index execution_attempt_public_id_key on execution_attempt (public_id);
create unique index execution_attempt_step_id_trial_index_key on execution_attempt (step_id, trial_index);
create unique index execution_experiment_global_unique_id_key on execution_experiment (global_unique_id);
create unique index execution_experiment_pkey on execution_experiment (id);
create unique index execution_federated_attempt_attempt_id_key on execution_federated_attempt (attempt_id);
create unique index execution_federated_attempt_pkey on execution_federated_attempt (id);
create unique index execution_federation_pkey on execution_federation (id);
create unique index execution_federation_step_id_key on execution_federation (step_id);
create unique index execution_file_pkey on execution_file (id);
create unique index execution_message_pkey on execution_message (id);
create unique index execution_participant_pkey on execution_participant (id);
create unique index execution_participant_run_id_participant_id_key on execution_participant (run_id, participant_id);
create unique index execution_run_pkey on execution_run (id);
create unique index execution_run_public_id_key on execution_run (public_id);
create unique index execution_run_step_pkey on execution_run_step (id);
create unique index execution_run_step_public_id_key on execution_run_step (public_id);
create unique index execution_run_step_run_id_workflow_node_id_key on execution_run_step (run_id, workflow_node_id);

alter table execution_attempt add constraint execution_attempt_pkey PRIMARY KEY using index execution_attempt_pkey;
alter table execution_experiment add constraint execution_experiment_pkey PRIMARY KEY using index execution_experiment_pkey;
alter table execution_federated_attempt add constraint execution_federated_attempt_pkey PRIMARY KEY using index execution_federated_attempt_pkey;
alter table execution_federation add constraint execution_federation_pkey PRIMARY KEY using index execution_federation_pkey;
alter table execution_file add constraint execution_file_pkey PRIMARY KEY using index execution_file_pkey;
alter table execution_message add constraint execution_message_pkey PRIMARY KEY using index execution_message_pkey;
alter table execution_participant add constraint execution_participant_pkey PRIMARY KEY using index execution_participant_pkey;
alter table execution_run add constraint execution_run_pkey PRIMARY KEY using index execution_run_pkey;
alter table execution_run_step add constraint execution_run_step_pkey PRIMARY KEY using index execution_run_step_pkey;
alter table data_analysis_files add constraint fki86f6hyotfoajll3mmie9gd52 FOREIGN KEY (attempt_id) REFERENCES execution_attempt(id) on DELETE CASCADE;
alter table execution_attempt add constraint execution_attempt_public_id_key UNIQUE using index execution_attempt_public_id_key;
alter table execution_attempt add constraint execution_attempt_status_check check (status in ('PENDING', 'INITIALIZED', 'STARTED', 'RUNNING', 'FINISHED', 'STOPPED', 'ERROR'));
alter table execution_attempt add constraint execution_attempt_step_id_trial_index_key UNIQUE using index execution_attempt_step_id_trial_index_key;
alter table execution_attempt add constraint fk2ll8sgoysijc8jby9foktaa7e FOREIGN KEY (score_message_id) REFERENCES execution_message(id);
alter table execution_attempt add constraint fkk5frq7p0spw7rw1pli331d2i3 FOREIGN KEY (step_id) REFERENCES execution_run_step(id) on DELETE CASCADE;
alter table execution_attempt add constraint fksilf1tpe3gkpwbukg2yrurd5i FOREIGN KEY (last_log_message_id) REFERENCES execution_message(id);
alter table execution_experiment add constraint execution_experiment_global_unique_id_key UNIQUE using index execution_experiment_global_unique_id_key;
alter table execution_experiment add constraint execution_experiment_status_check check (status in ('INIT', 'READY', 'PREPARE', 'RUNNING', 'SHUTDOWN', 'STOPPED', 'ERROR', 'FINISHED'));
alter table execution_experiment add constraint fk6tyme9hg1ysumfpg08dy8i0yp FOREIGN KEY (workflow_id) REFERENCES workflow(id);
alter table execution_experiment add constraint fkg33thb4mo1a94t68pxuh9t2eo FOREIGN KEY (app_version_id) REFERENCES federated_app_versions(id);
alter table execution_experiment add constraint fkoafnlhjqv5jk15nsuqjn1lxrx FOREIGN KEY (project_id) REFERENCES projects(id);
alter table execution_federated_attempt add constraint execution_federated_attempt_attempt_id_key UNIQUE using index execution_federated_attempt_attempt_id_key;
alter table execution_federated_attempt add constraint fkk881vd9lgr9jpkr1tr3ghr57h FOREIGN KEY (participant_id) REFERENCES execution_participant(id);
alter table execution_federated_attempt add constraint fkt0r7kgjqd2icaylt7083xs153 FOREIGN KEY (attempt_id) REFERENCES execution_attempt(id) on DELETE CASCADE;
alter table execution_federation add constraint execution_federation_step_id_key UNIQUE using index execution_federation_step_id_key;
alter table execution_federation add constraint fkf3wegb3os6kcayh2noptka6fx FOREIGN KEY (step_id) REFERENCES execution_run_step(id) on DELETE CASCADE;
alter table execution_file add constraint execution_file_direction_check check (direction in ('INPUT', 'OUTPUT'));
alter table execution_file add constraint fkea3ij3i0ki2mxwif3wtj27meh FOREIGN KEY (file_id) REFERENCES files(id) on DELETE CASCADE;
alter table execution_file add constraint fkr0n5k892odnbb02dt01dm89gf FOREIGN KEY (attempt_id) REFERENCES execution_attempt(id) on DELETE CASCADE;
alter table execution_message add constraint execution_message_type_check check (type in ('METRIC', 'LOG', 'ROUND'));
alter table execution_message add constraint fk9gf24mp94w6820s3bvfhuda9r FOREIGN KEY (attempt_id) REFERENCES execution_attempt(id) on DELETE CASCADE;
alter table execution_participant add constraint execution_participant_role_check check (role in ('CLIENT', 'AGGREGATOR'));
alter table execution_participant add constraint execution_participant_run_id_participant_id_key UNIQUE using index execution_participant_run_id_participant_id_key;
alter table execution_participant add constraint execution_participant_status_check check (status in ('PENDING', 'INITIALIZED', 'STARTED', 'RUNNING', 'FINISHED', 'STOPPED', 'ERROR'));
alter table execution_participant add constraint execution_participant_step_status_check check (step_status in ('INIT', 'READY', 'PREPARE', 'RUNNING', 'SHUTDOWN', 'STOPPED', 'ERROR', 'FINISHED'));
alter table execution_participant add constraint fkok9v9xvga4ig23nd8eem7cd7l FOREIGN KEY (run_id) REFERENCES execution_run(id) on DELETE CASCADE;
alter table execution_run add constraint execution_run_engine_mode_check check (engine_mode in ('ENGINE', 'SELF_TEST', 'SINGLE_RUN'));
alter table execution_run add constraint execution_run_location_check check (location in ('LOCAL', 'PLATFORM'));
alter table execution_run add constraint execution_run_mode_check check (mode in ('SINGLE', 'FEDERATED'));
alter table execution_run add constraint execution_run_public_id_key UNIQUE using index execution_run_public_id_key;
alter table execution_run add constraint execution_run_purpose_check check (purpose in ('ANALYSIS', 'TRAINING', 'PROCESSING', 'TEST', 'TEST_HOSTING'));
alter table execution_run add constraint execution_run_status_check check (status in ('PENDING', 'INITIALIZED', 'STARTED', 'RUNNING', 'FINISHED', 'STOPPED', 'ERROR'));
alter table execution_run add constraint fk3rnpigw4rqpehcmg2m0cdkjl0 FOREIGN KEY (workflow_id) REFERENCES data_analysis(id) on DELETE CASCADE;
alter table execution_run add constraint fk76ko3xskaf1257jufn3gtvnhi FOREIGN KEY (experiment_id) REFERENCES execution_experiment(id);
alter table execution_run add constraint fkp7qllgjk6ghmscu7emo93xbbb FOREIGN KEY (test_engine_id) REFERENCES execution_run(id) on DELETE SET NULL;
alter table execution_run_step add constraint execution_run_step_public_id_key UNIQUE using index execution_run_step_public_id_key;
alter table execution_run_step add constraint execution_run_step_run_id_workflow_node_id_key UNIQUE using index execution_run_step_run_id_workflow_node_id_key;
alter table execution_run_step add constraint execution_run_step_status_check check (status in ('PENDING', 'INITIALIZED', 'STARTED', 'RUNNING', 'FINISHED', 'STOPPED', 'ERROR'));
alter table execution_run_step add constraint fk328hlwc2i3kqx0q9tad5swqo0 FOREIGN KEY (app_version_id) REFERENCES federated_app_versions(id);
alter table execution_run_step add constraint fk7wntpccdd4mgoor11pvu0vvn7 FOREIGN KEY (selected_attempt_id) REFERENCES execution_attempt(id) on DELETE SET NULL;
alter table execution_run_step add constraint fkrtyx2n042wrwe251vuu1ut7r5 FOREIGN KEY (run_id) REFERENCES execution_run(id) on DELETE CASCADE;
alter table execution_run_step add constraint fkryxoe8mnjk9bo44gdjxf49qjq FOREIGN KEY (sub_model_id) REFERENCES model_subs(id);
alter table execution_run_step add constraint fktlhkg0io4jtwlkgndth9wjvof FOREIGN KEY (workflow_node_id) REFERENCES workflow_node(id);
alter table model_subs add constraint fk7l527434g9amwhqowfeu5yt0k FOREIGN KEY (experiment_run_id) REFERENCES execution_attempt(id);
alter table model_subs add constraint fke2e1iiyxjqbaqep7rs67h2iyh FOREIGN KEY (federated_experiment_id) REFERENCES execution_experiment(id);
alter table model_versions add constraint fknf1qnw9kf4bn0tqmwp1jy04jo FOREIGN KEY (federated_experiment_id) REFERENCES execution_experiment(id);
alter table model_versions add constraint fkrfueuhmnqv1ki3cix8nihdpid FOREIGN KEY (experiment_id) REFERENCES execution_experiment(id);
alter table run_metrics_request add constraint fkods2t5ud46l0icjv1nac79a6h FOREIGN KEY (experiment_id) REFERENCES execution_experiment(id);
alter table data_analysis_chat_human_in_the_loop_request add constraint fkeqn6s04q6r79o579a2qhkafx9 FOREIGN KEY (message_id) REFERENCES data_analysis_chat_message(id) on DELETE CASCADE;
alter table data_analysis_chat_message add constraint fkpxr8lo52kv0dpomd5r8t3difc FOREIGN KEY (workflow_id) REFERENCES data_analysis(id) on DELETE CASCADE;

