package app.feedgateway;

import static app.feedgateway.VolPremiumFixtures.FIXTURE_NOW_MS;
import static app.feedgateway.VolPremiumFixtures.Row;
import static app.feedgateway.VolPremiumFixtures.SESSION;
import static app.feedgateway.VolPremiumFixtures.SESSION_LAST_INSTANT_MS;
import static app.feedgateway.VolPremiumFixtures.canonicalReading;
import static app.feedgateway.VolPremiumFixtures.longField;
import static app.feedgateway.VolPremiumFixtures.readings;
import static app.feedgateway.VolPremiumFixtures.warnings;
import static app.feedgateway.VolPremiumFixtures.with;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.VolPremiumSessionStore.Admission;
import app.feedgateway.VolPremiumSessionStore.DiskOp;
import app.feedgateway.VolPremiumSessionStore.Item;
import app.feedgateway.VolPremiumSessionStore.Position;
import app.feedgateway.VolPremiumSessionStore.Refusal;
import app.feedgateway.VolPremiumSessionStore.Snapshot;
import app.feedgateway.VolPremiumSessionStore.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The session store's DISK log (Codex gateway r3, the storage decision): a replacement appends and repoints, the log
 * is rewritten when superseded bytes pass a quarter of the live ones, a session's log is deleted when the store stops
 * holding it, every disk failure fails its session CLOSED and LOUD without ever throwing, and the REST page streams
 * from the log and says when what it streamed is not the whole session.
 */
class VolPremiumSessionDiskTest {

    /** The real file system, with failures switched on at will, and the largest size each file ever reached. */
    static final class FailingIo implements VolPremiumSessionLog.Io {
        final VolPremiumSessionLog.Io real = VolPremiumSessionLog.FILES;
        volatile boolean directory;
        volatile boolean create;
        volatile boolean write;
        volatile boolean read;
        volatile boolean delete;
        /** When write is on: only files whose name ends with this fail (null: every file). */
        volatile String writeSuffix;
        /** When write is on: how many bytes of the failing write reach the file first (a disk filling mid-record). */
        volatile int partialBytes;
        final Map<Path, Long> peak = new ConcurrentHashMap<>();

        long peakFileBytes() {
            return peak.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        }

        @Override
        public Path createDirectory(Path root, String prefix) throws IOException {
            if (directory) {
                throw new IOException("Permission denied (injected)");
            }
            return real.createDirectory(root, prefix);
        }

        @Override
        public VolPremiumSessionLog.LogFile create(Path file) throws IOException {
            if (create) {
                throw new IOException("No space left on device (injected)");
            }
            VolPremiumSessionLog.LogFile f = real.create(file);
            return new VolPremiumSessionLog.LogFile() {
                @Override
                public int write(ByteBuffer source, long position) throws IOException {
                    if (write && (writeSuffix == null || file.getFileName().toString().endsWith(writeSuffix))) {
                        if (partialBytes > 0) {
                            ByteBuffer part = source.duplicate();
                            part.limit(Math.min(part.limit(), part.position() + partialBytes));
                            f.write(part, position);
                        }
                        throw new IOException("No space left on device (injected)");
                    }
                    int n = f.write(source, position);
                    peak.merge(file, position + n, Math::max);
                    return n;
                }

                @Override
                public int read(ByteBuffer target, long position) throws IOException {
                    if (read) {
                        throw new IOException("Input/output error (injected)");
                    }
                    return f.read(target, position);
                }

                @Override
                public void close() throws IOException {
                    f.close();
                }
            };
        }

        @Override
        public void delete(Path file) throws IOException {
            if (delete) {
                throw new IOException("Device or resource busy (injected)");
            }
            real.delete(file);
        }

        @Override
        public void deleteTree(Path dir) throws IOException {
            real.deleteTree(dir);
        }

        @Override
        public java.util.List<Path> children(Path root, String prefix) throws IOException {
            return real.children(root, prefix);
        }
    }

