package de.unihamburg.daibetes.api.project.training.federated;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Optional;

@ApplicationScoped
public class FlowerPlatformCertificateAO implements PanacheRepository<FlowerPlatformCertificateEntity> {

    /** The platform's stored Flower CA (certificate and key), if its aggregator has reported one yet. */
    public Optional<FlowerPlatformCertificateEntity> findStored() {
        return findAll().firstResultOptional();
    }

    /** Replaces the single stored row (there is only ever one) with the newly reported CA. */
    @Transactional
    public void storeTransactional(String caCertificate, String caPrivateKey) {
        deleteAll();
        FlowerPlatformCertificateEntity entity = new FlowerPlatformCertificateEntity();
        entity.setCaCertificate(caCertificate);
        entity.setCaPrivateKey(caPrivateKey);
        persist(entity);
    }
}
