package local.myhome.marketresearch.tools;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.LongSupplier;
import tools.jackson.databind.ObjectMapper;

/** Compatible with the existing JSON calls/quota/blocked_until ledger (epoch seconds). */
public final class QuotaLedger {
    public static final int DAILY_REQUEST_LIMIT = 4500;
    @FunctionalInterface public interface Sleeper { void sleep(long millis) throws InterruptedException; }
    @FunctionalInterface public interface Work<T> { T run(Budget budget) throws Exception; }
    private final Path directory;
    private final ObjectMapper json;
    private final LongSupplier clock;
    private final Sleeper sleeper;

    public QuotaLedger(Path directory, ObjectMapper json, LongSupplier clock, Sleeper sleeper) {
        this.directory = directory; this.json = json; this.clock = clock; this.sleeper = sleeper;
    }
    public <T> T locked(int cap, int required, Work<T> work) throws Exception {
        if(cap<1||cap>DAILY_REQUEST_LIMIT)throw new ToolFailure("Etsy local daily limit must be between 1 and 4500");
        PrivateJson.directory(directory);
        try (var channel = FileChannel.open(directory.resolve("budget.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            try (var lock = channel.tryLock()) {
                if (lock == null) throw new ProviderBusy();
                var budget = new Budget(cap);
                budget.requireCapacity(required);
                return work.run(budget);
            } catch (OverlappingFileLockException e) {
                throw new ProviderBusy();
            }
        }
    }

    public final class Budget {
        private final int cap;
        private final List<Double> calls = new ArrayList<>();
        private Map<String,String> quota = new TreeMap<>();
        private double blockedUntil;

        @SuppressWarnings("unchecked")
        Budget(int cap) throws IOException {
            this.cap = cap;
            Path path = directory.resolve("budget.json");
            if (Files.exists(path)) {
                Map<String,Object> state = json.readValue(Files.readString(path), Map.class);
                for (Object value : (List<?>)state.getOrDefault("calls", List.of())) {
                    double timestamp = ((Number)value).doubleValue();
                    if (!Double.isFinite(timestamp)) throw new ToolFailure("Invalid request ledger; no request sent.");
                    calls.add(timestamp);
                }
                var rawQuota = (Map<?,?>)state.getOrDefault("quota", Map.of());
                rawQuota.forEach((k,v) -> quota.put(k.toString(),v.toString()));
                blockedUntil = ((Number)state.getOrDefault("blocked_until", 0)).doubleValue();
            }
            prune();
        }
        private double now() { return clock.getAsLong()/1000.0; }
        private void prune() { calls.removeIf(timestamp -> timestamp <= now()-86400); }
        public int priorAttempts() { prune(); return calls.size(); }
        public Map<String,String> quota() { return Map.copyOf(quota); }
        public void requireCapacity(int required) {
            prune();
            if (required < 1 || required>cap)throw new ToolFailure("Invalid request reservation");
            if(calls.size()+required>cap)throw new ProviderDeferred((long)((Collections.min(calls)+86401)*1000));
            if (blockedUntil > now()) throw new ProviderDeferred((long)(blockedUntil*1000));
        }
        public void reserve(boolean probe) throws Exception {
            requireCapacity(1);
            if (!probe && (number("x-limit-per-second") < 1 || number("x-remaining-today") <= 10))
                throw new ToolFailure("Quota unavailable or below daily reserve; no search sent.");
            if (!calls.isEmpty()) {
                long wait = Math.max(0, (long)Math.ceil((Collections.max(calls)+2-now())*1000));
                if (wait > 60_000) throw new ToolFailure("Request ledger is ahead of the clock; no request sent.");
                if (wait > 0) sleeper.sleep(wait);
            }
            calls.add(now());
            save(); // Count uncertain/failed attempts before doing network I/O.
        }
        public long number(String name) {
            try { return Long.parseLong(quota.getOrDefault(name,"0")); }
            catch (NumberFormatException e) { return 0; }
        }
        public void reconcile(Map<String,String> headers, int status) throws IOException {
            quota = new TreeMap<>(headers);
            if (status == 429) {
                long seconds = 3600;
                try { seconds = Long.parseLong(headers.getOrDefault("retry-after","60")); }
                catch (NumberFormatException e) {
                    try {
                        seconds = Duration.between(Instant.ofEpochMilli(clock.getAsLong()),
                            ZonedDateTime.parse(headers.get("retry-after"), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds();
                    } catch (RuntimeException ignored) { /* Fail conservatively. */ }
                }
                blockedUntil = now()+Math.max(60,Math.min(seconds,86400));
            }
            save();
        }
        private void save() throws IOException {
            PrivateJson.write(directory.resolve("budget.json"),
                Map.of("calls",calls,"quota",quota,"blocked_until",blockedUntil),json);
        }
    }
}
