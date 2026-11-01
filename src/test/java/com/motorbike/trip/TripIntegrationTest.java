package com.motorbike.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.motorbike.IntegrationTest;
import com.motorbike.geo.LatLon;
import com.motorbike.geo.Polyline;
import com.motorbike.geo.RouteResult;
import com.motorbike.notification.NotificationService;
import com.motorbike.notification.NotificationType;
import com.motorbike.trip.TripEnums.TripRole;
import com.motorbike.user.User;

class TripIntegrationTest extends IntegrationTest {

    // zona usata solo da questo test (Sardegna), per non mescolare i prezzi con altri test
    private static final LatLon START = new LatLon(40.1200, 9.0100);
    private static final LatLon STOP = new LatLon(40.2000, 9.1000);
    private static final LatLon END = new LatLon(40.3000, 9.2000);

    @Autowired TripService trips;
    @Autowired NotificationService notifications;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void routing() {
        // 120 km, 2 ore, nessuna autostrada
        when(routingService.route(anyList(), anyBoolean(), anyBoolean())).thenAnswer(inv -> {
            List<LatLon> points = inv.getArgument(0);
            return new RouteResult(120_000, 7200, false, false, Polyline.encode(points),
                List.of(new RouteResult.Maneuver("Guida verso nord su SS131.", 12.5),
                    new RouteResult.Maneuver("Sei arrivato a destinazione.", 0)), false);
        });
        for (long id : new long[] {920001, 920002}) {
            jdbc.update("""
                insert into fuel_stations (id, name, brand, address, comune, provincia, lat, lon, petrol_self, imported_at)
                values (?, 'Stazione test', 'Test', 'Via Test', 'ORISTANO', 'OR', ?, ?, 1.800, ?) on conflict (id) do nothing
                """, id, id == 920001 ? START.lat() + 0.01 : END.lat() + 0.01, id == 920001 ? START.lon() : END.lon(),
                Timestamp.from(Instant.now()));
        }
    }

    @Test
    void ownerPlansATripWithStagesCostsAndDirections() throws Exception {
        User owner = createUser("tripOwn");
        owner.setTankCapacityL(new BigDecimal("5"));                    // autonomia 5 × 20 × 0,85 = 85 km
        users.save(owner);

        long tripId = createTrip(owner, "Giro della Sardegna");
        MvcResult res = mvc.perform(stageRequest(tripId, "Cagliari → Oristano")
                .param("stops[0].name", "Belvedere").param("stops[0].type", "VIEWPOINT")
                .param("stops[0].lat", String.valueOf(STOP.lat())).param("stops[0].lon", String.valueOf(STOP.lon()))
                .param("avoidMotorways", "true")
                .param("highlights", "Nuraghe Losa")
                .param("accommodationType", "HOSTEL").param("accommodationName", "Ostello del Golfo")
                .param("accommodationPrice", "35")
                .with(csrf()).with(login(owner)))
            .andExpect(redirectedUrlPattern("/trips/" + tripId + "/stages/*"))
            .andReturn();
        String stageUrl = res.getResponse().getRedirectedUrl();

        // il percorso passa dalla fermata ed evita le autostrade
        verify(routingService).route(argThat(points -> points.size() == 3 && points.get(1).equals(STOP)), eq(true), eq(false));

        // 120 km a 20 km/l (consumo predefinito) = 6 l × 1,800 €/l = 10,80 €
        mvc.perform(get(stageUrl).with(login(owner)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("10,80 €")))
            .andExpect(content().string(containsString("1,800 €/l")))
            .andExpect(content().string(containsString("1 rifornimento")))   // 120 km > 85 km di autonomia
            .andExpect(content().string(containsString("Guida verso nord su SS131.")))
            .andExpect(content().string(containsString("Nuraghe Losa")))
            .andExpect(content().string(containsString("Ostello del Golfo")))
            .andExpect(content().string(containsString("Senza autostrade")))
            .andExpect(content().string(containsString("data-map=")));

        // pagina del viaggio: totale = 10,80 carburante + 35 ostello
        mvc.perform(get("/trips/{id}", tripId).with(login(owner)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("45,80")))
            .andExpect(content().string(containsString("Cagliari → Oristano")));

        // la tappa successiva parte da dove finisce la precedente
        mvc.perform(get("/trips/{id}/stages/new", tripId).with(login(owner)))
            .andExpect(status().isOk())
            .andExpect(model().attribute("form", org.hamcrest.Matchers.hasProperty("startName", org.hamcrest.Matchers.equalTo("Arrivo"))));
    }

    @Test
    void stagesCanBeReorderedAndDeleted() throws Exception {
        User owner = createUser("tripOrd");
        long tripId = createTrip(owner, "Ordine");
        long a = stageId(mvc.perform(stageRequest(tripId, "A").with(csrf()).with(login(owner))).andReturn());
        long b = stageId(mvc.perform(stageRequest(tripId, "B").with(csrf()).with(login(owner))).andReturn());
        long c = stageId(mvc.perform(stageRequest(tripId, "C").with(csrf()).with(login(owner))).andReturn());

        mvc.perform(post("/trips/{id}/stages/{s}/move", tripId, c).param("dir", "up").with(csrf()).with(login(owner)));
        assertThat(order(owner, tripId)).containsExactly("A", "C", "B");

        mvc.perform(post("/trips/{id}/stages/{s}/delete", tripId, a).with(csrf()).with(login(owner)));
        var stages = trips.view(owner.getId(), tripId).stages();
        assertThat(stages).extracting(TripViews.StageView::title).containsExactly("C", "B");
        assertThat(stages).extracting(TripViews.StageView::position).containsExactly(1, 2);   // rinumerate
        assertThat(b).isNotEqualTo(c);
    }

