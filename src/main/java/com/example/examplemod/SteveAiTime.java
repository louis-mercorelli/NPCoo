/**
 * File: SteveAiTime.java
 *
 * Main intent:
 * Defines SteveAiTime functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code SteveAiTime(...)}:
 *    Purpose: Prevents instantiation of this static time-formatting helper.
 *    Input: none.
 *    Output: none (constructor).
 * 2) {@code scanTs(...)}:
 *    Purpose: Returns the current timestamp formatted for scan logs and output filenames.
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
