package com.motorbike.trip;

import java.util.List;

import org.springframework.stereotype.Component;

import com.motorbike.fuel.FuelPriceService;
import com.motorbike.trip.TripViews.StageView;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Dati per trip-map.js, serializzati in JSON e passati alla pagina in un attributo data-map. */
@Component
public class TripMapJson {

    private final JsonMapper json;

    public TripMapJson(JsonMapper json) {
        this.json = json;
    }

    public String of(long tripId, List<StageView> stages, Long focusStageId, List<FuelPriceService.Station> stations) {
        ObjectNode root = json.createObjectNode();
        if (focusStageId != null) {
            root.put("focus", focusStageId);
        }
        ArrayNode arr = root.putArray("stages");
        for (StageView s : stages) {
            ObjectNode n = arr.addObject();
            n.put("id", s.id());
            n.put("position", s.position());
            n.put("title", s.title());
            n.put("url", "/trips/" + tripId + "/stages/" + s.id());
            n.put("shape", s.shape());
            n.put("estimated", s.routeStatus() == TripEnums.RouteStatus.ESTIMATED);
            point(n.putObject("start"), s.startName(), s.startLat(), s.startLon());
            point(n.putObject("end"), s.endName(), s.endLat(), s.endLon());
            ArrayNode stops = n.putArray("stops");
            s.stops().forEach(st -> point(stops.addObject(), st.name(), st.lat(), st.lon())
                .put("label", st.typeLabel()));
        }
        ArrayNode st = root.putArray("stations");
        for (FuelPriceService.Station station : stations) {
            point(st.addObject(), station.displayName(), station.lat(), station.lon())
                .put("price", station.petrolSelf().toPlainString())
                .put("km", station.distanceKm());
        }
        return json.writeValueAsString(root);
    }

    private static ObjectNode point(ObjectNode n, String name, double lat, double lon) {
        return n.put("name", name).put("lat", lat).put("lon", lon);
    }
}
