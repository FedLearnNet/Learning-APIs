package bio.cosy.feddb.core.api.run;


/**
 * Enumeration representing the various connection types in an application context.
 * <p>
 * This enum is used to distinguish between different connection roles, specifically:
 * - {@code APP}: Represents the Docker container or test application running the test,
 * learning, or model process.
 * - {@code CLIENT}: Represents the frontend application that connects to the Docker container.
 * This type is used if asynchronous messages need to be forwarded to the frontend.
 * <p>
 * The enum provides utility methods to evaluate or determine connection types, such as:
 * - Parsing string values into an {@code AppConnectionTypesEnum}.
 * - Determining the opposite connection type.
 */
public enum AppConnectionTypesEnum {
    APP, // The Docker container/or test application that is running the test/learning/model
    CLIENT, // The frontend application that is used to connect to the Docker container. Only needed if you wanna forward async messages to the FE
    CONTROLLER; // the controller for learning


    public static AppConnectionTypesEnum getOpposite(String value) {
        return getAppConnectionTypesEnum(value, CLIENT, APP);
    }

    public static AppConnectionTypesEnum fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }
        if (APP.isEqual(value)) {
            return APP;
        } else if (CLIENT.isEqual(value)) {
            return CLIENT;
        } else if (CONTROLLER.isEqual(value)) {
            return CONTROLLER;
        } else {
            throw new IllegalArgumentException("Unknown value: " + value);
        }
    }


    /**
     * Resolves the appropriate {@code AppConnectionTypesEnum} based on the provided string value.
     * Depending on the value, it returns one of the provided enum constants.
     *
     * @param value the input string representing the connection type. Must not be null.
     * @param e1    the {@code AppConnectionTypesEnum} to return if the input matches the corresponding condition.
     * @param e2    the {@code AppConnectionTypesEnum} to return if the input matches the alternative condition.
     * @return the resolved {@code AppConnectionTypesEnum}, either {@code e1} or {@code e2}, depending on the value.
     * @throws IllegalArgumentException if the value is null or does not match any recognized connection type.
     */
    private static AppConnectionTypesEnum getAppConnectionTypesEnum(String value, AppConnectionTypesEnum e1, AppConnectionTypesEnum e2) {
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }
        if (APP.isEqual(value)) {
            return e1;
        } else if (CLIENT.isEqual(value)) {
            return e2;
        } else {
            throw new IllegalArgumentException("Unknown value: " + value);
        }
    }

    public boolean isEqual(String value) {
        if (value == null) {
            return false;
        }
        return this.name().equalsIgnoreCase(value);
    }
}
