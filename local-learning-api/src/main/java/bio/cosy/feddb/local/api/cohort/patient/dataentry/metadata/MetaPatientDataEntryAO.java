package bio.cosy.feddb.local.api.cohort.patient.dataentry.metadata;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MetaPatientDataEntryAO implements PanacheRepository<MetaPatientDataEntryEntity> {
}
