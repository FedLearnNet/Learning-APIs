package bio.cosy.feddb.local.api.cohort.patient.dataentry;


import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;

public class PatientDataEntryHelper {

    public static Object getValue(PatientDataEntryEntity entity, DataTypes type) {
        if (entity == null || type == null) return getValue(entity);
        Object value = switch (type) {
            case DataTypes.STRING, DataTypes.CATEGORICAL -> entity.getValueString();
            case DataTypes.INT -> entity.getValueInt();
            case DataTypes.FLOAT -> entity.getValueFloat();
            case DataTypes.BOOLEAN -> entity.getValueBoolean();
            case DataTypes.FILE -> entity.getValueBlob();
            case DataTypes.DATE -> entity.getValueDate();
            case DataTypes.DATE_TIME -> entity.getValueDateTime();
            default -> null;
        };
        if (value != null) return value;
        return getValue(entity);
    }

    public static Object getValue(PatientDataEntryEntity entity) {
        if (entity == null) return null;
        if (entity.getValueString() != null) return entity.getValueString();
        if (entity.getValueInt() != null) return entity.getValueInt();
        if (entity.getValueFloat() != null) return entity.getValueFloat();
        if (entity.getValueBoolean() != null) return entity.getValueBoolean();
        if (entity.getValueBlob() != null) return entity.getValueBlob();
        if (entity.getValueDate() != null) return entity.getValueDate();
        if (entity.getValueDateTime() != null) return entity.getValueDateTime();
        return null;
    }
}
