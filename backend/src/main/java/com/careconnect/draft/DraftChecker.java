package com.careconnect.draft;

import com.careconnect.draft.DraftGenerator.SourceNote;
import com.careconnect.draft.DraftGenerator.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Compares each draft statement with its cited notes, so the clinician can see what to check. A passing
 * result is not proof that a statement is correct. It catches missing citations and unsupported numbers.
 */
final class DraftChecker {

    record CheckedStatement(String text, List<String> sourceIds, List<String> warnings) {}

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private DraftChecker() {}

    static List<CheckedStatement> check(List<Statement> statements, List<SourceNote> notes) {
        Map<String, String> bodies = notes.stream().collect(Collectors.toMap(SourceNote::id, SourceNote::body));
        return statements.stream().map(s -> checkOne(s, bodies)).toList();
    }

    private static CheckedStatement checkOne(Statement statement, Map<String, String> bodies) {
        List<String> warnings = new ArrayList<>();
        List<String> valid = statement.sourceIds().stream().filter(bodies::containsKey).toList();
        if (valid.isEmpty()) {
            warnings.add("No valid source cited");
        } else if (valid.size() < statement.sourceIds().size()) {
            warnings.add("Cites a note that does not belong to this patient");
        }
        String cited = valid.stream().map(bodies::get).collect(Collectors.joining(" "));
        List<String> citedNumbers = numbers(cited);
        for (String number : numbers(statement.text())) {
            if (!citedNumbers.contains(number)) {
                warnings.add("Number " + number + " does not appear in the cited notes");
            }
        }
        return new CheckedStatement(statement.text(), valid, warnings);
    }

    private static List<String> numbers(String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }
}
