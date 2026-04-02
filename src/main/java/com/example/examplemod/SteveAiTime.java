/**
 * File: SteveAiTime.java
 *
 * Main intent:
 * Defines SteveAiTime functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code {}(...)}:
 *    Purpose: Implements {} logic in this file.
 *    Input: none.
 *    Output: SteveAiTime() {}.
 * 2) {@code scanTs(...)}:
 *    Purpose: Implements scanTs logic in this file.
 *    Input: none.
 *    Output: String.
 */
package com.example.examplemod;

final class SteveAiTime {

    private SteveAiTime() {}

    static String scanTs() {
        return java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    }
}
