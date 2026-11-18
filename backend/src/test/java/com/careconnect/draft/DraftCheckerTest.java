package com.careconnect.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.careconnect.draft.DraftGenerator.SourceNote;
import com.careconnect.draft.DraftGenerator.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

class DraftCheckerTest {

    private final List<SourceNote> notes = List.of(new SourceNote("n1", "Progress", "Doxycycline for 5 more days."));

    private DraftChecker.CheckedStatement check(String text, String... ids) {
        return DraftChecker.check(List.of(new Statement("Hospital course", text, List.of(ids))), notes).getFirst();
    }

    @Test
    void acceptsAStatementSupportedByItsSource() {
        assertThat(check("Continue doxycycline for 5 days.", "n1").warnings()).isEmpty();
    }

    @Test
    void flagsAWrongNumber() {
        assertThat(check("Continue doxycycline for 50 days.", "n1").warnings())
                .containsExactly("Number 50 does not appear in the cited notes");
    }

    @Test
    void flagsMissingAndForeignCitations() {
        assertThat(check("Patient improved.").warnings()).contains("No valid source cited");
        var foreign = check("Patient improved.", "n1", "other-patient-note");
        assertThat(foreign.warnings()).contains("Cites a note that does not belong to this patient");
        assertThat(foreign.sourceIds()).containsExactly("n1");
    }
}
