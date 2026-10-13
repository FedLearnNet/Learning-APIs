-- State of the relay certificate of a step's controller run (mTLS towards the relay server):
-- the CSR, how often and when it was sent to the global server for signing, whether the certificate
-- arrived, and whether the controller run was stopped.
alter table federated_learning_experiments_steps
    add column relay_csr TEXT,
    add column relay_cert_requested_at timestamp(6),
    add column relay_cert_attempts integer,
    add column relay_cert_signed boolean,
    add column relay_stopped boolean;
