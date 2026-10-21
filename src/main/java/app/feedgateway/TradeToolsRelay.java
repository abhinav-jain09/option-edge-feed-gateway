package app.feedgateway;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.generic.GenericRecord;

/**
 * Phase D trade-level tools (ta-books-catalog/06_TRADE_TOOLS_FRAME_CONTRACT.md): twelve display records of es-trade-tools-service, one frame
 * type each, dispatched on the WRITER schema's record name. The gateway checks the boundary only (record type for the topic, schema
 * version 1, a symbol, {@code validation == UNVALIDATED}, a horizon or width of 1m/5m/15m, size under 64 KiB, per-key ordering by
 * {@code asOfMs}); the page is the full validator. Records are keyed upserts. A column or event is routed to the timeframe of the retained
 * {@code PfChartState} of its {@code chartId} and dropped (counted) when there is none; volume node sets belong to all three timeframes.
 * Retained per timeframe, bounded: 4 charts, 24 columns per chart, 10 events per kind, 32 flow bars, 8 climax events; 4 node sets overall.
 * The hello carries one bundle of twelve arrays per timeframe, whole, in the order 1m, 5m, 15m while it fits the part budget.
 */
final class TradeToolsRelay extends TaRelay {
    /** The binding events, one per producer topic, in the order of {@link GatewaySettings#tradeToolsTopics()}. */
    static final String[] ROLES = {"trade-tools-pf-chart", "trade-tools-pf-column", "trade-tools-pf-events", "trade-tools-volume-nodes",
        "trade-tools-flow-bar", "trade-tools-climax"};
    /** The frame type of each of the twelve records, by kind index. */
    static final String[] FRAME = {"trade-tools-pf-chart", "trade-tools-pf-column", "trade-tools-pf-break", "trade-tools-pf-line",
        "trade-tools-pf-count", "trade-tools-pf-macross", "trade-tools-pf-voldiv", "trade-tools-pf-mfdiv", "trade-tools-pf-volcheck",
        "trade-tools-volume-nodes", "trade-tools-flow-bar", "trade-tools-climax"};
    /** The bundle's array keys, in contract order, with the kind each carries. */
    private static final String[] ARRAY = {"pfCharts", "pfColumns", "pfBreaks", "pfLines", "pfCounts", "pfMaCrosses", "pfVolDivs", "pfMfDivs",
        "pfVolChecks", "flowBars", "climax", "nodes"};
    private static final int[] ARRAY_KIND = {0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 9};
    private static final String[] RECORD = {"PfChartState", "PfColumn", "PfBreakEvent", "PfLineEvent", "PfCountEvent", "PfMaCrossEvent",
        "PfVolumeDivergenceEvent", "PfMoneyFlowDivergenceEvent", "PfBreakVolumeCheck", "VolumeNodeSet", "FlowBar", "ClimaxEvent"};
    private static final String NAMESPACE = "com.optionsedge.contracts.tradetools";
    private static final String[] HORIZON = {"M1", "M5", "M15"};
    /** The hello part is held under 512 KiB of retained records. */
    static final long HELLO_PART_BYTES = 512L * 1024;
    static final int RECORD_MAX_BYTES = 64 * 1024;
    static final int CHARTS_PER_TF = 4;
    static final int COLUMNS_PER_CHART = 24;
    static final int EVENTS_PER_KIND = 10;
    static final int FLOW_BARS = 32;
    static final int CLIMAX = 8;
    static final int NODES = 4;
    /** Records read before their chart during a recovery wait here until the chart arrives (bounded; a live record never waits). */
    static final int ORPHANS = 2048;
    /** Records re-read from the end of each partition per role: the retained depth above plus the republished open items. */
    private static final int[] TAIL = {64, 2048, 1024, 32, 1024, 256};

    /** One accepted record: its routing, its ordering clock and the frame data it is sent as. */
    private record Held(int kind, int tf, String key, String chartId, String epoch, String json, int bytes, long asOf, long order, long sort) {
        Held at(int timeframe) {
            return new Held(kind, timeframe, key, chartId, epoch, json, bytes, asOf, order, sort);
        }
    }

