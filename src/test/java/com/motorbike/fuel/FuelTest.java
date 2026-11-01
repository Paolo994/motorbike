package com.motorbike.fuel;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.motorbike.IntegrationTest;
import com.motorbike.geo.LatLon;

class FuelTest extends IntegrationTest {

    @Autowired FuelPriceService prices;
    @Autowired JdbcTemplate jdbc;

    @Test
    void parsesMimitStationsWithRealWorldQuirks() {
        String csv = """
            Estrazione del 2026-10-30
            idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine
            59183|ENIMOOV S.P.A.|Agip Eni|Stradale|19829 AGRIGENTO|SS.189 KM. 64+649  |AGRIGENTO|AG|37.333935|13.595533
            63381|ENIMOOV S.P.A.|Agip Eni|Stradale|19834\tMONTALLEGRO|SS.115 KM.158+302  S.N.  |AGRIGENTO|AG|37.38895|13.351206
            70000|Rossi|Pompe Bianche|Stradale|Distributore | con barra|Via Roma 1|FIRENZE|FI|43.77|11.25
            1|X|Y|Stradale|Zero|Via|NESSUNO|XX|0|0
            riga rotta
            """;
        List<MimitCsv.Station> stations = MimitCsv.parseStations(csv);
        assertThat(stations).extracting(MimitCsv.Station::id).containsExactly(59183L, 63381L, 70000L);
        assertThat(stations.get(1).name()).isEqualTo("19834 MONTALLEGRO");       // TAB normalizzato
        assertThat(stations.get(1).address()).isEqualTo("SS.115 KM.158+302 S.N.");
        // separatore dentro un campo: coordinate, comune e provincia restano corretti
        assertThat(stations.get(2).comune()).isEqualTo("FIRENZE");
        assertThat(stations.get(2).provincia()).isEqualTo("FI");
        assertThat(stations.get(2).lat()).isEqualTo(43.77);
    }

    @Test
    void parsesOnlyPetrolPricesSelfAndServed() {
        String csv = """
            Estrazione del 2026-10-30
            idImpianto|descCarburante|prezzo|isSelf|dtComu
            3464|Benzina|2.349|0|23/10/2026 20:00:07
            3464|Benzina|1.999|1|23/10/2026 20:00:06
            3464|Gasolio|1.899|1|23/10/2026 20:00:06
            3464|Benzina Plus|2.100|1|23/10/2026 20:00:06
            3464|Benzina|1.959|1|29/10/2026 08:00:00
            9999|Benzina|0.010|1|29/10/2026 08:00:00
            """;
        Map<Long, MimitCsv.PetrolPrice> p = MimitCsv.parsePetrolPrices(csv);
        assertThat(p).containsOnlyKeys(3464L);                                    // prezzo assurdo scartato
        assertThat(p.get(3464L).self()).isEqualByComparingTo("1.959");            // comunicazione più recente
        assertThat(p.get(3464L).served()).isEqualByComparingTo("2.349");
    }

    @Test
    void decodesLatin1WhenNotUtf8() {
        byte[] latin1 = "FORLÌ".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(MimitCsv.decode(latin1)).isEqualTo("FORLÌ");
        assertThat(MimitCsv.decode("FORLÌ".getBytes(StandardCharsets.UTF_8))).isEqualTo("FORLÌ");
    }

    @Test
    void findsCheapestAndAveragePricesNearby() {
        // tre distributori vicino a Siena e uno lontano (coordinate usate solo da questo test)
        insert(910001, "Caro", 43.3200, 11.3300, "1.950");
        insert(910002, "Economico", 43.3210, 11.3310, "1.750");
        insert(910003, "Medio", 43.3500, 11.3000, "1.850");
        insert(910004, "Lontano", 43.9000, 12.5000, "1.500");

        LatLon siena = new LatLon(43.3188, 11.3308);
        List<FuelPriceService.Station> cheapest = prices.cheapestNear(siena, 10, 5);
        assertThat(cheapest).extracting(FuelPriceService.Station::name)
            .containsSequence("Economico", "Medio", "Caro")
            .doesNotContain("Lontano");
        assertThat(cheapest.getFirst().distanceKm()).isLessThan(1);

        FuelPriceService.PriceEstimate local = prices.estimate(List.of(siena), 10).orElseThrow();
        assertThat(local.local()).isTrue();
        assertThat(local.stationsSampled()).isEqualTo(3);
        assertThat(local.pricePerLiter()).isEqualByComparingTo("1.850");          // (1.95 + 1.75 + 1.85) / 3

        // in mezzo al mare non ci sono distributori: media nazionale
        FuelPriceService.PriceEstimate sea = prices.estimate(List.of(new LatLon(40.0, 11.0)), 10).orElseThrow();
        assertThat(sea.local()).isFalse();
    }

    private void insert(long id, String name, double lat, double lon, String price) {
        jdbc.update("""
                insert into fuel_stations (id, name, brand, address, comune, provincia, lat, lon, petrol_self, imported_at)
                values (?, ?, 'Test', 'Via Test', 'SIENA', 'SI', ?, ?, ?, ?)
                on conflict (id) do nothing
                """, id, name, lat, lon, new BigDecimal(price), Timestamp.from(Instant.now()));
    }
}
