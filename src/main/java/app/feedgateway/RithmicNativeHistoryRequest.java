package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/** Opt-in full-book request contract. A price-sliced legacy request cannot acquire this format. */
final class RithmicNativeHistoryRequest {
    static final String FORMAT = "rithmic-native-fullbook-gzip-v1";
    static final long MAX_SAFE = 9_007_199_254_740_991L;
    static final long MAX_WINDOW_MS = 7_220_000L;
    private static final Set<String> ROOT_FIELDS = Set.of(
            "type", "format", "namespace", "requestId", "fromMs", "toMs", "afterMs");
    private static final Set<String> SCOPE_FIELDS = Set.of("environment", "exchange", "contract");
    private static final Set<String> SYSTEMS = Set.of("Rithmic Test", "Rithmic Paper Trading");

    record Scope(String environment, String exchange, String contract) {
        Scope {
            if (!supportedSystem(environment) || !"CME".equals(exchange) || !"ESZ6".equals(contract)) {
                throw new IllegalArgumentException("Unsupported depth scope");
            }
        }
    }

    record Request(String requestId, Scope namespace, long fromMs, long toMs, long afterMs) {
        Request {
            if (requestId == null || requestId.isEmpty() || requestId.length() > 64 || namespace == null
                    || fromMs < 0 || toMs < fromMs || toMs > MAX_SAFE || toMs - fromMs > MAX_WINDOW_MS
                    || afterMs < fromMs - 1 || afterMs > toMs) {
                throw new IllegalArgumentException("Invalid depth history request");
            }
        }
    }

    record Result(Request request, String requestId, String error) {
        boolean accepted() { return request != null; }
    }

    private RithmicNativeHistoryRequest() { }

    static boolean supportedSystem(String value) {
        return value != null && SYSTEMS.contains(value);
    }

    static Result parse(JsonNode root, String configuredSystem, long nowMs) {
        String id = safeId(root);
        if (!supportedSystem(configuredSystem) || nowMs < 0 || nowMs > MAX_SAFE) {
            return new Result(null, id, "unavailable");
        }
        if (!objectFields(root, ROOT_FIELDS)
                || !text(root.get("type"), RithmicDepthHistory.REQUEST)
                || !text(root.get("format"), FORMAT) || id.isEmpty()) {
            return new Result(null, id, "invalid_request");
        }
        JsonNode namespace = root.get("namespace");
        if (!objectFields(namespace, SCOPE_FIELDS)
                || !namespace.path("environment").isTextual()
                || !namespace.path("exchange").isTextual()
                || !namespace.path("contract").isTextual()) {
            return new Result(null, id, "invalid_request");
        }
        if (!text(namespace.get("environment"), configuredSystem)
                || !text(namespace.get("exchange"), "CME") || !text(namespace.get("contract"), "ESZ6")) {
            return new Result(null, id, "unsupported_namespace");
        }
        for (String field : new String[] {"fromMs", "toMs", "afterMs"}) {
            JsonNode value = root.get(field);
            if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                    || value.longValue() < -1 || value.longValue() > MAX_SAFE) {
                return new Result(null, id, "invalid_request");
            }
        }
        long from = root.path("fromMs").longValue(), to = root.path("toMs").longValue(),
                after = root.path("afterMs").longValue();
        if (to > nowMs + 5_000L) return new Result(null, id, "invalid_request");
        try {
            return new Result(new Request(id, new Scope(configuredSystem, "CME", "ESZ6"), from, to, after), id, null);
        } catch (IllegalArgumentException invalid) {
            return new Result(null, id, "invalid_request");
        }
    }

    private static boolean objectFields(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject() || node.size() != allowed.size()) return false;
        var fields = node.fieldNames();
        while (fields.hasNext()) if (!allowed.contains(fields.next())) return false;
        return true;
    }

    private static boolean text(JsonNode node, String expected) {
        return node != null && node.isTextual() && expected.equals(node.textValue());
    }

    private static String safeId(JsonNode root) {
        if (root == null || !root.isObject()) return "";
        JsonNode id = root.get("requestId");
        return id != null && id.isTextual() && !id.textValue().isEmpty() && id.textValue().length() <= 64
                ? id.textValue() : "";
    }
}
