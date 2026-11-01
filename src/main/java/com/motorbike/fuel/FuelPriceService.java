package com.motorbike.fuel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.motorbike.geo.LatLon;

/** Prezzi della benzina (self service) vicino a un punto, dai dati MIMIT importati. */
@Service
public class FuelPriceService {

    public record Station(long id, String name, String brand, String address, String comune, String provincia,
                          double lat, double lon, BigDecimal petrolSelf, double distanceKm) {

        public String displayName() {
            if (brand != null && name != null && !name.toLowerCase().contains(brand.toLowerCase())) {
                return brand + " · " + name;
            }
            return name != null ? name : (brand != null ? brand : "Distributore");
        }
    }

    /** Prezzo stimato e da dove viene (vicino al percorso oppure media nazionale). */
    public record PriceEstimate(BigDecimal pricePerLiter, boolean local, int stationsSampled) {
    }

    private static final String DISTANCE = """
        (6371.0088 * 2 * asin(sqrt(
            power(sin(radians(lat - :lat) / 2), 2)
          + cos(radians(:lat)) * cos(radians(lat)) * power(sin(radians(lon - :lon) / 2), 2))))
        """;

    private final JdbcClient jdbc;

    public FuelPriceService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Distributori più economici entro {@code radiusKm}, a parità di prezzo il più vicino. */
    public List<Station> cheapestNear(LatLon p, double radiusKm, int limit) {
        return jdbc.sql("""
                select * from (
                    select id, name, brand, address, comune, provincia, lat, lon, petrol_self,
                    """ + DISTANCE + """
                           as distance_km
                      from fuel_stations
                     where petrol_self is not null
                       and lat between :minLat and :maxLat and lon between :minLon and :maxLon
                ) s
                where distance_km <= :radius
                order by petrol_self, distance_km
                limit :limit
                """)
            .params(box(p, radiusKm))
            .param("radius", radiusKm)
            .param("limit", limit)
            .query((rs, i) -> new Station(rs.getLong("id"), rs.getString("name"), rs.getString("brand"),
                rs.getString("address"), rs.getString("comune"), rs.getString("provincia"),
                rs.getDouble("lat"), rs.getDouble("lon"), rs.getBigDecimal("petrol_self"),
                Math.round(rs.getDouble("distance_km") * 10) / 10.0))
            .list();
    }

    /**
     * Prezzo medio della benzina self vicino ai punti indicati (entro {@code radiusKm});
     * se non ci sono dati locali usa la media nazionale. Vuoto se non è mai stato importato nulla.
     */
    public Optional<PriceEstimate> estimate(List<LatLon> points, double radiusKm) {
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (LatLon p : points) {
            var row = jdbc.sql("""
                    select coalesce(sum(petrol_self), 0) as total, count(*) as n from (
                        select petrol_self,
                        """ + DISTANCE + """
                               as distance_km
                          from fuel_stations
                         where petrol_self is not null
                           and lat between :minLat and :maxLat and lon between :minLon and :maxLon
                    ) s where distance_km <= :radius
                    """)
                .params(box(p, radiusKm))
                .param("radius", radiusKm)
                .query((rs, i) -> new Object[] {rs.getBigDecimal("total"), rs.getInt("n")})
                .single();
            sum = sum.add((BigDecimal) row[0]);
            count += (Integer) row[1];
        }
        if (count > 0) {
            return Optional.of(new PriceEstimate(sum.divide(BigDecimal.valueOf(count), 3, RoundingMode.HALF_UP),
                true, count));
        }
        return nationalAverage().map(avg -> new PriceEstimate(avg, false, 0));
    }

    public Optional<BigDecimal> nationalAverage() {
        return jdbc.sql("select round(avg(petrol_self), 3) from fuel_stations where petrol_self is not null")
            .query(BigDecimal.class)
            .optional();
    }

    private static java.util.Map<String, Object> box(LatLon p, double radiusKm) {
        double dLat = radiusKm / 111.0;
        double dLon = radiusKm / (111.0 * Math.max(0.2, Math.cos(Math.toRadians(p.lat()))));
        return java.util.Map.of("lat", p.lat(), "lon", p.lon(),
            "minLat", p.lat() - dLat, "maxLat", p.lat() + dLat,
            "minLon", p.lon() - dLon, "maxLon", p.lon() + dLon);
    }
}
