package com.coursewaitlist.cli;

import com.coursewaitlist.domain.CourseOffering;
import com.coursewaitlist.domain.DropResult;
import com.coursewaitlist.domain.EnrollmentResult;
import com.coursewaitlist.domain.EnrollmentSnapshot;
import com.coursewaitlist.domain.Learner;
import com.coursewaitlist.repository.InMemoryLearnerDirectory;
import com.coursewaitlist.service.CourseEnrollmentService;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Parses one line of CLI input and executes it.
 *
 * <p>Kept separate from {@link WaitlistCli} (which owns stdin/stdout) so command handling
 * can be unit-tested without a console. This class contains <strong>no enrollment
 * rules</strong>: {@code enroll}, {@code drop} and {@code status} delegate straight to
 * {@link CourseEnrollmentService}.
 *
 * <h2>Test-support commands</h2>
 * {@code reset}, {@code grant}, {@code revoke} and {@code learners} exist only so the
 * acceptance criteria can be verified by hand (see {@code docs/MANUAL_TEST_PLAN.md}).
 * {@code grant}/{@code revoke} stand in for the external student-records system changing
 * a transcript; they write to the learner directory, never to roster or waitlist state.
 */
public final class CommandProcessor {

    /** Text printed for the {@code help} command. */
    static final String HELP = """
            Commands:
              enroll <learnerId>            request a seat (waitlisted if full)
              drop <learnerId>              drop a seat or leave the waitlist
              status                        show enrolled learners and waitlist
              learners                      show demo learners and completed courses
              grant <learnerId> <course>    [test support] add a completed course
              revoke <learnerId> <course>   [test support] remove a completed course
              reset                         [test support] restore the initial demo state
              help                          show this help
              quit                          exit""";

    private final Supplier<InMemoryLearnerDirectory> directoryFactory;
    private final CourseOffering offering;

    /** Current directory; replaced on {@code reset}. */
    private InMemoryLearnerDirectory directory;
    /** Current service; replaced on {@code reset}. */
    private CourseEnrollmentService service;

    /**
     * @param directoryFactory creates the initial learner directory (called again on {@code reset})
     * @param offering         the single offering to manage
     */
    public CommandProcessor(Supplier<InMemoryLearnerDirectory> directoryFactory, CourseOffering offering) {
        this.directoryFactory = Objects.requireNonNull(directoryFactory, "directoryFactory must not be null");
        this.offering = Objects.requireNonNull(offering, "offering must not be null");
        reset();
    }

    /** Convenience constructor using {@link DemoData}. */
    public CommandProcessor() {
        this(DemoData::newDirectory, DemoData.OFFERING);
    }

    /**
     * Executes one command line.
     *
     * @param line raw input, e.g. {@code "enroll L1"}
     * @return text to print; never {@code null}
     */
    public String execute(String line) {
        if (line == null || line.isBlank()) {
            return "";
        }
        String[] parts = line.trim().split("\\s+");
        String command = parts[0].toLowerCase(Locale.ROOT);

        return switch (command) {
            case "enroll" -> requireArgs(parts, 1, "<learnerId>").orElseGet(() -> format(service.enroll(parts[1])));
            case "drop" -> requireArgs(parts, 1, "<learnerId>").orElseGet(() -> format(service.drop(parts[1])));
            case "status" -> format(service.snapshot());
            case "learners" -> listLearners();
            case "grant" -> requireArgs(parts, 2, "<learnerId> <course>")
                    .orElseGet(() -> changeTranscript(parts[1], parts[2], true));
            case "revoke" -> requireArgs(parts, 2, "<learnerId> <course>")
                    .orElseGet(() -> changeTranscript(parts[1], parts[2], false));
            case "reset" -> {
                reset();
                yield "[RESET] Demo state restored: " + offering.offeringId() + " is empty.";
            }
            case "help" -> HELP;
            default -> "Unknown command '" + parts[0] + "'. Type 'help' for usage.";
        };
    }

    // ---- test-support helpers -----------------------------------------------------------

    /** Rebuilds directory and service from scratch (fresh roster, waitlist and transcripts). */
    private void reset() {
        directory = directoryFactory.get();
        service = new CourseEnrollmentService(offering, directory);
    }

    /** Adds or removes one completed course on a learner's record. */
    private String changeTranscript(String learnerId, String course, boolean add) {
        Optional<Learner> existing = directory.findById(learnerId);
        if (existing.isEmpty()) {
            return "No learner with id " + learnerId + ".";
        }
        Learner learner = existing.get();
        Set<String> courses = new HashSet<>(learner.completedCourses());
        boolean changed = add ? courses.add(course) : courses.remove(course);
        directory.save(new Learner(learner.id(), learner.name(), courses));
        String verb = add ? "granted" : "revoked";
        return changed
                ? "[TRANSCRIPT] " + course + " " + verb + " for " + learnerId + "; now " + new TreeSet<>(courses) + "."
                : "[TRANSCRIPT] No change for " + learnerId + "; now " + new TreeSet<>(courses) + ".";
    }

    /** Lists every learner in the directory (sorted by id) with eligibility for the offering. */
    private String listLearners() {
        StringBuilder out = new StringBuilder("Learners (prerequisites: ").append(offering.prerequisites()).append(')');
        for (Learner l : directory.findAll()) {
            out.append(System.lineSeparator())
                    .append("  %-3s %-8s %-18s %s".formatted(l.id(), l.name(),
                            new TreeSet<>(l.completedCourses()),
                            offering.isEligible(l) ? "eligible" : "NOT eligible"));
        }
        return out.toString();
    }

    // ---- parsing / formatting -----------------------------------------------------------

    /** @return a usage message unless the command has exactly {@code expected} arguments */
    private static Optional<String> requireArgs(String[] parts, int expected, String usage) {
        return parts.length == expected + 1
                ? Optional.empty()
                : Optional.of("Usage: " + parts[0] + " " + usage);
    }

    private static String format(EnrollmentResult result) {
        return "[" + result.code() + "] " + result.message();
    }

    private static String format(DropResult result) {
        return "[" + result.code() + "] " + result.message();
    }

    private static String format(EnrollmentSnapshot snapshot) {
        return """
                Offering : %s
                Seats    : %d/%d taken (%d open)
                Enrolled : %s
                Waitlist : %s""".formatted(
                snapshot.offeringId(),
                snapshot.enrolled().size(), snapshot.capacity(), snapshot.availableSeats(),
                snapshot.enrolled(), snapshot.waitlist());
    }
}