    /** A bounded keyed set: per-key ordering by {@code asOf}, the entry with the smallest {@code order} leaves when it overflows. */
    private static final class Series {
        static final Comparator<Held> BY_ORDER = Comparator.comparingLong(Held::order).thenComparing(Held::key);
        static final Comparator<Held> BY_SORT = Comparator.comparingLong(Held::sort).thenComparing(Held::key);
        final int cap;
        final Map<String, Held> held = new LinkedHashMap<>();

        Series(int cap) {
            this.cap = cap;
        }

        /** 0 regression, 1 accepted (an equal clock replaces), 2 accepted and strictly newer or new. */
        int put(Held h) {
            Held old = held.get(h.key());
            if (old != null && h.asOf() < old.asOf()) {
                return 0;
            }
            held.put(h.key(), h);
            if (held.size() > cap) {
                Held evicted = held.values().stream().min(BY_ORDER).orElseThrow();
                held.remove(evicted.key());
                if (evicted == h) {
                    return 0;   // outranked by everything retained: not kept, so not forwarded either (a retained record that was replaced stays replaced)
                }
            }
            return old == null || h.asOf() > old.asOf() ? 2 : 1;
        }

        Series copy() {
            Series c = new Series(cap);
            c.held.putAll(held);
            return c;
        }

        List<Held> sorted() {
            List<Held> out = new ArrayList<>(held.values());
            out.sort(BY_SORT);
            return out;
        }
    }

    @SuppressWarnings("unchecked")
    private static final class Store {
        final Map<String, Held>[] charts = new Map[TF.length];
        final Map<String, Integer> chartTf = new HashMap<>();
        final Map<String, Series> columns = new HashMap<>();
        final Series[][] events = new Series[TF.length][ARRAY.length];
        final Series[] flow = new Series[TF.length];
        final Series[] climax = new Series[TF.length];
        final Series nodes = new Series(NODES);
        /** Only a recovery staging copy holds orphans. */
        final List<Held> orphans;
        long version;

        Store(boolean staging) {
            orphans = staging ? new ArrayList<>() : null;
            for (int t = 0; t < TF.length; t++) {
                charts[t] = new HashMap<>();
                flow[t] = new Series(FLOW_BARS);
                climax[t] = new Series(CLIMAX);
                for (int k = 2; k <= 8; k++) {
                    events[t][k] = new Series(EVENTS_PER_KIND);
                }
            }
        }

        Store copyForStaging() {
            Store c = new Store(true);
            for (int t = 0; t < TF.length; t++) {
                c.charts[t].putAll(charts[t]);
                c.flow[t] = flow[t].copy();
                c.climax[t] = climax[t].copy();
                for (int k = 2; k <= 8; k++) {
                    c.events[t][k] = events[t][k].copy();
                }
            }
            c.chartTf.putAll(chartTf);
            columns.forEach((id, s) -> c.columns.put(id, s.copy()));
            c.nodes.held.putAll(nodes.held);
            c.version = version;
            return c;
        }

        void removeChart(String chartId) {
            Integer t = chartTf.remove(chartId);
            if (t != null) {
                charts[t].remove(chartId);
                columns.remove(chartId);
                purgeEvents(chartId, null);
            }
        }

        /** Drops the P&F events of {@code chartId} (of every epoch but {@code keepEpoch}, when given): an event never outlives the chart or contract epoch it belongs to. */
        void purgeEvents(String chartId, String keepEpoch) {
            for (int t = 0; t < TF.length; t++) {
                for (int k = 2; k <= 8; k++) {
                    events[t][k].held.values().removeIf(e -> e.chartId().equals(chartId) && (keepEpoch == null || !e.epoch().equals(keepEpoch)));
                }
            }
        }

