package com.careconnect.draft;

import java.util.List;

/** Produces cited statements from one patient's notes. It has no ability to read or change anything else. */
public interface DraftGenerator {

    record SourceNote(String id, String noteType, String body) {}

    /** One sentence of the draft, and the IDs of the source notes that support it. */
    record Statement(String text, List<String> sourceIds) {}

    List<Statement> generate(List<SourceNote> notes);

    String name();
}
