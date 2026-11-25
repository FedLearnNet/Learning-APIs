package bio.cosy.feddb.local.api.query.builder;

import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.query.QueryValidationBO;
import bio.cosy.feddb.core.api.query.item.ConnectingOperator;
import bio.cosy.feddb.core.api.query.item.QueryItemDTO;
import bio.cosy.feddb.core.api.query.item.TemporalRelationDTO;
import bio.cosy.feddb.core.api.query.item.TemporalRelationship;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Builds the SQL that finds the patients matching a query tree.
 * QueryValidationBO -> rejects invalid trees
 * QueryOccurrenceBO -> rows matching one condition
 * QueryPredicateBO  -> operator and value as SQL
 * QueryValueHelper  -> value checks and interval text
 */
@ApplicationScoped
public class QueryBuilderBO {

    private static final String EMPTY_PATIENT_SQL = "SELECT DISTINCT patient_id FROM patient_data WHERE 1 = 0";

    @Inject
    QueryValidationBO validationBO;

    @Inject
    QueryOccurrenceBO occurrenceBO;

    /**
     * Patient ids matching the query in the given cohorts.
     */
    public String buildCountDistinctSql(QueryDTO dto, Collection<Long> cohortIds) {
        if (dto == null || validationBO.isEmptyQuery(dto.getQuery())) {
            return EMPTY_PATIENT_SQL;
        }
        QueryItemDTO root = dto.getQuery();
        validationBO.validateTree(root);
        return "SELECT DISTINCT patient_id FROM (" + nodeSql(root, cohortIds) + ") AS query_result";
    }

    private String nodeSql(QueryItemDTO node, Collection<Long> cohortIds) {
        if (node.isGroup() && hasRelations(node)) {
            return relationGroupSql(node, cohortIds);
        }
        if (node.isGroup()) {
            return groupSql(node, cohortIds);
        }
        if (node.hasMatchBounds()) {
            return matchCountSql(node, cohortIds);
        }
        return occurrenceBO.patientsSql(occurrenceBO.occurrencesSql(node, null, false, cohortIds));
    }

    private boolean hasRelations(QueryItemDTO group) {
        return group.getTemporalRelations() != null && !group.getTemporalRelations().isEmpty();
    }

    private String groupSql(QueryItemDTO group, Collection<Long> cohortIds) {
        List<String> clauses = new ArrayList<>();
        for (QueryItemDTO condition : group.getConditions()) {
            clauses.add(nodeSql(condition, cohortIds));
        }
        return setSql(group.getConnectingOperator(), clauses);
    }

    // relations constrain the boolean result; a relation already proves its endpoints, only their bounds are added
    private String relationGroupSql(QueryItemDTO group, Collection<Long> cohortIds) {
        List<QueryItemDTO> conditions = group.getConditions();
        List<String> clauses = new ArrayList<>();
        if (group.getConnectingOperator() == ConnectingOperator.OR) {
            clauses.add(groupSql(group, cohortIds));
        } else {
            for (int i = 0; i < conditions.size(); i++) {
                QueryItemDTO condition = conditions.get(i);
                if (!isEndpoint(group, i)) {
                    clauses.add(nodeSql(condition, cohortIds));
                } else if (condition.hasMatchBounds()) {
                    clauses.add(matchCountSql(condition, cohortIds));
                }
            }
        }
        for (TemporalRelationDTO relation : group.getTemporalRelations()) {
            clauses.add(relationSql(relation, conditions, cohortIds));
        }
        return setSql(ConnectingOperator.AND, clauses);
    }

    private boolean isEndpoint(QueryItemDTO group, int index) {
        for (TemporalRelationDTO relation : group.getTemporalRelations()) {
            if (relation.getSourceCondition() == index || relation.getTargetCondition() == index) {
                return true;
            }
        }
        return false;
    }