        /** Every held record in promotion order: charts, columns, events, nodes, flow bars, climax. */
        List<Held> all() {
            List<Held> out = new ArrayList<>();
            for (int t = 0; t < TF.length; t++) {
                out.addAll(charts[t].values());
            }
            columns.values().forEach(s -> out.addAll(s.sorted()));
            for (int t = 0; t < TF.length; t++) {
                for (int k = 2; k <= 8; k++) {
                    out.addAll(events[t][k].sorted());
                }
            }
            out.addAll(nodes.sorted());
            for (int t = 0; t < TF.length; t++) {
                out.addAll(flow[t].sorted());
                out.addAll(climax[t].sorted());
            }
            return out;
        }

        /** The put result for a record that has a timeframe (or, for a column or event, a retained chart), -1 when it has no home. */
        int insert(Held record) {
            Held h = record;
            if (h.kind() >= 1 && h.kind() <= 8) {
                Integer t = chartTf.get(h.chartId());
                if (t == null) {
                    return -1;
                }
                h = h.at(t);
            }
            int result;
            switch (h.kind()) {
                case 0 -> {
                    Integer previous = chartTf.get(h.key());
                    Held old = previous == null ? null : charts[previous].get(h.key());
                    if (old != null && h.asOf() < old.asOf()) {
                        return 0;
                    }
                    if (previous != null && previous != h.tf()) {
                        removeChart(h.key());   // a chart never changes horizon; a newer record of another horizon replaces it whole
                        old = null;
                    }
                    if (old == null && charts[h.tf()].size() == CHARTS_PER_TF) {
                        Held oldest = charts[h.tf()].values().stream().min(Comparator.comparingLong(Held::asOf)).orElseThrow();
                        if (h.asOf() < oldest.asOf()) {
                            return 0;   // a delayed chart older than every retained one never evicts a newer chart
                        }
                        removeChart(oldest.key());
                    }
                    if (old != null && !old.epoch().equals(h.epoch())) {
                        columns.remove(h.key());   // a new contract epoch starts the chart's columns afresh
                        purgeEvents(h.key(), h.epoch());   // and its events
                    }
                    charts[h.tf()].put(h.key(), h);
                    chartTf.put(h.key(), h.tf());
                    result = old == null || h.asOf() > old.asOf() ? 2 : 1;
                }
                case 1 -> result = columns.computeIfAbsent(h.chartId(), id -> new Series(COLUMNS_PER_CHART)).put(h);
                case 9 -> result = nodes.put(h);
                case 10 -> result = flow[h.tf()].put(h);
                case 11 -> result = climax[h.tf()].put(h);
                default -> result = events[h.tf()][h.kind()].put(h);
            }
            if (result > 0) {
                version++;
                if (h.kind() == 0 && orphans != null) {
                    retryOrphans(h.key());
                }
            }
            return result;
        }

        private void retryOrphans(String chartId) {
            for (var it = orphans.iterator(); it.hasNext();) {
                Held o = it.next();
                if (o.chartId().equals(chartId)) {
                    it.remove();
                    insert(o);
                }
            }
        }
    }

    private Store live = new Store(false);
    private Store staged;
    private final String[] helloBundle = new String[TF.length];
    private final long[] helloBytes = new long[TF.length];
    private long helloVersion = -1L;
    private String helloCache;
    private long helloCap = -1L;

    TradeToolsRelay() {
        super("trade_tools");
    }

    @Override
    boolean handles(String bindingEvent) {
        return indexOf(ROLES, bindingEvent) >= 0;
    }

    @Override
    int tailRecords(String bindingEvent) {
        return TAIL[indexOf(ROLES, bindingEvent)];
    }

    @Override
    synchronized String[] onLive(String bindingEvent, Object value) {
        Held h = parse(bindingEvent, value);
        if (h == null) {
            return null;
        }
        int result = live.insert(h);
        if (result < 0) {
            onDrop();   // a column or event of a chart the gateway does not hold: never guessed
            return null;
        }
        if (result == 0) {
            regressed();
            return null;
        }
        accepted();
        return new String[] {FRAME[h.kind()], h.json()};
    }

    @Override
    synchronized void beginRecovery() {
        staged = live.copyForStaging();
    }