    static VolPremiumSessionStore storeIn(Path root, VolPremiumSessionLog.Io io, long budget) {
        return new VolPremiumSessionStore(budget, VolPremiumSessionStore.MAX_SYMBOLS,
                VolPremiumSessionStore.MAX_OBSERVATION_POSITIONS, VolPremiumSessionStore.MAX_WARNING_POSITIONS,
                root, false, io);
    }

    static VolPremiumSessionStore storeIn(Path root, VolPremiumSessionLog.Io io) {
        return storeIn(root, io, VolPremiumSessionStore.SERIES_DISK_BUDGET_BYTES);
    }

    private static Admission offer(VolPremiumSessionStore store, Row row, long offset) {
        return store.acceptObservation("DATABENTO", row.key(), 0, offset, row.json(), FIXTURE_NOW_MS);
    }

    private static List<String> walk(VolPremiumSessionStore store, long nowMs) {
        List<String> out = new ArrayList<>();
        Position at = null;
        for (Item item = store.next(null, nowMs); item != null; item = store.next(at, nowMs)) {
            out.add(item.json());
            at = item.position();
        }
        return out;
    }

    private static List<String> json(List<Row> rows) {
        return rows.stream().map(Row::json).toList();
    }

    private static int bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8).length;
    }

    /** The same observation, corrected: its event time moved later within its own 5 s window. */
    private static Row corrected(Row row, long ms) {
        return new Row(row.key(), with(row.json(), "eventTimeMs", longField(row.json(), "eventTimeMs") + ms));
    }

    private interface Body {
        void run() throws Exception;
    }

    private static String stdout(Body body) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            body.run();
        } finally {
            System.setOut(original);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }

    private static int count(String text, String needle) {
        return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    // ----- replacement, compaction, deletion ----------------------------------------------------------------------

    @Test
    void aReplacementAppendsTheNewVersionAndRepointsTheIndexToIt(@TempDir Path root) throws Exception {
        VolPremiumSessionStore store = storeIn(root, VolPremiumSessionLog.FILES);
        List<Row> rows = new ArrayList<>(readings().subList(0, 10));
        Position fifth = null;
        for (int i = 0; i < rows.size(); i++) {
            Admission a = offer(store, rows.get(i), i);
            fifth = i == 4 ? a.position() : fifth;
        }
        VolPremiumSessionStore.Slot slot = store.slotForTest(fifth);
        long oldOffset = slot.fileOffset;
        int oldLength = slot.length;
        long end = store.logFileBytes();
        Row replacement = corrected(rows.get(4), 1_000L);
        assertTrue(offer(store, replacement, 20).admitted());

        // APPENDED at the end, and the index REPOINTED at it: offset, length and checksum of the new bytes.
        assertEquals(end, slot.fileOffset, "the new version's bytes begin where the log ended");
        assertEquals(bytes(replacement.json()), slot.length);
        assertEquals(VolPremiumSessionLog.crc(replacement.json().getBytes(StandardCharsets.UTF_8)), slot.crc);
        assertEquals(end + bytes(replacement.json()), store.logFileBytes(), "one record's superseded bytes: no rewrite yet");
        assertEquals(0L, store.rewrites());
        // Append-only: the superseded bytes are still where they were, untouched.
        byte[] file = Files.readAllBytes(store.logPathForTest("DATABENTO|SPX"));
        assertArrayEquals(rows.get(4).json().getBytes(StandardCharsets.UTF_8),
                Arrays.copyOfRange(file, (int) oldOffset, (int) oldOffset + oldLength));
        // Every reader gets the new version: the walk, the REST page.
        rows.set(4, replacement);
        assertEquals(json(rows), walk(store, FIXTURE_NOW_MS));
        assertEquals(json(rows), store.snapshot("DATABENTO|SPX", FIXTURE_NOW_MS).observations());
    }

    @Test
    void supersededBytesPastAQuarterOfTheLiveOnesAreRewrittenAwayAndTheReplayIsUnchanged(@TempDir Path root) {
        VolPremiumSessionStore store = storeIn(root, VolPremiumSessionLog.FILES);
        List<Row> rows = new ArrayList<>(readings().subList(0, 12));
        long offset = 0;
        for (Row row : rows) {
            assertTrue(offer(store, row, offset++).admitted());
        }
        long seen = 0;
        for (int round = 1; round <= 3; round++) {
            for (int i = 0; i < rows.size(); i++) {
                Row replacement = corrected(readings().get(i), round * 1_000L);
                assertTrue(offer(store, replacement, offset++).admitted(), "round " + round + " row " + i);
                rows.set(i, replacement);
                long live = store.heldBytes();
                long dead = store.logFileBytes() - live;
                assertTrue(dead * VolPremiumSessionStore.COMPACTION_DEAD_DENOMINATOR <= live,
                        "superseded " + dead + " bytes past a quarter of the live " + live);
                seen = Math.max(seen, dead);
                assertEquals(json(rows), walk(store, FIXTURE_NOW_MS), "the rewrite moved bytes, never versions");
            }
        }
        assertTrue(seen > 0, "superseded bytes do accumulate between rewrites");
        assertTrue(store.rewrites() >= 3, "and are reclaimed: " + store.rewrites() + " rewrites");
        assertTrue(store.metricsText().contains("gateway_vol_premium_log_rewrites_total " + store.rewrites() + "\n"));
    }

    @Test
    void anAppendThatWouldTakeTheFilePastTheBudgetRewritesTheSupersededBytesAwayFirst(@TempDir Path root) {
        // 40 records of r bytes, a budget of 41.5 r. One replacement leaves r superseded bytes (under a quarter of
        // the live ones, so no rewrite yet) and a 41 r file. The next record fits the LIVE budget (41 r) but would
        // take the FILE to 42 r: the superseded bytes are rewritten away first, so the file never passes the budget.
        int r = 6_000;
        List<Row> rows = new ArrayList<>();
        for (Row row : readings().subList(0, 41)) {
            rows.add(new Row(row.key(), VolPremiumEnvelopeTest.padded(row.json(), r)));
        }
        long budget = 41L * r + r / 2;
        FailingIo io = new FailingIo();
        VolPremiumSessionStore store = storeIn(root, io, budget);
        long offset = 0;
        for (Row row : rows.subList(0, 40)) {
            assertTrue(offer(store, row, offset++).admitted());
        }
        Row replacement = new Row(rows.get(5).key(),
                VolPremiumEnvelopeTest.padded(corrected(readings().get(5), 1_000L).json(), r));
        assertTrue(offer(store, replacement, offset++).admitted());
        assertEquals(0L, store.rewrites());
        assertEquals(41L * r, store.logFileBytes());
        assertTrue(offer(store, rows.get(40), offset++).admitted(), "41 r live bytes fit a 41.5 r budget");
        assertEquals(1L, store.rewrites(), "the superseded record was rewritten away before the append");
        assertEquals(41L * r, store.logFileBytes());
        assertEquals(41L * r, store.heldBytes());
        assertTrue(io.peakFileBytes() <= budget, "no file ever passed the budget: " + io.peakFileBytes());
        List<Row> expected = new ArrayList<>(rows);
        expected.set(5, replacement);
        assertEquals(json(expected), walk(store, FIXTURE_NOW_MS));
    }

    @Test
    void aSessionsLogIsDeletedWhenANewerSessionReplacesItWhenItEndsAndAtClose(@TempDir Path root) {
        VolPremiumSessionStore store = storeIn(root, VolPremiumSessionLog.FILES);
        assertTrue(offer(store, readings().get(0), 0).admitted());
        Path first = store.logPathForTest("DATABENTO|SPX");
        assertTrue(Files.exists(first));

        // ROLLOVER: the next session's first record replaces the series, and the store no longer holds the old one.
        String nextDay = "2026-08-28";
        Row next = new Row("SPX|" + nextDay + "|6840",
                VolPremiumFixtures.shiftedDays(readings().get(0).json(), 1, nextDay));
        long nextNow = FIXTURE_NOW_MS + 86_400_000L;
        assertTrue(store.acceptObservation("DATABENTO", next.key(), 0, 1, next.json(), nextNow).admitted());
        assertFalse(Files.exists(first), "the replaced session's log is deleted");
        Path second = store.logPathForTest("DATABENTO|SPX");
        assertTrue(Files.exists(second));
        assertEquals(List.of(next.json()), walk(store, nextNow));

        // END: the retention rule (MAX_AFTER_MIDNIGHT past its ending midnight) releases it, and its log goes.
        assertEquals(0, store.purge(SESSION_LAST_INSTANT_MS + 86_400_000L), "still current at its last instant");
        assertTrue(Files.exists(second));
        assertEquals(1, store.purge(SESSION_LAST_INSTANT_MS + 86_400_000L + 1L));
        assertFalse(Files.exists(second));

        // CLOSE: the process directory goes too.
        Path directory = store.directoryForTest();
        assertTrue(Files.isDirectory(directory));
        store.close();
        assertFalse(Files.exists(directory));
    }

    @Test
    void aDedicatedLogDirectoryIsClearedOfEarlierProcessesLogsAtStartAndNothingElseIsTouched(@TempDir Path root)
            throws Exception {
        // An emptyDir outlives a container restart; the log is a cache nothing reads back, so a new process deletes
        // what earlier ones left. Only when the directory is DEDICATED (the env var set), and only its own prefix.
        Path stale = Files.createDirectories(root.resolve(VolPremiumSessionStore.LOG_DIR_PREFIX + "123"));
        Files.writeString(stale.resolve("s1.a.log"), "{}");
        Path other = Files.writeString(root.resolve("unrelated.txt"), "keep");
        new VolPremiumSessionStore(1L << 20, 2, 10, 10, root, false, VolPremiumSessionLog.FILES);
        assertTrue(Files.exists(stale), "java.io.tmpdir is shared: its siblings are left alone");
        new VolPremiumSessionStore(1L << 20, 2, 10, 10, root, true, VolPremiumSessionLog.FILES);
        assertFalse(Files.exists(stale), "an earlier process's directory in a dedicated root is deleted");
        assertTrue(Files.exists(other), "and nothing else");
        assertEquals(Path.of("/data/vp"), VolPremiumSessionStore.configuredRoot(" /data/vp "));
        assertEquals(Path.of(System.getProperty("java.io.tmpdir")), VolPremiumSessionStore.configuredRoot(" "));
        assertTrue(VolPremiumSessionStore.dedicated("/data/vp"));
        assertFalse(VolPremiumSessionStore.dedicated(null));
    }

    @Test
    void aRecordWithALoneSurrogateIsRefusedBecauseItsBytesCouldNotBeForwardedVerbatim(@TempDir Path root) {
        // A consumer's UTF-8 decoder never yields one, but the store's contract parse would accept it, and encoding
        // it would write '?' in its place: the bytes read back would not be the record the contract validated.
        VolPremiumSessionStore store = storeIn(root, VolPremiumSessionLog.FILES);
        Row canonical = canonicalReading();
        String lone = VolPremiumFixtures.rawReplace(canonical.json(), "\"codeVersion\":\"code-test\"",
                "\"codeVersion\":\"code\uD800test\"");
        assertEquals(Refusal.MALFORMED, store.acceptObservation("DATABENTO", canonical.key(), 0, 0, lone,
                FIXTURE_NOW_MS).refusal());
        assertNull(VolPremiumSessionStore.utf8(lone));
        assertEquals(0, store.heldObservations());
        // A non-Latin-1 character that IS encodable is held and read back exactly.
        String greek = VolPremiumFixtures.rawReplace(canonical.json(), "\"codeVersion\":\"code-test\"",
                "\"codeVersion\":\"code-αβ\"");
        assertTrue(store.acceptObservation("DATABENTO", canonical.key(), 0, 1, greek, FIXTURE_NOW_MS).admitted());
        assertEquals(List.of(greek), walk(store, FIXTURE_NOW_MS));
    }

    // ----- disk failures: CLOSED and LOUD, never thrown, never truncated --------------------------------------

    @Test
    void aDirectoryThatCannotBeCreatedFailsTheSessionClosedLoudlyAndNeverThrows(@TempDir Path root) throws Exception {
        FailingIo io = new FailingIo();
        io.directory = true;
        VolPremiumSessionStore store = storeIn(root, io);
        List<Row> rows = readings().subList(0, 3);
        String log = stdout(() -> {
            for (int i = 0; i < rows.size(); i++) {
                assertEquals(Refusal.DISK_FAILURE, offer(store, rows.get(i), i).refusal(), "row " + i);
            }
        });
        assertEquals(1, count(log, "ERROR vol-premium: session DATABENTO|SPX " + SESSION + ": its disk log FAILED on DIRECTORY"),
                log);
        assertEquals(1L, store.diskErrors(DiskOp.DIRECTORY));
        assertEquals(3L, store.refusals(Stream.OBSERVATION, Refusal.DISK_FAILURE));
        assertEquals(0, store.heldObservations());
        Snapshot page = store.snapshot("DATABENTO|SPX", FIXTURE_NOW_MS);
        assertEquals(SESSION, page.sessionDate());
        assertFalse(page.complete());
        assertEquals(3L, page.refusedForDisk());
        String metrics = store.metricsText();
        assertTrue(metrics.contains("gateway_vol_premium_refused_total{stream=\"ivrv\",reason=\"DISK_FAILURE\"} 3\n"));
        assertTrue(metrics.contains("gateway_vol_premium_disk_errors_total{op=\"DIRECTORY\"} 1\n"));
        assertTrue(metrics.contains("gateway_vol_premium_sessions_disk_failed 1\n"));
        // The next session tries again: a failure is the session's, not the process's.
        io.directory = false;
        String nextDay = "2026-08-28";
        Row next = new Row("SPX|" + nextDay + "|6840",
                VolPremiumFixtures.shiftedDays(readings().get(0).json(), 1, nextDay));
        assertTrue(store.acceptObservation("DATABENTO", next.key(), 0, 9, next.json(), FIXTURE_NOW_MS + 86_400_000L)
                .admitted());
    }

    @Test
    void aDiskThatFillsMidRecordRefusesItKeepsWhatIsHeldAndSaysTheSessionIsIncomplete(@TempDir Path root)
            throws Exception {
        FailingIo io = new FailingIo();
        VolPremiumSessionStore store = storeIn(root, io);
        List<Row> rows = readings().subList(0, 7);
        for (int i = 0; i < 5; i++) {
            assertTrue(offer(store, rows.get(i), i).admitted());
        }
        long live = store.heldBytes();
        io.write = true;
        io.partialBytes = 100;   // the disk takes 100 bytes of the record, then is full
        String log = stdout(() -> {
            assertEquals(Refusal.DISK_FAILURE, offer(store, rows.get(5), 5).refusal());
            io.write = false;    // space comes back: the session still takes nothing more, it is already incomplete
            assertEquals(Refusal.DISK_FAILURE, offer(store, rows.get(6), 6).refusal());
        });
        assertEquals(1, count(log, "its disk log FAILED on WRITE"), log);
        assertEquals(1L, store.diskErrors(DiskOp.WRITE));
        assertEquals(live, store.logFileBytes(), "the half-written record is past the log's end: nothing points at it");
        assertEquals(json(rows.subList(0, 5)), walk(store, FIXTURE_NOW_MS), "what was held is served, exactly");
        Snapshot page = store.snapshot("DATABENTO|SPX", FIXTURE_NOW_MS);
        assertEquals(json(rows.subList(0, 5)), page.observations());
        assertFalse(page.complete());
        assertEquals(2L, page.refusedForDisk());
        assertEquals(1, store.sessionsDiskFailed());
    }

    @Test
    void aRecordThatDoesNotReadBackIntactIsNeverForwardedAndFailsTheSession(@TempDir Path root) throws Exception {
        VolPremiumSessionStore store = storeIn(root, VolPremiumSessionLog.FILES);
        List<Row> rows = readings().subList(0, 5);
        Position third = null;
        for (int i = 0; i < rows.size(); i++) {
            Admission a = offer(store, rows.get(i), i);
            third = i == 2 ? a.position() : third;
        }
        assertTrue(store.acceptWarning("DATABENTO", warnings().get(0).key(), 0, 9, warnings().get(0).json(),
                FIXTURE_NOW_MS).admitted());
        // One byte of the third record flipped on disk (a bad sector, a stray write).
        VolPremiumSessionStore.Slot slot = store.slotForTest(third);
        try (RandomAccessFile file = new RandomAccessFile(store.logPathForTest("DATABENTO|SPX").toFile(), "rw")) {
            file.seek(slot.fileOffset + 10);
            int b = file.read();
            file.seek(slot.fileOffset + 10);
            file.write(b ^ 0x01);
        }
        List<String> walked = new ArrayList<>();
        String log = stdout(() -> walked.addAll(walk(store, FIXTURE_NOW_MS)));
        assertEquals(json(rows.subList(0, 2)), walked, "the walk stops AT the bad record: never it, never past it, "
                + "and not on to the session's warnings");
        assertTrue(log.contains("its disk log FAILED on READ"), log);
        assertTrue(log.contains("CRC32C mismatch"), log);
        assertTrue(store.diskErrors(DiskOp.READ) >= 1L);
        // The page is the same prefix: it stops at the bad record, and nothing after it is written, warnings
        // included, although they read back intact.
        Snapshot page = store.snapshot("DATABENTO|SPX", FIXTURE_NOW_MS);
        assertEquals(json(rows.subList(0, 2)), page.observations());
        assertEquals(List.of(), page.warnings(), "nothing past the unreadable record");
        assertFalse(page.complete(), "the page says it is not the whole session");
        assertEquals(Refusal.DISK_FAILURE, offer(store, readings().get(5), 5).refusal(), "and the session takes nothing more");
    }

    @Test
    void aReadErrorEndsTheWalkThroughThatSessionAndThePageSaysIncomplete(@TempDir Path root) throws Exception {
        FailingIo io = new FailingIo();
        VolPremiumSessionStore store = storeIn(root, io);
        for (int i = 0; i < 3; i++) {
            assertTrue(offer(store, readings().get(i), i).admitted());
        }
        io.read = true;
        stdout(() -> {
            assertNull(store.next(null, FIXTURE_NOW_MS));
            VolPremiumSessionStore.Page page = store.page("DATABENTO|SPX", () -> FIXTURE_NOW_MS);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            page.writeObservations(out);
            page.writeWarnings(out);
            assertEquals(0, out.size());
            assertFalse(page.retention().complete());
        });
        assertTrue(store.diskErrors(DiskOp.READ) >= 1L);
    }

    @Test
    void aRewriteThatFailsLeavesTheOldLogInPlaceAndFailsTheSessionFromItsNextRecord(@TempDir Path root)
            throws Exception {
        FailingIo io = new FailingIo();
        VolPremiumSessionStore store = storeIn(root, io);
        List<Row> rows = new ArrayList<>(readings().subList(0, 3));
        for (int i = 0; i < rows.size(); i++) {
            assertTrue(offer(store, rows.get(i), i).admitted());
        }
        Path current = store.logPathForTest("DATABENTO|SPX");
        Path nextGeneration = current.resolveSibling(current.getFileName().toString().replace(".a.log", ".b.log"));
        io.write = true;
        io.writeSuffix = ".b.log";   // the rewrite's new file fails; the current one does not
        Row replacement = corrected(rows.get(1), 1_000L);
        String log = stdout(() -> assertTrue(offer(store, replacement, 3).admitted(),
                "the replacement is appended to the current file before the rewrite is attempted"));
        assertTrue(log.contains("its disk log FAILED on REWRITE"), log);
        assertEquals(1L, store.diskErrors(DiskOp.REWRITE));
        assertFalse(Files.exists(nextGeneration), "the abandoned rewrite's partial file is deleted");
        assertEquals(current, store.logPathForTest("DATABENTO|SPX"), "the old file is still the log");
        rows.set(1, replacement);
        assertEquals(json(rows), walk(store, FIXTURE_NOW_MS), "and every held version reads back from it");
        assertEquals(Refusal.DISK_FAILURE, offer(store, readings().get(3), 4).refusal());
        assertFalse(store.snapshot("DATABENTO|SPX", FIXTURE_NOW_MS).complete());
    }

    @Test
    void aLogThatCannotBeDeletedIsCountedAndLogged(@TempDir Path root) throws Exception {
        FailingIo io = new FailingIo();
        VolPremiumSessionStore store = storeIn(root, io);
        assertTrue(offer(store, readings().get(0), 0).admitted());
        io.delete = true;
        String log = stdout(() -> assertEquals(1, store.purge(SESSION_LAST_INSTANT_MS + 1L)));
        assertTrue(log.contains("could not be deleted"), log);
        assertEquals(1L, store.diskErrors(DiskOp.DELETE));
        assertTrue(store.metricsText().contains("gateway_vol_premium_disk_errors_total{op=\"DELETE\"} 1\n"));
    }

    // ----- the REST page -------------------------------------------------------------------------------------

    @Test
    void aPageWhoseSessionEndsOrIsReplacedWhileItIsReadSaysIncomplete(@TempDir Path root) throws Exception {
        VolPremiumSessionStore store = storeIn(root, VolPremiumSessionLog.FILES);
        List<Row> rows = readings().subList(0, 4);
        for (int i = 0; i < rows.size(); i++) {
            assertTrue(offer(store, rows.get(i), i).admitted());
        }
        assertTrue(store.acceptWarning("DATABENTO", warnings().get(0).key(), 0, 9, warnings().get(0).json(),
                FIXTURE_NOW_MS).admitted());
        // Whole: every record, and complete.
        long[] clock = {FIXTURE_NOW_MS};
        VolPremiumSessionStore.Page whole = store.page("DATABENTO|SPX", () -> clock[0]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        whole.writeObservations(out);
        assertEquals(String.join(",", json(rows)), out.toString(StandardCharsets.UTF_8));
        out.reset();
        whole.writeWarnings(out);
        assertEquals(warnings().get(0).json(), out.toString(StandardCharsets.UTF_8));
        assertTrue(whole.retention().complete());

        // ENDS before it is read: opened while current, the session is over when its records are read.
        VolPremiumSessionStore.Page ending = store.page("DATABENTO|SPX", () -> clock[0]);
        clock[0] = SESSION_LAST_INSTANT_MS + 1L;
        ByteArrayOutputStream rest = new ByteArrayOutputStream();
        ending.writeObservations(rest);
        ending.writeWarnings(rest);
        assertEquals(0, rest.size(), "nothing of an ended session is read");
        assertFalse(ending.retention().complete());

        // REPLACED before it is read: a newer session rolls the series over after the page opened.
        clock[0] = FIXTURE_NOW_MS;
        VolPremiumSessionStore.Page replaced = store.page("DATABENTO|SPX", () -> clock[0]);
        String nextDay = "2026-08-28";
        Row next = new Row("SPX|" + nextDay + "|6840",
                VolPremiumFixtures.shiftedDays(readings().get(0).json(), 1, nextDay));
        clock[0] = FIXTURE_NOW_MS + 86_400_000L;
        assertTrue(store.acceptObservation("DATABENTO", next.key(), 0, 10, next.json(), clock[0]).admitted());
        rest.reset();
        replaced.writeObservations(rest);
        replaced.writeWarnings(rest);
        assertEquals(0, rest.size(), "nothing of the next session is mixed into this page");
        assertEquals(SESSION, replaced.sessionDate());
        assertFalse(replaced.retention().complete());
        assertNotNull(store.logPathForTest("DATABENTO|SPX"));
    }
}
