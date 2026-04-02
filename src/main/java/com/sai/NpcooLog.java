/**
 * File: NpcooLog.java
 *
 * Main intent:
 * Defines NpcooLog functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code NpcooLog(...)}:
 *    Purpose: Prevents instantiation of this static log-formatting helper.
 *    Input: none.
 *    Output: none (constructor).
 * 2) {@code tag(...)}:
 *    Purpose: Prepends the standard NPCoo prefix to a log message.
 *    Input: String message.
 *    Output: String.
 */
package com.sai;

public final class NpcooLog {
    private static final String PREFIX = "[NPCoo] ";

    private NpcooLog() {
    }

    public static String tag(String message) {
        return PREFIX + message;
    }
}