    @Override
    synchronized void onStaged(String bindingEvent, Object value) {
        Held h = parse(bindingEvent, value);
        if (h == null) {
            return;
        }
        int result = staged.insert(h);
        if (result < 0 && staged.orphans.size() < ORPHANS) {
            staged.orphans.add(h);   // its chart may still be read later in this recovery
        } else if (result < 0) {
            onDrop();
        } else if (result == 0) {
            regressed();
        } else {
            accepted();
        }
    }

    @Override
    synchronized List<String[]> promote() {
        // Only a record AFTER live promotes (an equal one never does): staged is a copy of live plus the history below the captured end offsets,
        // so on an equal ordering key the live record is the later one in log order and stays.
        List<String[]> frames = new ArrayList<>();
        if (staged != null) {
            for (int i = 0; i < staged.orphans.size(); i++) {
                onDrop();   // the chart never arrived within the recovery read
            }
            for (Held h : staged.all()) {
                if (live.insert(h) == 2) {
                    frames.add(new String[] {FRAME[h.kind()], h.json()});
                }
            }
        }
        staged = null;
        return frames;
    }

    @Override
    synchronized String helloField(long budget) {
        long cap = Math.min(budget, HELLO_PART_BYTES);
        if (helloVersion != live.version) {
            for (int t = 0; t < TF.length; t++) {
                helloBundle[t] = bundle(t);
            }
            helloVersion = live.version;
            helloCache = null;
        }
        if (helloCache == null || helloCap != cap) {
            helloCache = renderHello(cap);
            helloCap = cap;
        }
        return helloCache;
    }

    private String renderHello(long cap) {
        StringBuilder sb = new StringBuilder(",\"tradeTools\":{");
        long used = 0;
        boolean fits = true;
        for (int t = 0; t < TF.length; t++) {
            sb.append(t == 0 ? "" : ",").append('"').append(TF[t]).append("\":");
            if (fits && helloBundle[t] != null && used + helloBytes[t] <= cap) {
                used += helloBytes[t];
                sb.append(helloBundle[t]);
            } else {
                fits &= helloBundle[t] == null;   // the first retained bundle that does not fit ends the list: the rest are null
                sb.append("null");
            }
        }
        return sb.append('}').toString();
    }

    /** The twelve-array bundle of one timeframe, or null when nothing is retained for it; sets {@code helloBytes[t]}. */
    private String bundle(int t) {
        List<List<Held>> arrays = new ArrayList<>(ARRAY.length);
        Map<String, String> chartEpoch = new HashMap<>();
        List<Held> charts = new ArrayList<>(live.charts[t].values());
        charts.sort(Comparator.comparing(Held::key));
        charts.forEach(c -> chartEpoch.put(c.key(), c.epoch()));
        List<Held> flow = live.flow[t].sorted();
        String flowEpoch = flow.isEmpty() ? null : flow.get(flow.size() - 1).epoch();
        long bytes = 0;
        boolean any = false;
        for (int a = 0; a < ARRAY.length; a++) {
            int kind = ARRAY_KIND[a];
            List<Held> items = switch (kind) {
                case 0 -> charts;
                case 1 -> columnsOf(charts, chartEpoch);
                case 9 -> live.nodes.sorted();
                case 10 -> flow.stream().filter(h -> h.epoch().equals(flowEpoch)).toList();
                case 11 -> live.climax[t].sorted();
                default -> live.events[t][kind].sorted();
            };
            any |= !items.isEmpty();
            arrays.add(items);
        }
        if (!any) {
            helloBytes[t] = 0;
            return null;
        }
        StringBuilder sb = new StringBuilder(256).append('{');
        for (int a = 0; a < ARRAY.length; a++) {
            sb.append(a == 0 ? "" : ",").append('"').append(ARRAY[a]).append("\":[");
            List<Held> items = arrays.get(a);
            for (int i = 0; i < items.size(); i++) {
                sb.append(i == 0 ? "" : ",").append(items.get(i).json());
                bytes += items.get(i).bytes() + 1;
            }
            sb.append(']');
        }
        helloBytes[t] = bytes;
        return sb.append('}').toString();
    }

