package app.feedgateway;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;

/**
 * ES-FOOTPRINT-GATEWAY-DESIGN.md G-R8a — topic validation BEFORE consumption, on the consumer's own
 * thread. A footprint topic is VALID iff its effective {@code max.message.bytes} is at or under the
 * ceiling AND its effective {@code compression.type} is {@code producer} or {@code uncompressed}
 * (fact (iii) of G-R8: a fetched byte is a decompressed byte). Start-up validates the topics that
 * EXIST (an existing invalid topic refuses start-up; an absent one is deferred; any other Admin
 * failure refuses start-up); afterwards {@link #admit} is the {@code PartitionRefresh} admission
 * predicate: a topic is assigned only once validated, re-checked at every refresh until then. Once
 * VALID a topic stays VALID for the incarnation. Per-topic state is a {@link ConcurrentHashMap}
 * shared by both state consumers (each evaluates on its own thread; a duplicate validation is
 * harmless).
 */
final class FootprintTopicGate implements Predicate<String> {

    /** G-R9 failure reasons, in precedence order: admin → unknown → ceiling → compression. */
    enum Reason { admin, unknown, ceiling, compression }

    /** The two effective topic configs the gate reads. */
    record TopicConfigView(long maxMessageBytes, String compressionType) { }

    /** Thrown by a reader when the topic does not exist. */
    static final class UnknownTopic extends Exception {
        UnknownTopic(String topic) { super("unknown topic " + topic); }
    }

    /** Thrown by a reader for every other Admin failure (timeout, authorization, broker unreachable). */
    static final class AdminFailure extends Exception {
        AdminFailure(String message, Throwable cause) { super(message, cause); }
    }

    /** Reader seam: the real one wraps {@code AdminClient.describeConfigs}; tests supply a map. */
    interface ConfigReader {
        TopicConfigView read(String topic) throws UnknownTopic, AdminFailure;
    }

    private static final Set<String> UNCOMPRESSED = Set.of("producer", "uncompressed");

    private final Set<String> topics;
    private final long ceiling;
    private final ConfigReader reader;
    private final Map<String, Boolean> validated = new ConcurrentHashMap<>();
    private final Map<String, Map<Reason, AtomicLong>> failures = new ConcurrentHashMap<>();
    private final Map<String, String> lastLogged = new ConcurrentHashMap<>();

    FootprintTopicGate(Collection<String> footprintTopics, long maxMessageBytesCeiling, ConfigReader reader) {
        if (maxMessageBytesCeiling <= 0) throw new IllegalArgumentException("ceiling must be positive");
        this.topics = Set.copyOf(footprintTopics);
        this.ceiling = maxMessageBytesCeiling;
        this.reader = reader;
        for (String t : topics) {
            validated.put(t, Boolean.FALSE);
            Map<Reason, AtomicLong> m = new EnumMap<>(Reason.class);
            for (Reason r : Reason.values()) m.put(r, new AtomicLong());
            failures.put(t, m);
        }
    }

    Set<String> topics() { return topics; }

    /**
     * Start-up (G-R8a): validate the topics that EXIST. Throws {@link IllegalStateException} for an
     * existing invalid topic or for any Admin failure other than "unknown topic"; an absent topic is
     * simply left unvalidated (the consumers' refresh path admits it once it appears and validates).
     */
    void validateExisting() {
        List<String> refusals = new ArrayList<>();
        for (String topic : topics) {
            Outcome o = attempt(topic);
            if (o.valid) continue;
            if (o.reason == Reason.unknown) continue;
            refusals.add(topic + ": " + o.reason + " (" + o.detail + ")");
        }
        if (!refusals.isEmpty()) throw new IllegalStateException("ES_FOOTPRINT_TOPIC_INVALID " + refusals);
    }

    /** The {@code PartitionRefresh} admission predicate (G-R8a): non-footprint topics always pass. */
    boolean admit(String topic) {
        if (!topics.contains(topic)) return true;
        if (Boolean.TRUE.equals(validated.get(topic))) return true;
        return attempt(topic).valid;
    }

    @Override public boolean test(String topic) { return admit(topic); }

    private record Outcome(boolean valid, Reason reason, String detail) { }

