package app.feedgateway;

import static app.feedgateway.VolPremiumFixtures.FIXTURE_NOW_MS;
import static app.feedgateway.VolPremiumFixtures.Row;
import static app.feedgateway.VolPremiumFixtures.longField;
import static app.feedgateway.VolPremiumFixtures.readings;
import static app.feedgateway.VolPremiumFixtures.warnings;
import static app.feedgateway.VolPremiumFixtures.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.feedgateway.VolPremiumSessionStore.Admission;
import app.feedgateway.VolPremiumSessionStore.Incomplete; // r4-api
import app.feedgateway.VolPremiumSessionStore.Item;
import app.feedgateway.VolPremiumSessionStore.Page;
import app.feedgateway.VolPremiumSessionStore.Position;
import app.feedgateway.VolPremiumSessionStore.Refusal;
import app.feedgateway.VolPremiumSessionStore.Retention;
import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * The REST page is ONE SNAPSHOT of its session (Codex gateway r4): every record of the session as it stood when the
 * page was opened, each once, in the store's order, with {@code complete=true}; or {@code complete=false} with the
 * reason. Never a gapped view, nor versions that never coexisted, marked complete. Each concurrent case is
 * reproduced deterministically: the mutation runs on the page's first write, after its first chunk was read and
 * while it is being written out, with the store's lock free (as it is between chunks in production).
 *
 * <p>The observations are the widest the engine can write ({@link VolPremiumSessionStore#WIDEST_OBSERVATION_BYTES},
 * 14,015 bytes), so 18 fill a chunk and 45 take three.
 */
class VolPremiumPageConsistencyTest {

    private static final String SERIES = "DATABENTO|SPX";
    private static final int WIDE = VolPremiumSessionStore.WIDEST_OBSERVATION_BYTES;

    /** Row {@code i} of the stream as the widest observation the engine can write. */
    private static Row wide(int i) {
        Row row = readings().get(i);
        return new Row(row.key(), VolPremiumEnvelopeTest.widened(row.json(), WIDE, true));
    }

    /** The same observation corrected (its event time one second later in its window), at the same width. */
    private static Row wideCorrected(int i) {
        Row row = readings().get(i);
        String json = with(row.json(), "eventTimeMs", longField(row.json(), "eventTimeMs") + 1_000L);
        return new Row(row.key(), VolPremiumEnvelopeTest.widened(json, WIDE, true));
    }

    private static VolPremiumSessionStore storeIn(Path root) {
        return VolPremiumSessionDiskTest.storeIn(root, VolPremiumSessionLog.FILES);
    }

    private static Admission offer(VolPremiumSessionStore store, Row row, long offset) {
        return store.acceptObservation("DATABENTO", row.key(), 0, offset, row.json(), FIXTURE_NOW_MS);
    }

    private static void admitWide(VolPremiumSessionStore store, List<Integer> rows) {
        for (int i : rows) {
            assertTrue(offer(store, wide(i), i).admitted(), "row " + i);
        }
    }

    private static List<Integer> range(int from, int to, int... except) {
        List<Integer> out = new ArrayList<>();
        outer:
        for (int i = from; i < to; i++) {
            for (int e : except) {
                if (i == e) {
                    continue outer;
                }
            }
            out.add(i);
        }
        return out;
    }

    /** The socket replay's records, by stream (observations, warnings): what every reader of the store is held to. */
    private static List<List<String>> replay(VolPremiumSessionStore store, long nowMs) {
        List<String> observations = new ArrayList<>();
        List<String> transitions = new ArrayList<>();
        Position at = null;
        for (Item item = store.next(null, nowMs); item != null; item = store.next(at, nowMs)) {
            (item.position().phase() == Position.OBSERVATIONS ? observations : transitions).add(item.json());
            at = item.position();
        }
        return List.of(observations, transitions);
    }

    /** The records of a written JSON array body ({@code a,b,c}), each exactly as it was written. */
    static List<String> records(String array) throws IOException {
        List<String> out = new ArrayList<>();
        String text = "[" + array + "]";
        try (JsonParser parser = new JsonFactory().createParser(text)) {
            assertEquals(JsonToken.START_ARRAY, parser.nextToken());
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                int from = (int) parser.getTokenLocation().getCharOffset();
                parser.skipChildren();
                int to = (int) parser.getTokenLocation().getCharOffset() + 1;
                out.add(text.substring(from, to));
            }
        }
        return out;
    }

    /**
     * The UTF-8 bytes of records as the page opened on them, summed from the records themselves: the figure every
     * verdict of that page states as {@code retainedBytes}, whatever its reason (Codex r5).
     */
    private static long utf8Bytes(List<String> records) {
        long n = 0L;
        for (String record : records) {
            n += record.getBytes(StandardCharsets.UTF_8).length;
        }
        return n;
    }

    /** {@code written} is a subsequence of {@code snapshot}: only its records, each at most once, in its order. */
    private static void assertSubsequenceOf(List<String> snapshot, List<String> written) {
        int at = 0;
        for (String record : written) {
            while (at < snapshot.size() && !snapshot.get(at).equals(record)) {
                at++;
            }
            assertTrue(at < snapshot.size(), "a record written that is not the snapshot's, or out of its order");
            at++;
        }
    }

    interface Body {
        void run() throws Exception;
    }

    /** Where a page writes its observations: runs the mutation once, at its first write, and notes the lock. */
    private static final class Interleaved extends OutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final Object lock;
        private Body mutation;
        boolean fired;
        boolean lockFree;

        Interleaved(Object lock, Body mutation) {
            this.lock = lock;
            this.mutation = mutation;
        }

        @Override
        public void write(int b) throws IOException {
            fire();
            bytes.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            fire();
            bytes.write(b, off, len);
        }

        private void fire() throws IOException {
            if (mutation == null) {
                return;
            }
            Body run = mutation;
            mutation = null;
            fired = true;
            lockFree = !Thread.holdsLock(lock);
            try {
                run.run();
            } catch (IOException | RuntimeException | Error passThrough) {
                throw passThrough;
            } catch (Exception checked) {
                throw new IOException(checked);
            }
        }
    }

    private record Served(List<String> observations, List<String> warnings, Retention retention) {
    }

    /**
     * Opens a page, runs {@code mutation} (if any) while its first chunk is being written out, then writes the rest
     * and reads the verdict, as the REST route does.
     */
    private static Served serve(VolPremiumSessionStore store, LongSupplier clock, Body mutation) throws Exception {
        Page page = store.page(SERIES, clock);
        Interleaved observations = new Interleaved(store, mutation);
        page.writeObservations(observations);
        ByteArrayOutputStream transitions = new ByteArrayOutputStream();
        page.writeWarnings(transitions);
        Retention retention = page.retention();
        if (mutation != null) {
            assertTrue(observations.fired, "precondition: the mutation ran between two chunks");
            assertTrue(observations.lockFree, "precondition: with the store's lock free, as between chunks");
        }
        return new Served(records(observations.bytes.toString(StandardCharsets.UTF_8)),
                records(transitions.toString(StandardCharsets.UTF_8)), retention);
    }

    // ----- Codex r4's two reproductions: never a view marked complete that the session never was --------------------

    @Test
    void aCorrectionOnEachSideOfThePageCursorIsNeverServedAsACompleteSession(@TempDir Path root) throws Exception {
        // Codex r4, reproduction 1: 45 observations over three chunks. While the first chunk is written out,
        // observation 0 (already read) and then observation 40 (not yet read) are corrected. The r3 page served the OLD
        // 0 with the NEW 40, two versions that never coexisted, and said complete.
        VolPremiumSessionStore store = storeIn(root);
        admitWide(store, range(0, 45));
        List<String> opened = replay(store, FIXTURE_NOW_MS).get(0);

        Served served = serve(store, () -> FIXTURE_NOW_MS, () -> {
            assertTrue(offer(store, wideCorrected(0), 100).admitted());
            assertTrue(offer(store, wideCorrected(40), 101).admitted());
        });

        assertFalse(served.retention().complete(), "the snapshot's 40 was replaced before the page reached it");
        assertEquals(Incomplete.CHANGED_WHILE_READ, served.retention().reason()); // r4-api
        assertEquals(45L * WIDE, utf8Bytes(opened), "precondition: the snapshot's own size");
        assertEquals(utf8Bytes(opened), served.retention().retainedBytes(), "the snapshot's captured size, not what was served");
        assertSubsequenceOf(opened, served.observations());
        assertFalse(served.observations().contains(wideCorrected(40).json()), "no version admitted after the page opened");
        // Asked again, undisturbed: complete, and the session as it now stands, both corrections included.
        Served again = serve(store, () -> FIXTURE_NOW_MS, null);
        assertTrue(again.retention().complete());
        assertEquals(replay(store, FIXTURE_NOW_MS).get(0), again.observations());
        assertTrue(again.observations().contains(wideCorrected(0).json()));
        assertTrue(again.observations().contains(wideCorrected(40).json()));
    }

    @Test
    void anInsertionBehindTheCursorAndACorrectionAheadOfItAreNeverServedAsACompleteSession(@TempDir Path root)
            throws Exception {
        // Codex r4, reproduction 2: 44 observations with a hole at row 5, inside the first chunk. While that chunk is
        // written out, row 5 is admitted (behind the cursor) and row 40 corrected (ahead of it). The r3 page served 44
        // while the store held 45, with the later correction but without the insertion, and said complete.
        VolPremiumSessionStore store = storeIn(root);
        admitWide(store, range(0, 45, 5));
        List<String> opened = replay(store, FIXTURE_NOW_MS).get(0);
        assertEquals(44, opened.size());

        Served served = serve(store, () -> FIXTURE_NOW_MS, () -> {
            assertTrue(offer(store, wide(5), 100).admitted());
            assertTrue(offer(store, wideCorrected(40), 101).admitted());
        });

        assertFalse(served.retention().complete());
        assertEquals(Incomplete.CHANGED_WHILE_READ, served.retention().reason()); // r4-api
        assertEquals(44L * WIDE, served.retention().retainedBytes(), "the 44 opened on, not the 45 held after");
        assertEquals(utf8Bytes(opened), served.retention().retainedBytes());
        assertSubsequenceOf(opened, served.observations());
        Served again = serve(store, () -> FIXTURE_NOW_MS, null);
        assertTrue(again.retention().complete());
        assertEquals(45, again.observations().size());
        assertEquals(replay(store, FIXTURE_NOW_MS).get(0), again.observations());
    }

    @Test
    void recordsAdmittedOnBothSidesOfTheCursorAreLeftOutAndThePageIsTheSessionAsItWasOpened(@TempDir Path root)
            throws Exception {
        // Live traffic: while the first chunk is written out, a late observation lands BEHIND the cursor, a new frame
        // AHEAD of it, and a new transition in the stream not yet read. The r3 page served the frame and the transition
        // but not the late observation (a set of records the session never held at any instant) and said complete.
        // The page is the snapshot it opened on, whole.
        VolPremiumSessionStore store = storeIn(root);
        admitWide(store, range(0, 45, 5));
        for (int w = 0; w < 2; w++) {
            assertTrue(store.acceptWarning("DATABENTO", warnings().get(w).key(), 0, 200 + w, warnings().get(w).json(),
                    FIXTURE_NOW_MS).admitted());
        }
        List<List<String>> opened = replay(store, FIXTURE_NOW_MS);
        long openedBytes = store.heldBytes();

        Served served = serve(store, () -> FIXTURE_NOW_MS, () -> {
            assertTrue(offer(store, wide(5), 100).admitted());
            assertTrue(offer(store, wide(45), 101).admitted());
            assertTrue(store.acceptWarning("DATABENTO", warnings().get(2).key(), 0, 202, warnings().get(2).json(),
                    FIXTURE_NOW_MS).admitted());
        });

        assertTrue(store.heldBytes() > openedBytes, "precondition: all three were admitted during the page");
        assertTrue(served.retention().complete(), "new positions are not part of the snapshot; nothing of it was lost");
        assertEquals(null, served.retention().reason()); // r4-api
        assertEquals(opened.get(0), served.observations(), "the observations as the page opened on them");
        assertEquals(opened.get(1), served.warnings(), "and the transitions, from the same instant");
        assertEquals(openedBytes, served.retention().retainedBytes(), "the figure is the snapshot's own");
        Served again = serve(store, () -> FIXTURE_NOW_MS, null);
        assertTrue(again.retention().complete());
        assertEquals(replay(store, FIXTURE_NOW_MS), List.of(again.observations(), again.warnings()));
        assertEquals(46, again.observations().size());
        assertEquals(3, again.warnings().size());
    }

    // ----- the other concurrent changes: compaction, a same-date session, disk failure, rollover --------------------

    @Test
    void correctionsOfRecordsAlreadyWrittenAndTheRewriteTheyTriggerLeaveThePageCompleteAndUnchanged(@TempDir Path root)
            throws Exception {
        // Twelve corrections of records the first chunk already wrote: superseded bytes pass a quarter of the live ones
        // and the log is REWRITTEN between chunks. The rest of the page reads through the new generation, at the new
        // offsets, and is still exactly the snapshot.
        VolPremiumSessionStore store = storeIn(root);
        admitWide(store, range(0, 45));
        List<String> opened = replay(store, FIXTURE_NOW_MS).get(0);
        long rewrites = store.rewrites();
        Path generation = store.logPathForTest(SERIES);

        Served served = serve(store, () -> FIXTURE_NOW_MS, () -> {
            for (int i = 0; i < 12; i++) {
                assertTrue(offer(store, wideCorrected(i), 100 + i).admitted());
            }
            assertEquals(rewrites + 1, store.rewrites(), "precondition: the log was rewritten between chunks");
            assertNotEquals(generation, store.logPathForTest(SERIES), "precondition: into its next generation");
        });

        assertTrue(served.retention().complete(), "every record of the snapshot was delivered in its own version");
        assertEquals(opened, served.observations());
        Served again = serve(store, () -> FIXTURE_NOW_MS, null);
        assertTrue(again.retention().complete());
        assertEquals(replay(store, FIXTURE_NOW_MS).get(0), again.observations());
    }

    @Test
    void aPageNeverReadsASessionOtherThanTheOneItOpenedOnEvenOneOfTheSameDate(@TempDir Path root) throws Exception {
        // Closed, then opened again for the same date: the r3 page matched sessions by date, so it streamed the NEW
        // session's records under the old one's verdict and said complete.
        VolPremiumSessionStore store = storeIn(root);
        admitWide(store, range(0, 3));
        long openedBytes = utf8Bytes(replay(store, FIXTURE_NOW_MS).get(0));
        assertEquals(3L * WIDE, openedBytes, "precondition");
        Page page = store.page(SERIES, () -> FIXTURE_NOW_MS);
        store.close();
        assertTrue(offer(store, wide(0), 10).admitted(), "a new session of the same date");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        page.writeObservations(out);
        page.writeWarnings(out);
        Retention retention = page.retention();
        assertEquals(0, out.size(), "nothing of another session is written");
        assertFalse(retention.complete());
        assertEquals(Incomplete.SESSION_ENDED, retention.reason()); // r4-api
        assertEquals(openedBytes, retention.retainedBytes(),
                "Codex r5: the snapshot's captured size, though the session it was captured from is gone");
    }

    @Test
    void aRecordThatStopsReadingBackMidPageStopsThePageThereAndSaysDiskFailure(@TempDir Path root) throws Exception {
        VolPremiumSessionStore store = storeIn(root);
        Position thirtieth = null;
        for (int i = 0; i < 45; i++) {
            Admission a = offer(store, wide(i), i);
            thirtieth = i == 30 ? a.position() : thirtieth;
        }
        List<String> opened = replay(store, FIXTURE_NOW_MS).get(0);
        VolPremiumSessionStore.Slot slot = store.slotForTest(thirtieth);
        Path log = store.logPathForTest(SERIES);

        Served served = serve(store, () -> FIXTURE_NOW_MS, () -> {
            try (RandomAccessFile file = new RandomAccessFile(log.toFile(), "rw")) {
                file.seek(slot.fileOffset + 10);   // one byte of the thirtieth record flipped on disk
                int b = file.read();
                file.seek(slot.fileOffset + 10);
                file.write(b ^ 0x01);
            }
        });

        assertEquals(opened.subList(0, 30), served.observations(), "the snapshot up to the bad record: never it, never past it");
        assertEquals(List.of(), served.warnings());
        assertFalse(served.retention().complete());
        assertEquals(Incomplete.DISK_FAILURE, served.retention().reason()); // r4-api
        assertEquals(45L * WIDE, served.retention().retainedBytes(), "the snapshot's captured size, not the 30 served");
        assertEquals(utf8Bytes(opened), served.retention().retainedBytes());
    }

    @Test
    void aDiskFailureDuringThePageLeavesItsSnapshotWholeAndTheVerdictIncomplete(@TempDir Path root) throws Exception {
        // A write failure while the page is written out: the refused append is past the log's end, so the snapshot
        // still reads back whole. The session is incomplete from that refusal on, and the verdict says so.
        VolPremiumSessionDiskTest.FailingIo io = new VolPremiumSessionDiskTest.FailingIo();
        VolPremiumSessionStore store = VolPremiumSessionDiskTest.storeIn(root, io);
        admitWide(store, range(0, 45));
        List<String> opened = replay(store, FIXTURE_NOW_MS).get(0);

        Served served = serve(store, () -> FIXTURE_NOW_MS, () -> {
            io.write = true;
            assertEquals(Refusal.DISK_FAILURE, offer(store, wide(45), 100).refusal());
            io.write = false;
        });

        assertEquals(opened, served.observations());
        assertFalse(served.retention().complete());
        assertEquals(Incomplete.DISK_FAILURE, served.retention().reason()); // r4-api
        assertEquals(1L, served.retention().refusedForDisk());
        assertEquals(45L * WIDE, served.retention().retainedBytes(), "the snapshot's captured size");
        assertEquals(utf8Bytes(opened), served.retention().retainedBytes());
    }

    @Test
    void aRolloverMidPageStopsItAtTheNextChunkAndSaysTheSessionEnded(@TempDir Path root) throws Exception {
        VolPremiumSessionStore store = storeIn(root);
        admitWide(store, range(0, 45));
        List<String> opened = replay(store, FIXTURE_NOW_MS).get(0);
        long[] clock = {FIXTURE_NOW_MS};
        String nextDay = "2026-08-28";
        Row next = new Row("SPX|" + nextDay + "|6840",
                VolPremiumFixtures.shiftedDays(readings().get(0).json(), 1, nextDay));

        Served served = serve(store, () -> clock[0], () -> {
            clock[0] = FIXTURE_NOW_MS + 86_400_000L;
            assertTrue(store.acceptObservation("DATABENTO", next.key(), 0, 100, next.json(), clock[0]).admitted());
        });

        assertTrue(!served.observations().isEmpty() && served.observations().size() < opened.size(),
                "the chunk read before the rollover, and nothing after it");
        assertEquals(opened.subList(0, served.observations().size()), served.observations());
        assertFalse(served.observations().contains(next.json()), "nothing of the next session");
        assertFalse(served.retention().complete());
        assertEquals(Incomplete.SESSION_ENDED, served.retention().reason()); // r4-api
        assertEquals(45L * WIDE, served.retention().retainedBytes(),
                "Codex r5: a nonempty response states the snapshot's captured size, never zero");
        assertEquals(utf8Bytes(opened), served.retention().retainedBytes());
    }

    @Test
    void anExpiryMidPageStopsItAndStillStatesTheSnapshotsCapturedSize(@TempDir Path root) throws Exception {
        // Codex r5: the session stops being current while the page is written out, with nothing admitted after it.
        VolPremiumSessionStore store = storeIn(root);
        admitWide(store, range(0, 45));
        List<String> opened = replay(store, FIXTURE_NOW_MS).get(0);
        long[] clock = {FIXTURE_NOW_MS};

        Served served = serve(store, () -> clock[0], () -> clock[0] = FIXTURE_NOW_MS + 86_400_000L);

        assertTrue(!served.observations().isEmpty() && served.observations().size() < opened.size(),
                "the chunk read before the expiry, and nothing after it");
        assertEquals(opened.subList(0, served.observations().size()), served.observations());
        assertFalse(served.retention().complete());
        assertEquals(Incomplete.SESSION_ENDED, served.retention().reason()); // r4-api
        assertEquals(45L * WIDE, served.retention().retainedBytes(), "the snapshot's captured size, never zero");
        assertEquals(utf8Bytes(opened), served.retention().retainedBytes());
    }

    @Test
    void aBudgetRefusalMidPageSaysSoAndStillStatesTheSnapshotsCapturedSize(@TempDir Path root) throws Exception {
        // A session whose budget is exactly its 45 records refuses the 46th while the page is written out: the
        // snapshot reads back whole, the verdict is SESSION_BUDGET, and the size is the snapshot's.
        VolPremiumSessionStore store = VolPremiumSessionDiskTest.storeIn(root, VolPremiumSessionLog.FILES, 45L * WIDE);
        admitWide(store, range(0, 45));
        List<String> opened = replay(store, FIXTURE_NOW_MS).get(0);

        Served served = serve(store, () -> FIXTURE_NOW_MS,
                () -> assertEquals(Refusal.SESSION_BUDGET, offer(store, wide(45), 100).refusal()));

        assertEquals(opened, served.observations());
        assertFalse(served.retention().complete());
        assertEquals(Incomplete.SESSION_BUDGET, served.retention().reason()); // r4-api
        assertEquals(1L, served.retention().refusedForBudget());
        assertEquals(45L * WIDE, served.retention().retainedBytes(), "the snapshot's captured size");
        assertEquals(utf8Bytes(opened), served.retention().retainedBytes());
    }

    // ----- the chunk bound (Codex r4 minor) and the undisturbed control --------------------------------------------

    /** The real file system, recording the bytes each read returns (and whether the store's lock is held). */
    private static final class RecordingIo implements VolPremiumSessionLog.Io {
        final VolPremiumSessionLog.Io real = VolPremiumSessionLog.FILES;
        /** Positive: bytes one read returned. -1: a record handed to the response. */
        final List<Long> events = Collections.synchronizedList(new ArrayList<>());
        volatile Object lock;
        volatile boolean readOutsideTheLock;

        @Override
        public Path createDirectory(Path root, String prefix) throws IOException {
            return real.createDirectory(root, prefix);
        }

        @Override
        public VolPremiumSessionLog.LogFile create(Path file) throws IOException {
            VolPremiumSessionLog.LogFile f = real.create(file);
            return new VolPremiumSessionLog.LogFile() {
                @Override
                public int write(ByteBuffer source, long position) throws IOException {
                    return f.write(source, position);
                }

                @Override
                public int read(ByteBuffer target, long position) throws IOException {
                    if (lock != null && !Thread.holdsLock(lock)) {
                        readOutsideTheLock = true;
                    }
                    int n = f.read(target, position);
                    events.add((long) n);
                    return n;
                }

                @Override
                public void close() throws IOException {
                    f.close();
                }
            };
        }

        @Override
        public void delete(Path file) throws IOException {
            real.delete(file);
        }

        @Override
        public void deleteTree(Path dir) throws IOException {
            real.deleteTree(dir);
        }

        @Override
        public List<Path> children(Path root, String prefix) throws IOException {
            return real.children(root, prefix);
        }
    }

    @Test
    void noPageChunkReadsMoreThanItsStatedBoundUnderTheLock(@TempDir Path root) throws Exception {
        // Codex r4 minor: nineteen 14,015-byte observations made ONE r3 chunk of 266,285 bytes, past the advertised
        // 262,144. The bound is now checked before each read: 18 records (252,270 bytes), then 1.
        RecordingIo io = new RecordingIo();
        VolPremiumSessionStore store = VolPremiumSessionDiskTest.storeIn(root, io);
        io.lock = store;
        admitWide(store, range(0, 19));
        List<String> opened = replay(store, FIXTURE_NOW_MS).get(0);
        io.events.clear();

        Page page = store.page(SERIES, () -> FIXTURE_NOW_MS);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) {
                body.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                io.events.add(-1L);
                body.write(b, off, len);
            }
        };
        page.writeObservations(out);
        page.writeWarnings(out);
        Retention retention = page.retention();

        // A chunk is the bytes read (under the lock) before its records are handed to the response.
        List<Long> chunks = new ArrayList<>();
        long run = 0L;
        for (long event : io.events) {
            if (event > 0L) {
                run += event;
            } else if (run > 0L) {
                chunks.add(run);
                run = 0L;
            }
        }
        if (run > 0L) {
            chunks.add(run);
        }
        assertFalse(io.readOutsideTheLock, "every read of a chunk is under the store's lock");
        for (long chunk : chunks) {
            assertTrue(chunk <= VolPremiumSessionStore.PAGE_CHUNK_BYTES,
                    "a chunk of " + chunk + " bytes read under the lock, past the stated " + VolPremiumSessionStore.PAGE_CHUNK_BYTES);
        }
        assertEquals(List.of(18L * WIDE, (long) WIDE), chunks);
        assertTrue(retention.complete());
        assertEquals(opened, records(body.toString(StandardCharsets.UTF_8)));
    }

    @Test
    void anUndisturbedPageIsCompleteAndIdenticalToTheReplay(@TempDir Path root) throws Exception {
        // Both wire versions and every transition of the stream, over three chunks: the page and the socket replay are
        // the same records, bytes and order.
        VolPremiumSessionStore store = storeIn(root);
        admitWide(store, range(0, 45));
        long offset = 100;
        for (long seq = 7141; seq < 7144; seq++) {
            Row v1 = VolPremiumFixtures.v1At(seq);
            assertTrue(offer(store, v1, offset++).admitted(), v1.key());
        }
        for (Row w : warnings()) {
            assertTrue(store.acceptWarning("DATABENTO", w.key(), 0, offset++, w.json(), FIXTURE_NOW_MS).admitted(), w.key());
        }
        List<List<String>> replayed = replay(store, FIXTURE_NOW_MS);
        assertEquals(48, replayed.get(0).size());
        assertEquals(warnings().size(), replayed.get(1).size());

        Served served = serve(store, () -> FIXTURE_NOW_MS, null);

        assertTrue(served.retention().complete());
        assertEquals(null, served.retention().reason()); // r4-api
        assertEquals(replayed.get(0), served.observations());
        assertEquals(replayed.get(1), served.warnings());
        assertEquals(store.heldBytes(), served.retention().retainedBytes());
    }

    // ----- what a REST client sees -----------------------------------------------------------------------------

    @Test
    void theRouteSaysIncompleteWithItsReasonWhenARecordChangesUnderItAndCompleteWhenUndisturbed() throws Exception {
        FeedGatewayService service = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(),
                new HpsfGatewayViewMapper(), null);
        service.volPremiumClockForTest(() -> FIXTURE_NOW_MS);
        VolPremiumSessionStore store = service.volPremiumStoreForTest();
        LiquidityHistoryAuth auth = mock(LiquidityHistoryAuth.class);
        when(auth.authenticate(any())).thenReturn(new LiquidityHistoryAuth.Result(200, "tester"));
        VolPremiumController controller = new VolPremiumController(service, auth);
        ObjectMapper mapper = new ObjectMapper();
        try {
            for (int i = 0; i < 45; i++) {
                assertTrue(store.acceptObservation(FeedGatewayService.VOL_PREMIUM_SOURCE, wide(i).key(), 0, i,
                        wide(i).json(), FIXTURE_NOW_MS).admitted());
            }
            // Codex r4 reproduction 1 through the route: its first write is the envelope's head, its second the first
            // record, read with the first chunk. Observations 0 and 40 are corrected there.
            int[] writes = {0};
            MockHttpServletResponse response = new MockHttpServletResponse() {
                @Override
                public ServletOutputStream getOutputStream() {
                    ServletOutputStream inner = super.getOutputStream();
                    return new ServletOutputStream() {
                        @Override
                        public boolean isReady() {
                            return true;
                        }

                        @Override
                        public void setWriteListener(WriteListener listener) {
                        }

                        @Override
                        public void write(int b) throws IOException {
                            hook();
                            inner.write(b);
                        }

                        @Override
                        public void write(byte[] b, int off, int len) throws IOException {
                            hook();
                            inner.write(b, off, len);
                        }

                        private void hook() {
                            if (++writes[0] == 2) {
                                assertFalse(Thread.holdsLock(store));
                                for (int i : new int[] {0, 40}) {
                                    assertTrue(store.acceptObservation(FeedGatewayService.VOL_PREMIUM_SOURCE,
                                            wideCorrected(i).key(), 0, 100 + i, wideCorrected(i).json(),
                                            FIXTURE_NOW_MS).admitted());
                                }
                            }
                        }
                    };
                }
            };
            controller.ivrv("SPX", "Bearer t", response);
            assertTrue(writes[0] > 2, "precondition: the corrections ran mid-response");
            JsonNode changed = mapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
            assertFalse(changed.get("retention").get("complete").asBoolean(true), "never complete: " + changed.get("retention"));
            assertEquals("CHANGED_WHILE_READ", changed.get("retention").get("reason").asText()); // r4-api

            // Asked again, undisturbed: complete, and the session as it now stands.
            MockHttpServletResponse again = new MockHttpServletResponse();
            controller.ivrv("SPX", "Bearer t", again);
            JsonNode whole = mapper.readTree(again.getContentAsString(StandardCharsets.UTF_8));
            assertTrue(whole.get("retention").get("complete").asBoolean(false));
            assertTrue(whole.get("retention").get("reason").isNull()); // r4-api
            List<JsonNode> expected = new ArrayList<>();
            for (String json : replay(store, FIXTURE_NOW_MS).get(0)) {
                expected.add(mapper.readTree(json));
            }
            List<JsonNode> served = new ArrayList<>();
            whole.get("observations").forEach(served::add);
            assertEquals(expected, served);
        } finally {
            store.close();
        }
    }
}
