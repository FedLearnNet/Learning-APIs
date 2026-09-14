package de.unihamburg.daibetes;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;

@ApplicationScoped
public class Neo4jStartupChecker {

    private static final String SCHEMA_NODE_HASH_CONSTRAINT = "schema_node_hash_unique";

    @ConfigProperty(name = "quarkus.langchain4j.neo4j.dimension")
    int EMBEDDING_DIM;

    @Inject
    Driver driver;

    void onStart(@Observes StartupEvent ev) {
        Log.info("Checking / creating Neo4j indexes at startup ...");

        try (Session session = driver.session()) {
            createVectorIndex(session);
            createSchemaNodeHashConstraint(session);

            awaitIndexes(session);
            logIndexStatus(session);
        } catch (Exception e) {
            Log.warn("Neo4j index initialization encountered an issue", e);
        }
    }

    private void createVectorIndex(Session session) {
        Log.infof("Ensuring VECTOR index 'vector' exists with dimension %d ...", EMBEDDING_DIM);

        session.run("""
                CREATE VECTOR INDEX vector IF NOT EXISTS
                FOR (d:Document) ON (d.embedding)
                OPTIONS {
                  indexConfig: {
                    `vector.dimensions`: $dim,
                    `vector.similarity_function`: 'cosine'
                  }
                }
                """, Values.parameters("dim", EMBEDDING_DIM));
    }

    private void createSchemaNodeHashConstraint(Session session) {
        Log.infof("Ensuring UNIQUE constraint '%s' exists on :SchemaNode(nodeHash) ...",
                SCHEMA_NODE_HASH_CONSTRAINT);

        session.run("""
                CREATE CONSTRAINT %s IF NOT EXISTS
                FOR (n:SchemaNode)
                REQUIRE n.nodeHash IS UNIQUE
                """.formatted(SCHEMA_NODE_HASH_CONSTRAINT));
    }

    private void awaitIndexes(Session session) {
        try {
            Log.info("Waiting for indexes to come online (db.awaitIndexes) ...");
            session.run("CALL db.awaitIndexes(60)");
        } catch (Exception e) {
            Log.warn("db.awaitIndexes did not complete successfully. Continuing startup anyway.", e);
        }
    }

    private void logIndexStatus(Session session) {
        Log.info("Current Neo4j index status:");

        Result result = session.run("""
                SHOW INDEXES
                YIELD name, type, state, populationPercent, failureMessage
                RETURN name, type, state, populationPercent, failureMessage
                ORDER BY name
                """);

        while (result.hasNext()) {
            var r = result.next();
            String name = r.get("name").asString("");
            String type = r.get("type").asString("");
            String state = r.get("state").asString("");
            double population = r.get("populationPercent").asDouble(-1.0);
            String failure = r.get("failureMessage").isNull() ? "" : r.get("failureMessage").asString("");

            Log.infof("Index[name=%s, type=%s, state=%s, population=%.2f%%, failure=%s]",
                    name, type, state, population, failure);
        }
    }
}
