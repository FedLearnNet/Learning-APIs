package de.unihamburg.daibetes.api.app.tag;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FederatedAppTagAO implements PanacheRepository<FederatedAppTagEntity> {

}