    @Test
    void membersAndPermissions() throws Exception {
        User owner = createUser("tripBoss");
        User friend = createUser("tripFriend");
        User stranger = createUser("tripStranger");
        makeFriends(owner, friend);
        long tripId = createTrip(owner, "Con gli amici");
        long stageId = stageId(mvc.perform(stageRequest(tripId, "Tappa 1").with(csrf()).with(login(owner))).andReturn());

        // chi non partecipa non vede nemmeno che il viaggio esiste
        mvc.perform(get("/trips/{id}", tripId).with(login(stranger))).andExpect(status().isNotFound());
        mvc.perform(get("/trips/{id}/stages/{s}", tripId, stageId).with(login(stranger))).andExpect(status().isNotFound());

        // si possono invitare solo gli amici
        mvc.perform(post("/trips/{id}/members", tripId).param("username", stranger.getUsername()).param("role", "EDITOR")
                .with(csrf()).with(login(owner)))
            .andExpect(flash().attribute("error", "Puoi invitare solo i tuoi amici."));

        mvc.perform(post("/trips/{id}/members", tripId).param("username", friend.getUsername()).param("role", "VIEWER")
                .with(csrf()).with(login(owner)))
            .andExpect(flash().attributeExists("success"));
        assertThat(notifications.latest(friend.getId(), 5))
            .anyMatch(n -> n.getType() == NotificationType.TRIP_INVITE && n.getLink().equals("/trips/" + tripId));

        // VIEWER: legge ma non modifica
        mvc.perform(get("/trips/{id}", tripId).with(login(friend))).andExpect(status().isOk());
        mvc.perform(get("/trips/{id}/stages/{s}", tripId, stageId).with(login(friend))).andExpect(status().isOk());
        mvc.perform(get("/trips/{id}/stages/new", tripId).with(login(friend))).andExpect(status().isForbidden());
        mvc.perform(stageRequest(tripId, "Abusiva").with(csrf()).with(login(friend))).andExpect(status().isForbidden());
        mvc.perform(post("/trips/{id}/delete", tripId).with(csrf()).with(login(friend))).andExpect(status().isForbidden());

        // promosso a EDITOR: può aggiungere tappe ma non gestire i partecipanti
        trips.changeRole(owner.getId(), tripId, friend.getId(), TripRole.EDITOR);
        mvc.perform(stageRequest(tripId, "Tappa dell'amico").with(csrf()).with(login(friend)))
            .andExpect(redirectedUrlPattern("/trips/" + tripId + "/stages/*"));
        mvc.perform(post("/trips/{id}/members/{u}/remove", tripId, owner.getId()).with(csrf()).with(login(friend)))
            .andExpect(status().isForbidden());

        // l'amico lascia il viaggio
        mvc.perform(post("/trips/{id}/leave", tripId).with(csrf()).with(login(friend)));
        mvc.perform(get("/trips/{id}", tripId).with(login(friend))).andExpect(status().isNotFound());
        assertThat(trips.myTrips(owner.getId())).extracting(TripViews.TripSummary::stages).containsExactly(2L);
    }

    @Test
    void stageValidation() throws Exception {
        User owner = createUser("tripVal");
        long tripId = createTrip(owner, "Validazione");
        mvc.perform(post("/trips/{id}/stages", tripId).param("title", "Senza coordinate")
                .param("startName", "Da qualche parte").param("endName", "Altrove")
                .param("accommodationUrl", "javascript:alert(1)")
                .with(csrf()).with(login(owner)))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "startLat", "endLat", "accommodationUrl"));
    }

    // ---------------------------------------------------------------- helper

    private long createTrip(User owner, String title) throws Exception {
        MvcResult r = mvc.perform(post("/trips").param("title", title).param("startDate", "2027-06-01")
                .with(csrf()).with(login(owner)))
            .andExpect(redirectedUrlPattern("/trips/*"))
            .andReturn();
        return Long.parseLong(r.getResponse().getRedirectedUrl().substring("/trips/".length()));
    }

    private static MockHttpServletRequestBuilder stageRequest(long tripId, String title) {
        return post("/trips/{id}/stages", tripId)
            .param("title", title)
            .param("startName", "Partenza").param("startLat", String.valueOf(START.lat())).param("startLon", String.valueOf(START.lon()))
            .param("endName", "Arrivo").param("endLat", String.valueOf(END.lat())).param("endLon", String.valueOf(END.lon()));
    }

    private static long stageId(MvcResult r) {
        String url = r.getResponse().getRedirectedUrl();
        return Long.parseLong(url.substring(url.lastIndexOf('/') + 1));
    }

    private List<String> order(User owner, long tripId) {
        return trips.view(owner.getId(), tripId).stages().stream().map(TripViews.StageView::title).toList();
    }
}
