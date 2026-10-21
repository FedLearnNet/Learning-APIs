package bio.cosy.feddb.core.execution;

public enum ExecutionPurpose {
    ANALYSIS, //For local and global PoSyMed and SiteCoffer
    TRAINING, //For local and global FL-Net
    PROCESSING //For local and global processing. e.g. ETL
}