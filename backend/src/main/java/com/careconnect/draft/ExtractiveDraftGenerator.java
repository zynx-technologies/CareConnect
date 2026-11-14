package com.careconnect.draft;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Quotes each note verbatim. It uses no language model, so it needs no external service. */
@Component
@ConditionalOnProperty(name = "careconnect.ai.provider", havingValue = "extractive", matchIfMissing = true)
public class ExtractiveDraftGenerator implements DraftGenerator {

    @Override
    public List<Statement> generate(List<SourceNote> notes) {
        return notes.stream()
                .map(n -> new Statement(n.noteType() + ": " + n.body().strip(), List.of(n.id())))
                .toList();
    }

    @Override
    public String name() { return "extractive-v1"; }
}
