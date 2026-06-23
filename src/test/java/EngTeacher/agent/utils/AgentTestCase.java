package EngTeacher.agent.utils;

import EngTeacher.model.Exercise;
import EngTeacher.model.Phrase;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Ein Testfall fuer einen kompletten Mehr-Turn-Dialog.
 *
 * Fixture: was in Mongo liegt, bevor der erste Turn losgeht.
 * Turns:   pro Nutzernachricht eine erwartete Tool-Trajektorie.
 */
public record AgentTestCase(
        String name,
        Fixture fixture,
        List<Turn> turns
) {
    @Override public String toString() { return name; }

    public record Fixture(
            List<Phrase> phrases,
            List<Exercise> exercises
    ) {}

    public record Turn(
            String userMessage,
            List<ExpectedCall> expectedCalls,
            List<String> forbiddenInResponse
    ) {
        public Turn(String userMessage, List<ExpectedCall> expectedCalls) {
            this(userMessage, expectedCalls, List.of());
        }
    }

    /**
     * argsMatcher: pro Feld entweder ein String (exact equals) oder ein Pattern (regex match).
     * Felder, die nicht im Matcher stehen, werden ignoriert.
     */
    public record ExpectedCall(
            String toolName,
            Map<String, Object> argsMatcher
    ) {
        public static ExpectedCall of(String toolName) {
            return new ExpectedCall(toolName, Map.of());
        }
        public static ExpectedCall of(String toolName, Map<String, Object> args) {
            return new ExpectedCall(toolName, args);
        }
        public static Pattern regex(String r) { return Pattern.compile(r); }
    }
}
