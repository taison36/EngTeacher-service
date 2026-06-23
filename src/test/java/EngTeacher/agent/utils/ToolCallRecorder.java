package EngTeacher.agent.utils;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hueltet alle Tool-Aufrufe waehrend eines processMessage()-Durchlaufs fest.
 * Wird im TestConfig vor jeden ToolCallback gehaengt; das Original wird danach
 * normal ausgefuehrt, damit Seiteneffekte (Mongo, State) wie in Produktion passieren.
 */
public class ToolCallRecorder {

    public record Call(String toolName, String rawInput) {}

    private final List<Call> calls = new ArrayList<>();

    public void clear() { calls.clear(); }

    public List<Call> calls() { return Collections.unmodifiableList(calls); }

    public ToolCallback wrap(ToolCallback delegate) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return delegate.getToolDefinition();
            }

            @Override
            public String call(String toolInput) {
                calls.add(new Call(getToolDefinition().name(), toolInput));
                return delegate.call(toolInput);
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                calls.add(new Call(getToolDefinition().name(), toolInput));
                return delegate.call(toolInput, toolContext);
            }
        };
    }
}
