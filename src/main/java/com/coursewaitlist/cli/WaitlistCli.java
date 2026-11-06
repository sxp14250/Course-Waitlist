package com.coursewaitlist.cli;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * <strong>Starting point of the application.</strong>
 *
 * <p>A minimal command-line prototype (the fallback the assessment permits when no
 * starter project is supplied). It seeds one course offering and a handful of demo
 * learners (see {@link DemoData}) into the in-memory store, then reads commands from
 * standard input.
 *
 * <p>Works interactively, or with piped input for repeatable manual tests:
 * {@code java -jar target/course-waitlist-1.0.0.jar < manual-tests/ac1-available-capacity.txt}.
 * When input is piped, each command is echoed after the prompt so the transcript reads
 * like an interactive session. Lines starting with {@code #} are treated as comments and printed.
 */
public final class WaitlistCli {

    private WaitlistCli() {
    }

    /**
     * Runs the read-eval-print loop until {@code quit}/{@code exit} or end of input.
     *
     * @param args unused
     * @throws IOException if standard input cannot be read
     */
    public static void main(String[] args) throws IOException {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        CommandProcessor processor = new CommandProcessor();
        Console console = System.console();
        boolean interactive = console != null && console.isTerminal();

        out.println("CourseWaitlist CLI - offering CS201-FALL-A (capacity 2, prerequisite CS101)");
        out.println("Demo learners: L1, L2, L3, L5, L6 (have CS101), L4 (missing CS101)");
        out.println("Type 'help' for commands.");

        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while (true) {
                out.print("> ");
                out.flush();
                if ((line = in.readLine()) == null) {
                    out.println();
                    break; // end of piped input
                }
                // Windows PowerShell can prefix piped input with a UTF-8 byte-order mark (U+FEFF);
                // strip it so the first command of a script is not misread.
                String trimmed = line.replace("﻿", "").trim();
                if (!interactive) {
                    out.println(trimmed); // echo so piped transcripts are readable
                }
                if (trimmed.startsWith("#")) {
                    continue; // comment line in a test script
                }
                if (trimmed.equalsIgnoreCase("quit") || trimmed.equalsIgnoreCase("exit")) {
                    break;
                }
                String output = processor.execute(trimmed);
                if (!output.isEmpty()) {
                    out.println(output);
                }
            }
        }
        out.println("Bye.");
    }
}
