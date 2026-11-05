package app.feedgateway;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Shared bounded compressed source journal. Transport offsets never replace collector K. */
final class DepthUnitJournal {
    static final int BLOCK_TARGET = 64 * 1024, MAX_UNIT = 1024 * 1024;
    static final long DEFAULT_BYTES = 128L * 1024 * 1024;
    record Item(long K, long observedThrough, boolean heartbeat, byte[] datum) {}
    record Block(long firstK, long lastK, long firstTime, long lastTime, int rawBytes, byte[] encoded) {}
    private final long maxBytes;
    private final ArrayDeque<Block> blocks = new ArrayDeque<>();
    private ByteArrayOutputStream pending = new ByteArrayOutputStream(BLOCK_TARGET);
    private DataOutputStream writer = new DataOutputStream(pending);
    private long firstK, lastK, firstTime, lastTime, sourceK = -1, sourceF, encodedBytes;
    private boolean hasPending;
    private long evictions;

    DepthUnitJournal() { this(DEFAULT_BYTES); }
    DepthUnitJournal(long maxBytes) {
        if (maxBytes < MAX_UNIT + BLOCK_TARGET || maxBytes > DEFAULT_BYTES) throw new IllegalArgumentException("journal budget");
        this.maxBytes = maxBytes;
    }
    synchronized void append(long K, long observedThrough, boolean heartbeat, byte[] datum) {
        if (K < 0 || observedThrough < 0 || datum == null || datum.length == 0 || datum.length > MAX_UNIT)
            throw new IllegalArgumentException("journal unit bounds");
        if (sourceK >= 0 && (heartbeat ? K != sourceK : K != sourceK + 1))
            throw new IllegalArgumentException("source sequence discontinuity");
        if (sourceK >= 0 && observedThrough < sourceF) throw new IllegalArgumentException("source watermark regression");
        try {
            int length = 21 + datum.length;
            if (hasPending && pending.size() + length > BLOCK_TARGET) seal();
            if (!hasPending) { firstK = K; firstTime = observedThrough; hasPending = true; }
            writer.writeLong(K); writer.writeLong(observedThrough); writer.writeBoolean(heartbeat); writer.writeInt(datum.length); writer.write(datum);
            lastK = K; lastTime = observedThrough; sourceK = K; sourceF = observedThrough;
            if (pending.size() >= BLOCK_TARGET) seal();
            trim();
        } catch (IOException e) { throw new IllegalStateException("journal encoding", e); }
    }
    private void seal() throws IOException {
        if (!hasPending) return;
        writer.flush(); byte[] raw = pending.toByteArray();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (GZIPOutputStream zipped = new GZIPOutputStream(result)) { zipped.write(raw); }
        byte[] encoded = result.toByteArray();
        // Charge a conservative index/object allowance per block; actual retained heap is qualified separately.
        blocks.addLast(new Block(firstK, lastK, firstTime, lastTime, raw.length, encoded)); encodedBytes += encoded.length + 128L;
        pending = new ByteArrayOutputStream(BLOCK_TARGET); writer = new DataOutputStream(pending); hasPending = false;
    }
    private void trim() {
        while (!blocks.isEmpty() && encodedBytes + pending.size() + BLOCK_TARGET > maxBytes) {
            Block old = blocks.removeFirst(); encodedBytes -= old.encoded.length + 128L; evictions++;
        }
    }
    synchronized boolean canResume(long afterK) {
        if (sourceK < 0 || afterK > sourceK || afterK < 0) return false;
        if (blocks.isEmpty() && !hasPending) return afterK == sourceK;
        long earliest = blocks.isEmpty() ? firstK : blocks.getFirst().firstK;
        return afterK >= earliest - 1;
    }
    /** Bounded page. Caller supplies the last watermark to exclude old same-K heartbeat metadata. */
    synchronized List<Item> after(long afterK, long afterWatermark, int maxItems, int maxPageBytes) {
        if (!canResume(afterK) || maxItems < 1 || maxItems > 256 || maxPageBytes < 1 || maxPageBytes > 4 * 1024 * 1024) return List.of();
        List<Item> result = new ArrayList<>(); int[] bytes = {0};
        try {
            for (Block block : blocks) {
                if (block.lastK < afterK) continue;
                if (!readBlock(expand(block), afterK, afterWatermark, maxItems, maxPageBytes, result, bytes)) return List.copyOf(result);
            }
            if (hasPending) readBlock(pending.toByteArray(), afterK, afterWatermark, maxItems, maxPageBytes, result, bytes);
            return List.copyOf(result);
        } catch (IOException e) { throw new IllegalStateException("journal corruption", e); }
    }
    private static boolean readBlock(byte[] raw, long afterK, long afterWatermark, int maxItems, int maxBytes, List<Item> result, int[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw))) {
            while (in.available() > 0) {
                long K = in.readLong(), F = in.readLong(); boolean heartbeat = in.readBoolean(); int size = in.readInt();
                if (size < 1 || size > MAX_UNIT || size > in.available()) throw new IOException("record length");
                if (K < afterK || K == afterK && (!heartbeat || F <= afterWatermark)) { in.skipNBytes(size); continue; }
                if (result.size() >= maxItems || bytes[0] + size > maxBytes) return false;
                byte[] datum = in.readNBytes(size); result.add(new Item(K, F, heartbeat, datum)); bytes[0] += size;
            }
        }
        return true;
    }
    private static byte[] expand(Block block) throws IOException {
        if (block.rawBytes < 1 || block.rawBytes > MAX_UNIT + 21 + BLOCK_TARGET) throw new IOException("block bound");
        byte[] result = new byte[block.rawBytes];
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(block.encoded))) {
            int n = 0;
            while (n < result.length) {int count = in.read(result, n, result.length - n); if (count < 0) throw new IOException("short block"); n += count;}
            if (in.read() != -1) throw new IOException("expanded block bound");
        }
        return result;
    }
    synchronized long bytes() { return encodedBytes + pending.size() + BLOCK_TARGET; }
    synchronized long evictions() { return evictions; }
    synchronized long lastK() { return sourceK; }
    synchronized long earliestWatermark() { return blocks.isEmpty() ? hasPending ? firstTime : 0 : blocks.getFirst().firstTime; }
    synchronized void clear() {
        blocks.clear(); encodedBytes = 0; pending = new ByteArrayOutputStream(BLOCK_TARGET); writer = new DataOutputStream(pending);
        hasPending = false; sourceK = -1; sourceF = 0; firstK = lastK = firstTime = lastTime = 0;
    }
}
