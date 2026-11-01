package com.motorbike.trip;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.motorbike.fuel.FuelPriceService;
import com.motorbike.geo.RouteResult;
import com.motorbike.trip.TripEnums.RouteStatus;
import com.motorbike.trip.TripEnums.TripRole;

/** Modelli di sola lettura per le pagine dei road trip. */
public final class TripViews {

    private TripViews() {
    }

    public record TripSummary(long id, String title, LocalDate startDate, String ownerUsername, TripRole myRole,
                              long stages, double totalKm) {
    }

    public record MemberView(long userId, String username, String shownName, Long photoId, TripRole role) {
    }

    public record StopView(String name, String typeLabel, String icon, double lat, double lon, String notes) {
    }

    /** Stima dei costi carburante di una tappa per la moto di chi guarda. */
    public record FuelEstimate(double liters, BigDecimal cost, BigDecimal pricePerLiter, boolean localPrice,
                               Integer rangeKm, int refuelsNeeded) {
    }

    public record StageView(long id, int position, String title, LocalDate date,
                            String startName, double startLat, double startLon,
                            String endName, double endLat, double endLon,
                            boolean avoidMotorways, boolean avoidTolls, RouteStatus routeStatus,
                            Double km, Integer durationSeconds, String duration, Boolean hasMotorway, Boolean hasToll,
                            String shape,
                            List<RouteResult.Maneuver> directions, String notes, String highlights,
                            String accommodationType, String accommodationName, String accommodationAddress,
                            BigDecimal accommodationPrice, String accommodationUrl,
                            List<StopView> stops, FuelEstimate fuel) {

        public boolean hasAccommodation() {
            return accommodationName != null || accommodationType != null;
        }
    }

    public record Totals(double km, String duration, double liters, BigDecimal fuelCost,
                         BigDecimal accommodationCost, BigDecimal total) {
    }

    /** Parametri della moto usati per i calcoli (dal profilo, o valori predefiniti). */
    public record BikeParams(double kmPerLiter, boolean defaultConsumption, Double tankLiters) {
    }

    public record TripView(long id, String title, String description, LocalDate startDate, TripRole myRole,
                           boolean canEdit, boolean isOwner, List<MemberView> members, List<StageView> stages,
                           Totals totals, BikeParams bike) {
    }

    public record StageDetail(TripView trip, StageView stage, List<FuelPriceService.Station> cheapestNearStart,
                              Long prevStageId, Long nextStageId) {
    }

    /** "2 h 35 min" */
    static String formatDuration(Integer seconds) {
        if (seconds == null) {
            return null;
        }
        long minutes = Math.round(seconds / 60.0);
        long h = minutes / 60;
        long m = minutes % 60;
        return h == 0 ? m + " min" : (m == 0 ? h + " h" : h + " h " + m + " min");
    }
}
