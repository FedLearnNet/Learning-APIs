package bio.cosy.feddb.local.api.statistics;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import bio.cosy.feddb.local.api.cohort.patient.PatientEntity;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.*;

@ApplicationScoped
public class DataStatisticsBO {

    @Inject
    CohortAO cohortAO;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    public LocalDataStatisticsDTO getStatisticsForAll() {
        List<CohortEntity> cohorts = cohortAO.listAll();
        return getStatisticsForCohorts(cohorts);
    }

    public LocalDataStatisticsDTO getStatisticsForAll(String keycloakId) {
        List<CohortEntity> cohorts = cohortAO.listAll().stream()
                .filter(cohort -> cohortMemberAuthBO.isMember(cohort, keycloakId))
                .toList();
        return getStatisticsForCohorts(cohorts);
    }

    private LocalDataStatisticsDTO getStatisticsForCohorts(List<CohortEntity> cohorts) {
        Set<PatientEntity> patients = cohorts.stream()
                .filter(Objects::nonNull)
                .flatMap(cohort -> safeCollection(cohort.getPatients()).stream())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        return getDataStatisticsForPatient(patients);
    }

    public LocalDataStatisticsDTO getStatisticsForCohort(Long id) {
        CohortEntity cohortOptional = cohortAO.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Cohort with id " + id + " not found"));
        return getDataStatisticsForPatient(cohortOptional.getPatients());
    }

    public LocalDataStatisticsDTO getDataStatisticsForPatient(Set<PatientEntity> patients) {
        LocalDataStatisticsDTO statistics = new LocalDataStatisticsDTO();
        if (patients == null || patients.isEmpty()) {
            statistics.setProperties(Collections.emptyList());
            statistics.setCohortIds(Collections.emptyList());
            statistics.setPatientIds(Collections.emptyList());
            return statistics;
        }

        Set<Long> patientIds = new TreeSet<>();
        Set<Long> cohortIds = new TreeSet<>();
        Map<PropertyKey, PropertyAccumulator> propertyAccumulators = new HashMap<>();

        for (PatientEntity patient : patients) {
            if (patient == null) {
                continue;
            }

            if (patient.getId() != null) {
                patientIds.add(patient.getId());
            }
            if (patient.getCohort() != null && patient.getCohort().getId() != null) {
                cohortIds.add(patient.getCohort().getId());
            }

            for (PatientDataEntryEntity entry : safeCollection(patient.getDataEntries())) {
                if (entry == null || entry.getSchemaNode() == null) {
                    continue;
                }

                PropertyKey key = new PropertyKey(entry.getSchemaNode().getId(), entry.getSchemaNode().getName());
                PropertyAccumulator accumulator = propertyAccumulators.computeIfAbsent(
                        key,
                        ignored -> new PropertyAccumulator(
                                entry.getSchemaNode().getId(),
                                entry.getSchemaNode().getName(),
                                mapColumnType(entry.getSchemaNode().getDataType() == null ? null : entry.getSchemaNode().getDataType().getType())
                        )
                );
                accumulator.accept(entry);
            }
        }

        List<ColumnProfile> properties = propertyAccumulators.values().stream()
                .sorted(Comparator
                        .comparing(PropertyAccumulator::schemaNodeId, Comparator.nullsLast(Long::compareTo))
                        .thenComparing(PropertyAccumulator::name, Comparator.nullsLast(String::compareTo)))
                .map(PropertyAccumulator::toProfile)
                .toList();

        statistics.setProperties(properties);
        statistics.setPatientIds(new ArrayList<>(patientIds));
        statistics.setCohortIds(new ArrayList<>(cohortIds));
        return statistics;
    }

    private static <T> Collection<T> safeCollection(Collection<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private static String mapColumnType(DataTypes dataType) {
        if (dataType == null) {
            return "TEXT";
        }
        return switch (dataType) {
            case INT -> "INTEGER";
            case FLOAT -> "NUMBER";
            case BOOLEAN -> "BOOLEAN";
            case DATE, DATE_TIME -> "DATETIME";
            case STRING, FILE, CATEGORICAL -> "TEXT";
        };
    }

}
