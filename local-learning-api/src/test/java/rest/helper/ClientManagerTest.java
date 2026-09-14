package rest.helper;


import bio.cosy.feddb.local.api.eam.ClientManager;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Singleton;

@Priority(1)
@Alternative
@Singleton
public class ClientManagerTest extends ClientManager {
    //DO nothing
}
