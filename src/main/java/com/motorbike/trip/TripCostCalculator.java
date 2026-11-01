package com.motorbike.trip;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.motorbike.fuel.FuelPriceService;
import com.motorbike.geo.LatLon;
import com.motorbike.trip.TripViews.BikeParams;
import com.motorbike.trip.TripViews.FuelEstimate;
import com.motorbike.user.User;

/**
 * Calcolo deterministico dei costi carburante: niente stime "a sentimento", solo km del percorso,
 * consumo della moto e prezzi reali MIMIT vicino a partenza e arrivo della tappa.
 */
@Component
public class TripCostCalculator {

    /** Consumo usato se l'utente non l'ha indicato nel profilo. */
    static final double DEFAULT_KM_PER_LITER = 20.0;
    /** Quota di serbatoio considerata utilizzabile (il resto è riserva). */
    static final double USABLE_TANK = 0.85;
    static final double PRICE_RADIUS_KM = 15;

    private final FuelPriceService prices;

    public TripCostCalculator(FuelPriceService prices) {
        this.prices = prices;
    }

    public BikeParams bikeOf(User viewer) {
        BigDecimal consumption = viewer.getFuelConsumptionKmPerL();
        BigDecimal tank = viewer.getTankCapacityL();
        return new BikeParams(
            consumption != null ? consumption.doubleValue() : DEFAULT_KM_PER_LITER,
            consumption == null,
            tank != null ? tank.doubleValue() : null);
    }

    /** null se la tappa non ha ancora una distanza. */
    public FuelEstimate estimate(TripStage stage, BikeParams bike) {
        if (stage.getDistanceM() == null) {
            return null;
        }
        double km = stage.getDistanceM() / 1000.0;
        double liters = km / bike.kmPerLiter();
        Optional<FuelPriceService.PriceEstimate> price = prices.estimate(List.of(
            new LatLon(stage.getStartLat(), stage.getStartLon()),
            new LatLon(stage.getEndLat(), stage.getEndLon())), PRICE_RADIUS_KM);

        Integer range = null;
        int refuels = 0;
        if (bike.tankLiters() != null) {
            range = (int) Math.floor(bike.tankLiters() * bike.kmPerLiter() * USABLE_TANK);
            // si parte col pieno: un rifornimento ogni "range" km oltre il primo pieno
            refuels = range > 0 ? (int) Math.max(0, Math.ceil(km / range) - 1) : 0;
        }
        return new FuelEstimate(
            Math.round(liters * 10) / 10.0,
            price.map(p -> p.pricePerLiter().multiply(BigDecimal.valueOf(liters)).setScale(2, RoundingMode.HALF_UP))
                .orElse(null),
            price.map(FuelPriceService.PriceEstimate::pricePerLiter).orElse(null),
            price.map(FuelPriceService.PriceEstimate::local).orElse(false),
            range, refuels);
    }
}
