package app.feedgateway;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ephemeral disk-backed latest-value store for the large per-strike seller histories.
 *
 * <p>The Kafka compacted topic is authoritative and repopulates this store after every pod start. Keeping
 * the values on disk prevents a full session of strike histories from occupying the gateway heap.</p>
 */
final class SellerActivityDiskStore implements AutoCloseable {
    record Stored(String key, String json, long eventTimeMs) {}

    private final Path root;

    SellerActivityDiskStore() {
        try {
            root = Files.createTempDirectory("options-edge-seller-activity-");
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create seller-activity disk store", e);
        }
    }

    synchronized void put(String key, String json, long eventTimeMs) {
        String[] parts = key == null ? new String[0] : key.split("\\|", -1);
        if (parts.length != 4 || json == null || json.isBlank()) {
            return;
        }
        Path chain = root.resolve(safe(parts[0])).resolve(safe(parts[1])).resolve(safe(parts[2]));
        Path target = chain.resolve(safe(parts[3]) + ".json");
        Path temporary = chain.resolve(safe(parts[3]) + ".tmp");
        try {
            Files.createDirectories(chain);
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            Files.setLastModifiedTime(target, FileTime.fromMillis(eventTimeMs));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to persist seller-activity record", e);
        }
    }

    List<Stored> readChain(String source, String symbol, String expiry, long oldestEventTimeMs,
                           int maxRecords, int maxBytes) {
        Path chain = root.resolve(safe(source)).resolve(safe(symbol)).resolve(safe(expiry));
        if (!Files.isDirectory(chain)) {
            return List.of();
        }
        List<Stored> result = new ArrayList<>();
        int bytes = 0;
        try (var paths = Files.list(chain)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                long eventTime = Files.getLastModifiedTime(path).toMillis();
                if (eventTime < oldestEventTimeMs) {
                    Files.deleteIfExists(path);
                    continue;
                }
                long size = Files.size(path);
                if (result.size() >= maxRecords || size > maxBytes - bytes) {
                    break;
                }
                String json = Files.readString(path, StandardCharsets.UTF_8);
                String strike = path.getFileName().toString().replaceFirst("\\.json$", "");
                result.add(new Stored(source + "|" + symbol + "|" + expiry + "|" + strike, json, eventTime));
                bytes += (int) size;
            }
        } catch (IOException e) {
            return List.of();
        }
        return result;
    }

    synchronized void remove(String key) {
        String[] parts = key == null ? new String[0] : key.split("\\|", -1);
        if (parts.length != 4) {
            return;
        }
        try {
            Files.deleteIfExists(root.resolve(safe(parts[0])).resolve(safe(parts[1]))
                    .resolve(safe(parts[2])).resolve(safe(parts[3]) + ".json"));
        } catch (IOException ignored) {
            // A later compacted-topic record will recreate it.
        }
    }

    @Override
    public void close() {
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Pod-local temporary storage is reclaimed with the pod.
                }
            });
        } catch (IOException ignored) {
            // Pod-local temporary storage is reclaimed with the pod.
        }
    }

    private static String safe(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (normalized.isEmpty() || !normalized.matches("[A-Z0-9._-]+")) {
            throw new IllegalArgumentException("Unsafe seller-activity disk key");
        }
        return normalized;
    }
}
