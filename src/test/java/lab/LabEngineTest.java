package lab;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lightweight test runner that needs no external test framework. */
public final class LabEngineTest {
    private LabEngineTest() {
    }

    public static void main(String[] args) throws Exception {
        testCatalogIntegrity();
        testDiagnosticReports();
        testAnswerEvaluation();
        testProgressPersistence();
        System.out.println("All Language-Agnostic Debugging Lab tests passed.");
    }

    private static void testCatalogIntegrity() {
        List<BugScenario> scenarios = ScenarioCatalog.create();
        assertEquals(6, scenarios.size(), "Six learning scenarios should be available");

        Set<String> ids = new HashSet<>();
        for (BugScenario scenario : scenarios) {
            assertTrue(ids.add(scenario.id()), "Scenario IDs must be unique: " + scenario.id());
            assertTrue(!scenario.hints().isEmpty(), "Each scenario needs at least one hint");
            assertTrue(!scenario.observations().isEmpty(), "Each scenario needs test observations");
            assertTrue(!scenario.acceptedKeywords().isEmpty(), "Each scenario needs accepted diagnostics");
        }
    }

    private static void testDiagnosticReports() {
        BugScenario nullScenario = find("N01");
        DiagnosticReport report = DiagnosticSimulator.run(nullScenario);
        assertEquals(2, report.passedCount(), "N01 must show two successful test cases");
        assertEquals(1, report.failedCount(), "N01 must reproduce exactly one failing test case");
        assertEquals(3, report.observations().size(), "Report must retain all observations");
    }

    private static void testAnswerEvaluation() {
        assertTrue(AnswerEvaluator.evaluate(find("N01"), "null guard is missing").correct(),
                "A null diagnosis should solve N01");
        assertTrue(AnswerEvaluator.evaluate(find("B02"), "off-by-one boundary error").correct(),
                "An off-by-one diagnosis should solve B02");
        assertTrue(AnswerEvaluator.evaluate(find("M03"), "integer division truncates the value").correct(),
                "An integer division diagnosis should solve M03");
        assertTrue(!AnswerEvaluator.evaluate(find("C05"), "null pointer").correct(),
                "An unrelated diagnosis must not solve C05");
    }

    private static void testProgressPersistence() throws Exception {
        Path temporaryDirectory = Files.createTempDirectory("debugging-lab-test-");
        Path progressFile = temporaryDirectory.resolve("progress.tsv");
        ProgressStore store = new ProgressStore(progressFile);

        ProgressEntry firstAttempt = store.recordAttempt("N01", false, 0);
        assertEquals(1, firstAttempt.attempts(), "An incorrect answer must be counted");
        assertTrue(!firstAttempt.solved(), "An incorrect answer must not mark a scenario as solved");

        ProgressEntry solvedAttempt = store.recordAttempt("N01", true, 30);
        assertEquals(2, solvedAttempt.attempts(), "A solved attempt increments the count");
        assertTrue(solvedAttempt.solved(), "A correct answer should mark the scenario as solved");
        assertEquals(30, solvedAttempt.totalPoints(), "The first correct answer earns its points");

        ProgressEntry repeatedAttempt = store.recordAttempt("N01", true, 30);
        assertEquals(3, repeatedAttempt.attempts(), "Repeated answers remain visible in progress");
        assertEquals(30, repeatedAttempt.totalPoints(), "A solved scenario must not award points twice");

        Map<String, ProgressEntry> reloaded = store.load();
        assertEquals(1, reloaded.size(), "Progress should be written to disk");
        assertEquals(30, reloaded.get("N01").totalPoints(), "Saved points must survive reload");

        Files.deleteIfExists(progressFile);
        Files.deleteIfExists(temporaryDirectory);
    }

    private static BugScenario find(String id) {
        return ScenarioCatalog.create().stream()
                .filter(scenario -> scenario.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }
}
