package com.coursewaitlist.cli;

import com.coursewaitlist.domain.Learner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Thin tests for CLI parsing; business behaviour is covered by the service tests. */
@DisplayName("CommandProcessor")
class CommandProcessorTest {

    private CommandProcessor cli;

    @BeforeEach
    void setUp() {
        cli = new CommandProcessor();
    }

    @Test
    @DisplayName("end-to-end scripted scenario on the seeded offering")
    void scriptedScenario() {
        assertTrue(cli.execute("enroll L1").startsWith("[ENROLLED]"));
        assertTrue(cli.execute("ENROLL L2").startsWith("[ENROLLED]"));
        assertTrue(cli.execute("enroll L3").startsWith("[WAITLISTED]"));
        assertTrue(cli.execute("enroll L4").startsWith("[PREREQUISITE_NOT_MET]"));
        assertTrue(cli.execute("drop L1").contains("Promoted L3"));
        assertTrue(cli.execute("drop L1").startsWith("[NOT_REGISTERED]"));
        assertTrue(cli.execute("status").contains("Enrolled : [L2, L3]"));
    }

    @Test
    @DisplayName("revoke while waiting causes skip-and-remove on promotion")
    void revokeDrivesSkipPolicy() {
        cli.execute("enroll L1");
        cli.execute("enroll L2");
        cli.execute("enroll L3");
        cli.execute("enroll L5");

        assertTrue(cli.execute("revoke L3 CS101").startsWith("[TRANSCRIPT] CS101 revoked for L3"));
        String drop = cli.execute("drop L1");

        assertTrue(drop.contains("Removed ineligible from waitlist: [L3]"), drop);
        assertTrue(drop.contains("Promoted L5"), drop);
        assertTrue(cli.execute("status").contains("Waitlist : []"));
    }

    @Test
    @DisplayName("grant makes an ineligible learner eligible")
    void grantEnablesEnrollment() {
        assertTrue(cli.execute("enroll L4").startsWith("[PREREQUISITE_NOT_MET]"));
        cli.execute("grant L4 CS101");
        assertTrue(cli.execute("enroll L4").startsWith("[ENROLLED]"));
    }

    @Test
    @DisplayName("reset restores roster, waitlist and transcripts")
    void resetRestoresInitialState() {
        cli.execute("enroll L1");
        cli.execute("revoke L2 CS101");

        assertTrue(cli.execute("reset").startsWith("[RESET]"));

        assertTrue(cli.execute("status").contains("Enrolled : []"));
        assertTrue(cli.execute("enroll L2").startsWith("[ENROLLED]"));
    }

    @Test
    @DisplayName("learners command lists every learner in the directory, not just L1-L9")
    void learnersListsWholeDirectory() {
        var custom = new CommandProcessor(
                () -> DemoData.newDirectory().save(Learner.of("X100", "Extra", "CS101")),
                DemoData.OFFERING);

        assertTrue(custom.execute("learners").contains("X100"));
    }

    @Test
    @DisplayName("missing or extra arguments produce usage text")
    void usageErrors() {
        assertEquals("Usage: enroll <learnerId>", cli.execute("enroll"));
        assertEquals("Usage: drop <learnerId>", cli.execute("drop a b"));
        assertEquals("Usage: grant <learnerId> <course>", cli.execute("grant L1"));
        assertEquals("No learner with id GHOST.", cli.execute("revoke GHOST CS101"));
    }

    @Test
    @DisplayName("unknown command and blank input are handled")
    void unknownAndBlank() {
        assertTrue(cli.execute("register L1").startsWith("Unknown command"));
        assertEquals("", cli.execute("   "));
        assertEquals(CommandProcessor.HELP, cli.execute("help"));
        assertTrue(cli.execute("learners").contains("L4  Alan"));
    }
}