    private Outcome attempt(String topic) {
        Outcome o;
        try {
            TopicConfigView cfg = reader.read(topic);
            if (cfg == null) o = new Outcome(false, Reason.admin, "no config returned");
            else if (cfg.maxMessageBytes() > ceiling) o = new Outcome(false, Reason.ceiling, "max.message.bytes=" + cfg.maxMessageBytes() + " > " + ceiling);
            else if (cfg.compressionType() == null || !UNCOMPRESSED.contains(cfg.compressionType().trim().toLowerCase())) o = new Outcome(false, Reason.compression, "compression.type=" + cfg.compressionType());
            else o = new Outcome(true, null, null);
        } catch (UnknownTopic e) {
            o = new Outcome(false, Reason.unknown, e.getMessage());
        } catch (AdminFailure e) {
            o = new Outcome(false, Reason.admin, e.getMessage());
        } catch (RuntimeException e) {
            o = new Outcome(false, Reason.admin, e.toString());
        }
        if (o.valid) {
            validated.put(topic, Boolean.TRUE);
            if (!"valid".equals(lastLogged.put(topic, "valid"))) System.out.println("Feed gateway footprint topic validated: " + topic);
        } else {
            failures.get(topic).get(o.reason).incrementAndGet();
            String state = o.reason + ":" + o.detail;
            if (!state.equals(lastLogged.put(topic, state))) {         // log the TRANSITION only
                System.err.println("RGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED " + topic + " " + o.reason + " " + o.detail);
            }
        }
        return o;
    }

    boolean validated(String topic) { return Boolean.TRUE.equals(validated.get(topic)); }
    long failures(String topic, Reason reason) { Map<Reason, AtomicLong> m = failures.get(topic); return m == null ? 0 : m.get(reason).get(); }

    // ---- G-R8 layout verification -----------------------------------------------------------------

    static final List<String> LAYOUT_FLAGS = List.of("UseCompressedOops", "UseCompressedClassPointers", "CompactStrings");

    /**
     * The three HotSpot flags the G-R8 retained-heap arithmetic assumes. Returns the first flag that is
     * not {@code true} (or is unreadable), empty when all hold. {@code vmOption} is a seam over
     * {@code HotSpotDiagnosticMXBean.getVMOption(name).getValue()}.
     */
    static Optional<String> layoutViolation(Function<String, String> vmOption) {
        for (String flag : LAYOUT_FLAGS) {
            String v;
            try { v = vmOption.apply(flag); } catch (RuntimeException e) { return Optional.of(flag + " (unreadable: " + e + ")"); }
            if (!"true".equalsIgnoreCase(v == null ? "" : v.trim())) return Optional.of(flag + "=" + v);
        }
        return Optional.empty();
    }

    static Function<String, String> hotSpotVmOptions() {
        com.sun.management.HotSpotDiagnosticMXBean bean =
                ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class);
        return name -> bean.getVMOption(name).getValue();
    }

    // ---- the real reader --------------------------------------------------------------------------

    /** {@code AdminClient.describeConfigs} for one topic, bounded by {@code timeoutMs}; defaults included. */
    static ConfigReader adminReader(String bootstrapServers, long timeoutMs) {
        return topic -> {
            Properties props = new Properties();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, Integer.toString((int) Math.min(Integer.MAX_VALUE, timeoutMs)));
            props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, Integer.toString((int) Math.min(Integer.MAX_VALUE, timeoutMs)));
            ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topic);
            try (AdminClient admin = AdminClient.create(props)) {
                Config cfg = admin.describeConfigs(List.of(resource)).all().get(timeoutMs, TimeUnit.MILLISECONDS).get(resource);
                if (cfg == null) throw new AdminFailure("describeConfigs returned no config for " + topic, null);
                ConfigEntry mmb = cfg.get(TopicConfig.MAX_MESSAGE_BYTES_CONFIG);
                ConfigEntry ct = cfg.get(TopicConfig.COMPRESSION_TYPE_CONFIG);
                if (mmb == null || mmb.value() == null || ct == null || ct.value() == null) {
                    throw new AdminFailure("describeConfigs omitted max.message.bytes/compression.type for " + topic, null);
                }
                return new TopicConfigView(Long.parseLong(mmb.value().trim()), ct.value());
            } catch (ExecutionException e) {
                if (e.getCause() instanceof UnknownTopicOrPartitionException) throw new UnknownTopic(topic);
                throw new AdminFailure("describeConfigs failed for " + topic + ": " + e.getCause(), e);
            } catch (TimeoutException e) {
                throw new AdminFailure("describeConfigs timed out for " + topic, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AdminFailure("interrupted reading " + topic, e);
            } catch (NumberFormatException e) {
                throw new AdminFailure("unparseable max.message.bytes for " + topic, e);
            }
        };
    }
}