    // patients whose source occurrences have atLeast..atMost related target occurrences
    private String relationSql(TemporalRelationDTO relation, List<QueryItemDTO> conditions, Collection<Long> cohortIds) {
        String source = occurrenceBO.occurrencesSql(conditions.get(relation.getSourceCondition()), null, true, cohortIds);
        String scope = null;
        if (relation.getRelationship() == TemporalRelationship.SAME_GROUP) {
            scope = sameGroupScopeSql(source);
        }
        String target = occurrenceBO.occurrencesSql(conditions.get(relation.getTargetCondition()), scope, true, cohortIds);
        String bounds = boundsSql("MAX(relation_count)", relationAtLeast(relation), relation.getAtMost());
        return "(SELECT patient_id FROM (SELECT source.patient_id, source.occurrence_key, "
                + "COUNT(DISTINCT target.occurrence_key) AS relation_count FROM " + source + " source LEFT JOIN " + target
                + " target ON target.patient_id = source.patient_id AND " + relationJoinSql(relation)
                + " GROUP BY source.patient_id, source.occurrence_key) relation_counts GROUP BY patient_id"
                + " HAVING " + bounds + ")";
    }

    // limits target rows to the import groups of the source occurrences
    private String sameGroupScopeSql(String sourceSql) {
        return "scoped.import_schema_group_id IS NOT NULL AND EXISTS (SELECT 1 FROM " + sourceSql + " same_group_source"
                + " WHERE same_group_source.patient_id = scoped.patient_id"
                + " AND same_group_source.import_schema_group_id = scoped.import_schema_group_id)";
    }

    // without bounds a relation needs one related occurrence, atMost alone allows none
    private int relationAtLeast(TemporalRelationDTO relation) {
        if (relation.getAtLeast() != null) {
            return relation.getAtLeast();
        }
        if (relation.getAtMost() != null) {
            return 0;
        }
        return 1;
    }

    private String relationJoinSql(TemporalRelationDTO relation) {
        return switch (relation.getRelationship()) {
            case WITHIN -> "target.visit_timestamp >= source.visit_timestamp - " + intervalSql(relation)
                    + " AND target.visit_timestamp <= source.visit_timestamp + " + intervalSql(relation);
            case IN_THE_FOLLOWING -> "target.visit_timestamp > source.visit_timestamp"
                    + " AND target.visit_timestamp <= source.visit_timestamp + " + intervalSql(relation);
            case SAME_TIME -> "CAST(target.visit_timestamp AT TIME ZONE 'UTC' AS date)"
                    + " = CAST(source.visit_timestamp AT TIME ZONE 'UTC' AS date)";
            case ANYTIME_AFTER -> "target.visit_timestamp > source.visit_timestamp";
            case SAME_VISIT -> "source.visit_id IS NOT NULL AND target.visit_id = source.visit_id";
            case SAME_GROUP -> "source.import_schema_group_id IS NOT NULL"
                    + " AND target.import_schema_group_id = source.import_schema_group_id";
        };
    }

    private String intervalSql(TemporalRelationDTO relation) {
        return "interval '" + QueryValueHelper.intervalSql(relation.getInterval()) + "'";
    }

    // atMost without a minimum includes patients without any match
    private String matchCountSql(QueryItemDTO leaf, Collection<Long> cohortIds) {
        int atLeast = 0;
        if (leaf.getAtLeast() != null) {
            atLeast = leaf.getAtLeast();
        }
        String bounds = boundsSql("COUNT(DISTINCT counted.occurrence_key)", atLeast, leaf.getAtMost());
        return "(SELECT p.id AS patient_id FROM patient_meta p LEFT JOIN "
                + occurrenceBO.occurrencesSql(leaf, null, false, cohortIds) + " counted ON counted.patient_id = p.id"
                + " GROUP BY p.id HAVING " + bounds + ")";
    }

    private String boundsSql(String countSql, int atLeast, Integer atMost) {
        String bounds = countSql + " >= " + atLeast;
        if (atMost != null) {
            bounds += " AND " + countSql + " <= " + atMost;
        }
        return bounds;
    }

    private String setSql(ConnectingOperator operator, List<String> clauses) {
        if (clauses.size() == 1) {
            return clauses.getFirst();
        }
        return "(" + String.join(" " + operator.getSqlSetOperator() + " ", clauses) + ")";
    }
}
