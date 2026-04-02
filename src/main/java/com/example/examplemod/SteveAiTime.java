/**
 * File: SteveAiTime.java
 *
 * Main intent:
 * Defines SteveAiTime functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code SteveAiTime(...)}:
 *    Purpose: Constructs SteveAiTime.
 *    Input: none.
 *    Output: none (constructor).
 * 2) {@code scanTs(...)}:
 *    Purpose: Scans scan ts.
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
