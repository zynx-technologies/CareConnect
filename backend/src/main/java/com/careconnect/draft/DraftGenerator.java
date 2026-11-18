package com.careconnect.draft;

import java.util.List;

/** Produces cited statements from one patient's notes. It has no ability to read or change anything else. */
public interface DraftGenerator {

    record SourceNote(String id, String noteType, String body) {}

    /** One sentence of the draft, the summary section it belongs to, and the IDs of the notes that support it. */
    record Statement(String section, String text, List<String> sourceIds) {}

    List<Statement> generate(List<SourceNote> notes);

    String name();

    /** Maps a note type to a discharge summary section. */
    static String sectionFor(String noteType) {
        String type = noteType == null ? "" : noteType.toLowerCase();
        if (type.contains("pharmac") || type.contains("medic")) return "Medicines";
        if (type.contains("physio") || type.contains("occupational")) return "Function";
        if (type.contains("patholog") || type.contains("imaging") || type.contains("result")) return "Results and follow-up";
        if (type.contains("progress") || type.contains("admission") || type.contains("ward")) return "Hospital course";
        return "Other";
    }
}
