package bio.cosy.feddb.core.execution;

public enum ExecutionPurpose {
    ANALYSIS, //For local and global PoSyMed and SiteCoffer
    TRAINING, //For local and global FL-Net
    PROCESSING, //For local and global processing. e.g. ETL
    TEST //For testing an app in the developer's own app engine while it is built
}