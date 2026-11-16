package eval.agent.etl.gdc.pojo;

public enum GdcVerdict {
    /** Mapped from a column the ground truth names for the field */
    CORRECT,
    /** Mapped, but from other columns than the ground truth names */
    WRONG_COLUMN,
    /** The ground truth names columns, but the field was not mapped */
    MISSED,
    /** Mapped, although the ground truth has no column for the field in this study */
    EXTRA,
    /** Not mapped, and the ground truth has no column for it either */
    EMPTY
}
