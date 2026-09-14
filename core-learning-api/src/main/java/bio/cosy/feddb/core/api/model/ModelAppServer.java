package bio.cosy.feddb.core.api.model;

import bio.cosy.feddb.core.base.BaseAppServer;


/**
 * The ModelAppServer is an abstract class that extends the BaseAppServer class.
 * It provides a foundational framework for applications requiring websocket-based
 * communication to process model-related activities in a containerized environment.
 * <p>
 * This class is designed to support model execution, status updates, logging, error handling,
 * and other application-specific message exchanges related to model workflows. It leverages
 * the functionality provided by its superclass, BaseAppServer, and defines abstract or protected
 * methods to be implemented for use cases involving model operations.
 * <p>
 * It can be implemented in global or local-learning API contexts, allowing for model (trained Ai models) to be executed and connected
 * <p>
 * Common use cases for ModelAppServer include:
 * - Managing websocket events such as onOpen, onClose, and onError for connection handling.
 * - Processing various application messages to control model prediction workflows.
 * - Providing custom implementations to handle specific actions like starting, updating,
 * and finishing model-related tasks.
 */
public abstract class ModelAppServer extends BaseAppServer {


}
