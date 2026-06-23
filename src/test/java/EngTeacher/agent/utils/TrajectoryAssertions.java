package EngTeacher.agent.utils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Vergleicht die tatsaechlich aufgezeichneten Tool-Aufrufe mit der Erwartung.
 *
 * Strategie:
 *  - Alle Tool-Calls (inkl. getCurrentExercises) muessen in genau der erwarteten
 *    Reihenfolge auftreten.
 *  - Argument-Matcher: Exact-Equals fuer Strings, regex.matches() fuer Pattern.
 */
public final class TrajectoryAssertions {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TrajectoryAssertions() {}

    public static void assertTrajectory(List<ToolCallRecorder.Call> actual,
                                        List<AgentTestCase.ExpectedCall> expected) {

        assertEquals(expected.size(), actual.size(),
                "Anzahl Tool-Calls weicht ab. Erwartet=%s, Actual=%s"
                        .formatted(expected, actual));

        for (int i = 0; i < expected.size(); i++) {
            AgentTestCase.ExpectedCall exp = expected.get(i);
            ToolCallRecorder.Call act = actual.get(i);

            assertEquals(exp.toolName(), act.toolName(),
                    "Tool an Position %d falsch".formatted(i));

            assertArgs(exp.argsMatcher(), act.rawInput(), i);
        }
    }

    public static void assertResponseDoesNotMention(String response, List<String> forbidden) {
        if (forbidden == null || forbidden.isEmpty()) return;
        String haystack = response == null ? "" : response.toLowerCase();
        for (String needle : forbidden) {
            assertTrue(!haystack.contains(needle.toLowerCase()),
                    "Agent response sollte '%s' NICHT erwaehnen, tat es aber: %s"
                            .formatted(needle, response));
        }
    }

    private static void assertArgs(Map<String, Object> matcher, String rawInput, int idx) {
        if (matcher.isEmpty()) return;
        JsonNode json;
        try {
            json = MAPPER.readTree(rawInput);
        } catch (Exception e) {
            fail("Tool-Input #" + idx + " ist kein gueltiges JSON: " + rawInput);
            return;
        }
        matcher.forEach((field, expected) -> {
            JsonNode actualNode = json.get(field);
            assertTrue(actualNode != null && !actualNode.isNull(),
                    "Feld '%s' fehlt im Tool-Input #%d: %s".formatted(field, idx, rawInput));
            String actualValue = actualNode.asString();
            if (expected instanceof Pattern p) {
                assertTrue(p.matcher(actualValue).matches(),
                        "Feld '%s' #%d matcht Regex '%s' nicht: '%s'"
                                .formatted(field, idx, p.pattern(), actualValue));
            } else {
                assertEquals(expected.toString(), actualValue,
                        "Feld '%s' #%d weicht ab".formatted(field, idx));
            }
        });
    }
}
