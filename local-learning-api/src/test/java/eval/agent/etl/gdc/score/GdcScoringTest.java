package eval.agent.etl.gdc.score;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** OMOP tables share fields like person_id, so the Synthea benchmark names a field with its table. */
class GdcScoringTest {

    @Test
    void namesTheFieldOfAMappingPath() {
        assertEquals("age_at_diagnosis", GdcScoring.field("GDC.age_at_diagnosis", false));
    }

    @Test
    void namesTheFieldWithItsTableWhenQualified() {
        assertEquals("person.person_id", GdcScoring.field("person.person_id", true));
        assertEquals("visit_occurrence.person_id", GdcScoring.field("2026-10synthea_omop_schema.visit_occurrence.person_id", true));
    }
}
