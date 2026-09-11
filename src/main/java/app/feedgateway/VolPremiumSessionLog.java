package app.feedgateway;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.CRC32C;

/**
 * The DISK half of {@link VolPremiumSessionStore}: one APPEND-ONLY file per held session, holding the verbatim
 * UTF-8 bytes of every record version the store admitted, in admission order. The store's heap index records,
 * per held position, where the CURRENT version's bytes are (offset, length) and their CRC32C. A replay, a
 * repair and a REST page read the bytes back from here and verify them against that record before use.
 *
 * <p><b>A cache, never a source of truth.</b> Nothing here survives the process that wrote it. A restarted
 * gateway rebuilds every session from Kafka exactly as before this log existed (the store's seek-back and
 * re-admission). Its directory is created fresh by each process. The store deletes it at close(), and at
 * start-up when the directory is a dedicated one. So nothing on disk is ever read back across a restart.
 *
 * <p><b>The pattern it follows</b> is SellerActivityDiskStore, the gateway's other disk cache: a process-local
 * directory under {@code java.io.tmpdir} by default; no fsync, because a crash loses the process and its heap index
 * together and the page cache serves this process's reads of bytes it wrote; and the directory deleted at close().
 * It departs from that store in four ways, each for the budget's sake:
 * <ul>
 *   <li>the directory can be NAMED ({@code GATEWAY_VOL_PREMIUM_LOG_DIR}), so the deployment can give it a sized
 *       volume;</li>
 *   <li>an I/O failure is never swallowed. SellerActivityDiskStore.readChain returns an empty list on one; here the
 *       store fails the session CLOSED, counts it and logs it;</li>
 *   <li>every read is verified against the length and CRC32C the index recorded, so a truncated or corrupted file
 *       is a counted failure, never a wrong record forwarded as the producer's;</li>
 *   <li>superseded versions are reclaimed by rewriting the file ({@link #beginRewrite}), so disk use is bounded by
 *       live content, not by how often a position was replaced.</li>
 * </ul>
 */
final class VolPremiumSessionLog {

    /** The file operations the log needs: the real file system in production, a failing one in tests. */
    interface Io {
        /** A new, empty directory under {@code root} whose name starts with {@code prefix}. */
        Path createDirectory(Path root, String prefix) throws IOException;

        /** A NEW file (it must not exist), open for positional reads and writes. */
        LogFile create(Path file) throws IOException;

        /** Deletes the file if it exists. */
        void delete(Path file) throws IOException;

        /** Deletes {@code dir} and everything under it, if it exists. */
        void deleteTree(Path dir) throws IOException;

        /** The children of {@code root} whose names start with {@code prefix}; empty when root does not exist. */
        List<Path> children(Path root, String prefix) throws IOException;
    }

    /** One open log file: positional reads and writes, as FileChannel offers them. */
    interface LogFile {
        int write(ByteBuffer source, long position) throws IOException;

        int read(ByteBuffer target, long position) throws IOException;

        void close() throws IOException;
    }

    /** The production file system. */
    static final Io FILES = new Io() {
        @Override
        public Path createDirectory(Path root, String prefix) throws IOException {
            Files.createDirectories(root);
            Path dir = Files.createTempDirectory(root, prefix);
            // A JVM that exits without close() (a test run, a plain exit) still removes it. Registered before any
            // file in it, and deleteOnExit deletes in reverse order, so the files go first.
            dir.toFile().deleteOnExit();
            return dir;
        }

        @Override
        public LogFile create(Path file) throws IOException {
            FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
            // Two names per session at most (the generations alternate), and deleteOnExit keeps a set, so this
            // list stays bounded by the sessions a process opens.
            file.toFile().deleteOnExit();
            return new LogFile() {
                @Override
                public int write(ByteBuffer source, long position) throws IOException {
                    return channel.write(source, position);
                }

                @Override
                public int read(ByteBuffer target, long position) throws IOException {
                    return channel.read(target, position);
                }

                @Override
                public void close() throws IOException {
                    channel.close();
                }
            };
        }

        @Override
        public void delete(Path file) throws IOException {
            Files.deleteIfExists(file);
        }

        @Override
        public void deleteTree(Path dir) throws IOException {
            if (!Files.exists(dir)) {
                return;
            }
            IOException first = null;
            try (Stream<Path> paths = Files.walk(dir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException failed) {
                        if (first == null) {
                            first = failed;
                        }
                    }
                }
            }
            if (first != null) {
                throw first;
            }
        }

