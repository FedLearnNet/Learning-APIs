package de.unihamburg.daibetes.agent.store;

import de.unihamburg.daibetes.agent.store.bot.StoreBot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

@ApplicationScoped
@Path("test/llm/store")
public class TestStoreServiceImpl {

    @Inject
    StoreBot storeBot;

    @GET
    @Path("chat")
    public String chat(@QueryParam("chat") String chat) {
        return storeBot.chat(chat);
    }
}