    /** The columns of every chart of the timeframe that belong to the chart's current contract epoch, ascending by column index. */
    private List<Held> columnsOf(List<Held> charts, Map<String, String> chartEpoch) {
        List<Held> out = new ArrayList<>();
        for (Held chart : charts) {
            Series s = live.columns.get(chart.key());
            if (s != null) {
                s.sorted().stream().filter(c -> c.epoch().equals(chartEpoch.get(chart.key()))).forEach(out::add);
            }
        }
        return out;
    }

    /** The boundary: null (counted) for anything the gateway must not relay, else the rendered record with its routing and clock. */
    private Held parse(String bindingEvent, Object value) {
        GenericRecord r = recordOrDrop(value);
        if (r == null) {
            return null;
        }
        try {
            int kind = NAMESPACE.equals(r.getSchema().getNamespace()) ? indexOf(RECORD, r.getSchema().getName()) : -1;
            long asOf = (Long) r.get("asOfMs");
            if (kind < 0 || !roleHas(indexOf(ROLES, bindingEvent), kind) || !Integer.valueOf(1).equals(r.get("schemaVersion"))
                    || r.get("symbol").toString().isEmpty() || !"UNVALIDATED".equals(r.get("validation").toString()) || asOf < 0) {
                onDrop();
                return null;
            }
            int tf = kind == 0 ? indexOf(HORIZON, r.get("horizon").toString()) : kind >= 10 ? indexOf(HORIZON, r.get("width").toString()) : -1;
            if ((kind == 0 || kind >= 10) && tf < 0) {
                onDrop();   // H1 and H4 have no page timeframe
                return null;
            }
            String json = AvroFrameJson.render(r, RECORD_MAX_BYTES, false);
            int bytes = json == null ? Integer.MAX_VALUE : StudiesRelay.utf8Length(json);
            if (bytes > RECORD_MAX_BYTES) {
                onDrop();
                return null;
            }
            String epoch = r.get("contractEpoch").toString();
            return switch (kind) {
                case 0 -> new Held(kind, tf, r.get("chartId").toString(), null, epoch, json, bytes, asOf, asOf, asOf);
                case 1 -> new Held(kind, -1, r.get("chartId") + "|" + epoch + "|" + r.get("columnIndex"), r.get("chartId").toString(), epoch,
                        json, bytes, asOf, asOf, (Long) r.get("columnIndex"));
                case 9 -> new Held(kind, -1, r.get("scope") + "|" + r.get("windowSessions"), null, epoch, json, bytes, asOf, asOf,
                        ("GLOBEX".equals(r.get("scope").toString()) ? 0L : 1L) * 1_000_000L + (Integer) r.get("windowSessions"));
                case 10 -> new Held(kind, tf, r.get("width") + "|" + epoch + "|" + r.get("barStartMs"), null, epoch, json, bytes, asOf,
                        (Long) r.get("barStartMs"), (Long) r.get("barStartMs"));
                case 11 -> new Held(kind, tf, r.get("climaxId").toString(), null, epoch, json, bytes, asOf, (Long) r.get("candidateBarStartMs"),
                        (Long) r.get("candidateBarStartMs"));
                default -> new Held(kind, -1, r.get("eventKey").toString(), r.get("chartId").toString(), epoch, json, bytes, asOf,
                        (Long) r.get("eventTimeMs"), (Long) r.get("eventTimeMs"));
            };
        } catch (RuntimeException e) {
            onDrop();   // a missing or mistyped field is refused, never repaired
            return null;
        }
    }

    /** Whether the topic role carries records of this kind (the seven P&F events share one topic). */
    private static boolean roleHas(int role, int kind) {
        return switch (role) {
            case 0 -> kind == 0;
            case 1 -> kind == 1;
            case 2 -> kind >= 2 && kind <= 8;
            case 3 -> kind == 9;
            case 4 -> kind == 10;
            case 5 -> kind == 11;
            default -> false;
        };
    }
}