        @Override
        public List<Path> children(Path root, String prefix) throws IOException {
            if (!Files.isDirectory(root)) {
                return List.of();
            }
            try (Stream<Path> paths = Files.list(root)) {
                return paths.filter(p -> p.getFileName().toString().startsWith(prefix)).toList();
            }
        }
    };

    private final Io io;
    private final Path dir;
    private final String name;
    private int generation;
    private Path path;
    private LogFile file;
    /** Bytes written to the current generation; only ever moved past bytes that were written in full. */
    private long end;
    /** A superseded generation that could not be closed or deleted after a rewrite; taken by the store and counted. */
    private IOException retireFailure;

    /** Creates the session's first generation file in {@code dir}. */
    VolPremiumSessionLog(Io io, Path dir, String name) throws IOException {
        this.io = io;
        this.dir = dir;
        this.name = name;
        this.path = pathOf(0);
        this.file = io.create(path);
    }

    private Path pathOf(int generation) {
        return dir.resolve(name + ((generation & 1) == 0 ? ".a" : ".b") + ".log");
    }

    /** Bytes in the current file: every held version plus every superseded one not yet rewritten away. */
    long end() {
        return end;
    }

    Path path() {
        return path;
    }

    /**
     * Writes {@code bytes} whole at the end of the file and returns where they begin. The end moves only once
     * every byte is written, so a failed write leaves nothing the index can point at; the caller fails the session.
     */
    long append(byte[] bytes) throws IOException {
        long at = end;
        writeFully(file, bytes, at);
        end = at + bytes.length;
        return at;
    }

    /**
     * The bytes at {@code offset}, exactly {@code length} of them, verified against {@code crc}. A short file, an
     * I/O error or a checksum mismatch throws: the caller fails the session rather than forward what it read.
     */
    byte[] read(long offset, int length, int crc) throws IOException {
        return readFully(file, offset, length, crc);
    }

    /** CRC32C of a record's bytes, as the index records it. */
    static int crc(byte[] bytes) {
        CRC32C c = new CRC32C();
        c.update(bytes, 0, bytes.length);
        return (int) c.getValue();
    }

    /**
     * Starts the next generation: a new file the caller fills with the held versions, in the order it will
     * recompute their offsets in. The current file stays the log until {@link #commit}.
     */
    Rewrite beginRewrite() throws IOException {
        Path next = pathOf(generation + 1);
        io.delete(next);   // a leftover of an abandoned rewrite, if any
        return new Rewrite(next, io.create(next));
    }

    /** The new generation of a rewrite: sequential appends from offset 0. */
    final class Rewrite {
        private final Path target;
        private final LogFile out;
        private long written;

        private Rewrite(Path target, LogFile out) {
            this.target = target;
            this.out = out;
        }

        void append(byte[] bytes) throws IOException {
            writeFully(out, bytes, written);
            written += bytes.length;
        }
    }

    /**
     * Switches the log to the completed rewrite. The superseded file is then closed and deleted; if that fails the
     * switch still stands (every held version is in the new file), and the failure is kept for the store to count.
     */
    void commit(Rewrite rewrite) {
        LogFile old = file;
        Path oldPath = path;
        file = rewrite.out;
        path = rewrite.target;
        end = rewrite.written;
        generation++;
        try {
            old.close();
        } catch (IOException failed) {
            retireFailure = failed;
        }
        try {
            io.delete(oldPath);
        } catch (IOException failed) {
            retireFailure = failed;
        }
    }

    /** Abandons a rewrite: its partial file is closed and deleted; the current file, untouched, stays the log. */
    void abandon(Rewrite rewrite) {
        try {
            rewrite.out.close();
        } catch (IOException ignored) {
            // The file is deleted next; the store has already failed the session for the rewrite's failure.
        }
        try {
            io.delete(rewrite.target);
        } catch (IOException ignored) {
            // Removed with the process directory at close().
        }
    }

    /** The failure left by the last commit's retirement of the old file, once; null when there was none. */
    IOException takeRetireFailure() {
        IOException failure = retireFailure;
        retireFailure = null;
        return failure;
    }

    /** Closes and deletes the current file. Both are attempted; the first failure is thrown. */
    void delete() throws IOException {
        IOException first = null;
        try {
            file.close();
        } catch (IOException failed) {
            first = failed;
        }
        try {
            io.delete(path);
        } catch (IOException failed) {
            if (first == null) {
                first = failed;
            }
        }
        if (first != null) {
            throw first;
        }
    }

    private static void writeFully(LogFile target, byte[] bytes, long position) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long at = position;
        while (buffer.hasRemaining()) {
            int n = target.write(buffer, at);
            if (n <= 0) {
                throw new IOException("the log accepted no bytes at offset " + at);
            }
            at += n;
        }
    }

    private static byte[] readFully(LogFile source, long offset, int length, int crc) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        long at = offset;
        while (buffer.hasRemaining()) {
            int n = source.read(buffer, at);
            if (n <= 0) {
                throw new IOException("the log is shorter than the index says: " + length + " bytes at offset "
                        + offset + " end at " + at);
            }
            at += n;
        }
        byte[] bytes = buffer.array();
        int actual = crc(bytes);
        if (actual != crc) {
            throw new IOException("CRC32C mismatch for " + length + " bytes at offset " + offset + ": the index says "
                    + Integer.toHexString(crc) + ", the file holds " + Integer.toHexString(actual));
        }
        return bytes;
    }
}
