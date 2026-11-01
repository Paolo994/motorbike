package com.motorbike.fuel;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.motorbike.config.AppProperties;

/** Importa anagrafica distributori e prezzi della benzina dagli open data MIMIT (aggiornati ogni giorno). */
@Service
public class FuelImportService {

    private static final Logger log = LoggerFactory.getLogger(FuelImportService.class);
    private static final int BATCH = 1000;

    public record ImportRun(Instant startedAt, Instant finishedAt, String status, Integer stations, Integer prices,
                            String error) {
    }

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private final JdbcTemplate jdbcTemplate;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final AppProperties props;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public FuelImportService(JdbcTemplate jdbcTemplate, JdbcClient jdbc, TransactionTemplate tx, AppProperties props) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbc = jdbc;
        this.tx = tx;
        this.props = props;
    }

    public boolean isRunning() {
        return running.get();
    }

    @Async
    public void importAllAsync() {
        importAll();
    }

    public void importAll() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Instant started = Instant.now();
        Long runId = jdbc.sql("insert into fuel_import_runs (started_at, status) values (:s, 'RUNNING') returning id")
            .param("s", Timestamp.from(started)).query(Long.class).single();
        try {
            List<MimitCsv.Station> stations = MimitCsv.parseStations(download(props.fuel().stationsUrl()));
            Map<Long, MimitCsv.PetrolPrice> prices = MimitCsv.parsePetrolPrices(download(props.fuel().pricesUrl()));
            if (stations.size() < 1000) {
                throw new IllegalStateException("Anagrafica sospetta: solo " + stations.size() + " impianti");
            }
            int priced = tx.execute(s -> store(stations, prices, started));
            finish(runId, "OK", stations.size(), priced, null);
            log.info("Import carburanti: {} impianti, {} con prezzo benzina", stations.size(), priced);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Import carburanti fallito: {}", e.getMessage());
            finish(runId, "ERROR", null, null, e.getMessage());
        } finally {
            running.set(false);
        }
    }

    private int store(List<MimitCsv.Station> stations, Map<Long, MimitCsv.PetrolPrice> prices, Instant importedAt) {
        Timestamp ts = Timestamp.from(importedAt);
        List<Object[]> rows = new ArrayList<>(stations.size());
        int priced = 0;
        for (MimitCsv.Station s : stations) {
            MimitCsv.PetrolPrice p = prices.get(s.id());
            if (p != null && p.self() != null) {
                priced++;
            }
            rows.add(new Object[] {s.id(), s.name(), s.brand(), s.address(), s.comune(), s.provincia(), s.lat(), s.lon(),
                p == null ? null : p.self(), p == null ? null : p.served(),
                p == null ? null : Timestamp.from(p.updatedAt()), ts});
        }
        String upsert = """
            insert into fuel_stations (id, name, brand, address, comune, provincia, lat, lon,
                                       petrol_self, petrol_served, price_updated_at, imported_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (id) do update set
              name = excluded.name, brand = excluded.brand, address = excluded.address,
              comune = excluded.comune, provincia = excluded.provincia, lat = excluded.lat, lon = excluded.lon,
              petrol_self = excluded.petrol_self, petrol_served = excluded.petrol_served,
              price_updated_at = excluded.price_updated_at, imported_at = excluded.imported_at
            """;
        for (int i = 0; i < rows.size(); i += BATCH) {
            jdbcTemplate.batchUpdate(upsert, rows.subList(i, Math.min(rows.size(), i + BATCH)));
        }
        // impianti non più presenti nell'anagrafica (chiusi)
        jdbcTemplate.update("delete from fuel_stations where imported_at < ?", ts);
        return priced;
    }

    private String download(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(2))
            .header("User-Agent", props.http().userAgent())
            .GET().build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " da " + url);
        }
        return MimitCsv.decode(response.body());
    }

    private void finish(Long runId, String status, Integer stations, Integer prices, String error) {
        jdbc.sql("""
                update fuel_import_runs set finished_at = now(), status = :status, stations = :stations,
                       prices = :prices, error = :error where id = :id
                """)
            .param("status", status).param("stations", stations).param("prices", prices)
            .param("error", error == null ? null : error.substring(0, Math.min(500, error.length())))
            .param("id", runId)
            .update();
    }

    public Optional<ImportRun> lastRun() {
        return jdbc.sql("""
                select started_at, finished_at, status, stations, prices, error
                  from fuel_import_runs order by id desc limit 1
                """)
            .query((rs, i) -> new ImportRun(
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant(),
                rs.getString("status"),
                (Integer) rs.getObject("stations"), (Integer) rs.getObject("prices"), rs.getString("error")))
            .optional();
    }

    public long stationCount() {
        return jdbc.sql("select count(*) from fuel_stations").query(Long.class).single();
    }
}
