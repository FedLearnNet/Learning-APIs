-- Tags each Tool with the federated communication engine it bundles (FL-Net's own relay today,
-- Flower for FedLearnNet/Learning-APIs#13); null means FL-Net, the only engine before Flower.
alter table federated_apps
    add column communication_engine varchar(20);
