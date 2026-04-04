/**
 * File: AiToolResult.java
 *
 * Main intent:
 * Standard result envelope returned by every AiTools method.
 *
 * Fields:
 *   toolName  – name of the tool that produced this result.
 *   success   – false when the tool could not fulfil the request.
 *   rawOutput – human-readable summary string (mirrors what the command would print in chat).
 *   data      – structured key/value payload for programmatic consumption.
 */
package com.example.examplemod;

import java.util.Collections;
import java.util.Map;

public final class AiToolResult {

    public final String toolName;
    public final boolean success;
    public final String rawOutput;
    public final Map<String, Object> data;

    public AiToolResult(String toolName, boolean success, String rawOutput, Map<String, Object> data) {
        this.toolName  = toolName;
        this.success   = success;
        this.rawOutput = rawOutput != null ? rawOutput : "";
        this.data      = data != null ? Collections.unmodifiableMap(data) : Collections.emptyMap();
    }

    /** Successful result with structured data. */
    public static AiToolResult ok(String toolName, String rawOutput, Map<String, Object> data) {
        return new AiToolResult(toolName, true, rawOutput, data);
    }

    /** Successful result with no extra data. */
    public static AiToolResult ok(String toolName, String rawOutput) {
        return new AiToolResult(toolName, true, rawOutput, Collections.emptyMap());
    }

    /** Failure result — success=false, data is empty. */
    public static AiToolResult fail(String toolName, String reason) {
        return new AiToolResult(toolName, false, reason, Collections.emptyMap());
    }

    @Override
    public String toString() {
        return "AiToolResult{tool=" + toolName + " success=" + success + " raw=\"" + rawOutput + "\"}";
    }
}
