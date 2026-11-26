package bio.cosy.feddb.core.services.orch.dto;

// step of 'flnet upgrade', or BACKUP and RESTORE for 'flnet backup' and 'flnet restore'
public enum MaintenanceMode {
    PREPARE,
    APPLY,
    ROLLBACK,
    BACKUP,
    RESTORE
}
