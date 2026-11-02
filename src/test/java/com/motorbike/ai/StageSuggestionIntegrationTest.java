package com.motorbike.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import com.motorbike.IntegrationTest;
import com.motorbike.ai.TravelCurator.Curation;
import com.motorbike.ai.TravelCurator.CurationException;
import com.motorbike.ai.TravelCurator.Pick;
import com.motorbike.geo.LatLon;
import com.motorbike.geo.Polyline;
import com.motorbike.geo.RouteResult;
import com.motorbike.trip.TripEnums.AccommodationType;
import com.motorbike.trip.TripEnums.StopType;
import com.motorbike.trip.TripEnums.TripRole;
import com.motorbike.trip.TripService;
import com.motorbike.user.User;

class StageSuggestionIntegrationTest extends IntegrationTest {

    private static final LatLon START = new LatLon(41.10, 16.80);
    private static final LatLon END = new LatLon(41.30, 16.90);

    @Autowired TripService trips;

    private final Place hostel = place("h1", "Ostello del Borgo", "Ostello", 41.301, 16.901, 4.6, 812);
    private final Place castle = place("a1", "Castello Svevo", "Attrazione turistica", 41.2, 16.85, 4.7, 5300);
    private final Place trattoria = place("r1", "Trattoria da Nino", "Ristorante", 41.21, 16.86, 4.5, 640);
    private final Place pizzeria = place("r2", "Pizzeria Sole", "Ristorante", 41.22, 16.87, 4.1, 90);

    @BeforeEach
    void stubs() {
        when(routingService.route(anyList(), anyBoolean(), anyBoolean())).thenAnswer(inv -> {
            List<LatLon> points = inv.getArgument(0);
            return new RouteResult(40_000, 2400, false, false, Polyline.encode(points), List.of(), false);
        });
        when(placesClient.isConfigured()).thenReturn(true);
        when(placesClient.searchNearby(eq(PlaceCategory.LODGING), any(), anyInt(), anyInt())).thenReturn(List.of(hostel));
        when(placesClient.searchNearby(eq(PlaceCategory.ATTRACTION), any(), anyInt(), anyInt())).thenReturn(List.of(castle));
        when(placesClient.searchNearby(eq(PlaceCategory.RESTAURANT), any(), anyInt(), anyInt()))
            .thenReturn(List.of(trattoria, pizzeria));
    }

    @Test
    void generatesCuratedSuggestionsWithRealRatingsAndReviews() throws Exception {
        User owner = createUser("aiOwn");
        long[] ids = tripWithStage(owner);
        when(travelCurator.curate(any(), any())).thenReturn(new Curation("Tappa breve, sosta pranzo a Castel del Monte.",
            Map.of(PlaceCategory.LODGING, List.of(new Pick("h1", "A 150 m dall'arrivo, recensioni citano il parcheggio moto.")),
                PlaceCategory.ATTRACTION, List.of(new Pick("a1", "Deviazione breve, molto apprezzato.")),
                PlaceCategory.RESTAURANT, List.of(new Pick("r1", "A metà percorso, ottimo rapporto qualità-prezzo."),
                    new Pick("ID-INVENTATO", "non esiste")))));

        mvc.perform(post(suggestionsUrl(ids)).param("preferences", "pranzo tipico").with(csrf()).with(login(owner)))
            .andExpect(redirectedUrl(stageUrl(ids) + "#consigli"));

        // la preferenza del gruppo arriva al modello
        verify(travelCurator).curate(argThat(ctx -> "pranzo tipico".equals(ctx.preferences())), any());

        mvc.perform(get(stageUrl(ids)).with(login(owner)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Tappa breve, sosta pranzo a Castel del Monte.")))
            .andExpect(content().string(containsString("Ostello del Borgo")))
            .andExpect(content().string(containsString("4,6")))                       // valutazione da Google
            .andExpect(content().string(containsString("Ottimo per chi arriva in moto")))  // recensione da Google
            .andExpect(content().string(containsString("Trattoria da Nino")))
            .andExpect(content().string(not(containsString("Pizzeria Sole"))))       // non scelta dal modello
            .andExpect(content().string(containsString("Google Maps</strong>")))     // attribuzione
            .andExpect(content().string(containsString("https://www.google.com/maps/dir/?api=1")));
    }

    @Test
    void fallsBackToRatingOrderWhenClaudeIsUnavailable() throws Exception {
        User owner = createUser("aiFall");
        long[] ids = tripWithStage(owner);
        when(travelCurator.curate(any(), any())).thenThrow(new CurationException("Chiave Anthropic non configurata"));

        mvc.perform(post(suggestionsUrl(ids)).with(csrf()).with(login(owner)))
            .andExpect(flash().attribute("info", containsString("non è disponibile")));
        mvc.perform(get(stageUrl(ids)).with(login(owner)))
            .andExpect(content().string(containsString("ordinati per valutazione")))
            .andExpect(content().string(containsString("Trattoria da Nino")))
            .andExpect(content().string(containsString("Pizzeria Sole")));
    }

    @Test
    void suggestionsBecomeStopsOrAccommodation() throws Exception {
        User owner = createUser("aiAct");
        long[] ids = tripWithStage(owner);
        when(travelCurator.curate(any(), any())).thenReturn(new Curation(null, Map.of(
            PlaceCategory.LODGING, List.of(new Pick("h1", "vicino")),
            PlaceCategory.RESTAURANT, List.of(new Pick("r1", "a metà strada")))));
        mvc.perform(post(suggestionsUrl(ids)).with(csrf()).with(login(owner)));

        mvc.perform(post(suggestionsUrl(ids) + "/r1/stop").with(csrf()).with(login(owner)))
            .andExpect(flash().attribute("success", containsString("Trattoria da Nino")));
        // il percorso viene ricalcolato passando dal ristorante
        verify(routingService).route(argThat(points -> points.size() == 3
            && points.get(1).equals(new LatLon(trattoria.lat(), trattoria.lon()))), anyBoolean(), anyBoolean());
        var stage = trips.stageDetail(owner.getId(), ids[0], ids[1]).stage();
        assertThat(stage.stops()).singleElement().satisfies(s -> {
            assertThat(s.name()).isEqualTo("Trattoria da Nino");
            assertThat(s.typeLabel()).isEqualTo(StopType.FOOD.getLabel());
        });

        // la tappa è cambiata: i consigli vanno rigenerati prima di usarli di nuovo
        mvc.perform(post(suggestionsUrl(ids) + "/h1/accommodation").with(csrf()).with(login(owner)))
            .andExpect(flash().attribute("error", containsString("scaduti")));
        mvc.perform(post(suggestionsUrl(ids)).with(csrf()).with(login(owner)));
        mvc.perform(post(suggestionsUrl(ids) + "/h1/accommodation").with(csrf()).with(login(owner)))
            .andExpect(flash().attribute("success", containsString("Ostello del Borgo")));
        stage = trips.stageDetail(owner.getId(), ids[0], ids[1]).stage();
        assertThat(stage.accommodationName()).isEqualTo("Ostello del Borgo");
        assertThat(stage.accommodationType()).isEqualTo(AccommodationType.HOSTEL.getLabel());
    }

    @Test
    void permissionsAndRateLimit() throws Exception {
        User owner = createUser("aiBoss");
        User viewer = createUser("aiView");
        User stranger = createUser("aiNone");
        makeFriends(owner, viewer);
        long[] ids = tripWithStage(owner);
        trips.invite(owner.getId(), ids[0], viewer.getUsername(), TripRole.VIEWER);
        when(travelCurator.curate(any(), any())).thenReturn(new Curation(null, Map.of()));

        // generare costa: solo chi può modificare il viaggio
        mvc.perform(post(suggestionsUrl(ids)).with(csrf()).with(login(viewer))).andExpect(status().isForbidden());
        mvc.perform(post(suggestionsUrl(ids)).with(csrf()).with(login(stranger))).andExpect(status().isNotFound());
        mvc.perform(get(stageUrl(ids)).with(login(viewer)))
            .andExpect(content().string(containsString("chi può modificare il viaggio può generarli")));

        // limite orario (3 nei test)
        for (int i = 0; i < 3; i++) {
            mvc.perform(post(suggestionsUrl(ids)).with(csrf()).with(login(owner)))
                .andExpect(flash().attributeCount(0));
        }
        mvc.perform(post(suggestionsUrl(ids)).with(csrf()).with(login(owner)))
            .andExpect(flash().attribute("error", containsString("limite")));
    }

    /** Ricerca specifica: si cerca e si mostra solo la categoria scelta. */
    @Test
    void specificCategorySearchesAndShowsOnlyThatCategory() throws Exception {
        User owner = createUser("aiOnly");
        long[] ids = tripWithStage(owner);
        when(travelCurator.curate(any(), any())).thenReturn(new Curation(null,
            Map.of(PlaceCategory.LODGING, List.of(new Pick("h1", "Vicino all'arrivo")))));

        mvc.perform(post(suggestionsUrl(ids)).param("category", "LODGING").param("preferences", "ostello")
            .with(csrf()).with(login(owner)));

        verify(placesClient).searchNearby(eq(PlaceCategory.LODGING), any(), anyInt(), anyInt());
        verify(placesClient, never()).searchNearby(eq(PlaceCategory.ATTRACTION), any(), anyInt(), anyInt());
        verify(placesClient, never()).searchNearby(eq(PlaceCategory.RESTAURANT), any(), anyInt(), anyInt());
        // al modello arrivano solo gli alloggi
        verify(travelCurator).curate(any(), argThat(c -> c.keySet().equals(java.util.Set.of(PlaceCategory.LODGING))));

        mvc.perform(get(stageUrl(ids)).with(login(owner)))
            .andExpect(content().string(containsString("Ostello del Borgo")))
            .andExpect(content().string(not(containsString("Dove mangiare</span>"))))
            .andExpect(content().string(not(containsString("Trattoria da Nino"))));
    }

    /** Con "Tutto", se l'AI lascia vuote le altre categorie (richiesta su un solo tipo) non vengono mostrate. */
    @Test
    void emptyCategoriesAreHiddenWhenAiFocusesOnOne() throws Exception {
        User owner = createUser("aiFocus");
        long[] ids = tripWithStage(owner);
        when(travelCurator.curate(any(), any())).thenReturn(new Curation(null, Map.of(
            PlaceCategory.LODGING, List.of(new Pick("h1", "Ostello economico")),
            PlaceCategory.ATTRACTION, List.of(), PlaceCategory.RESTAURANT, List.of())));

        mvc.perform(post(suggestionsUrl(ids)).param("preferences", "solo un ostello economico")
            .with(csrf()).with(login(owner)));
        mvc.perform(get(stageUrl(ids)).with(login(owner)))
            .andExpect(content().string(containsString("Ostello del Borgo")))
            .andExpect(content().string(not(containsString("Cosa vedere</span>"))))
            .andExpect(content().string(not(containsString("Dove mangiare</span>"))));
    }

    /** "Cerca suggerimenti" nel form: funziona con le coordinate del form, anche senza tappa salvata. */
    @Test
    void formPreviewReturnsOnlyTheRequestedCategoryAsJson() throws Exception {
        User owner = createUser("aiForm");
        User viewer = createUser("aiFormView");
        makeFriends(owner, viewer);
        long tripId = tripWithStage(owner)[0];
        trips.invite(owner.getId(), tripId, viewer.getUsername(), TripRole.VIEWER);
        when(travelCurator.curate(any(), any())).thenReturn(new Curation(null,
            Map.of(PlaceCategory.ATTRACTION, List.of(new Pick("a1", "Deviazione breve")))));

        mvc.perform(preview(tripId, "ATTRACTION").with(csrf()).with(login(owner)))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.aiGenerated").value(true))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.places.length()").value(1))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.places[0].name").value("Castello Svevo"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.places[0].rating").value(4.7))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.places[0].reason").value("Deviazione breve"));
        verify(placesClient, never()).searchNearby(eq(PlaceCategory.LODGING), any(), anyInt(), anyInt());

        mvc.perform(preview(tripId, "ATTRACTION").with(csrf()).with(login(viewer))).andExpect(status().isForbidden());
        mvc.perform(preview(tripId, "RESTAURANT").with(csrf()).with(login(owner)))
            .andExpect(status().isUnprocessableEntity());
        mvc.perform(preview(tripId, "LODGING").with(login(owner))).andExpect(status().isForbidden());   // senza CSRF
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder preview(long tripId, String category) {
        return post("/trips/{id}/suggestions/preview", tripId).param("category", category)
            .param("startName", "Bari").param("startLat", String.valueOf(START.lat())).param("startLon", String.valueOf(START.lon()))
            .param("endName", "Andria").param("endLat", String.valueOf(END.lat())).param("endLon", String.valueOf(END.lon()))
            .param("stopLat", "41.2").param("stopLon", "16.85")
            .accept(org.springframework.http.MediaType.APPLICATION_JSON);
    }

    @Test
    void missingPlacesKeyShowsAMessageInsteadOfTheForm() throws Exception {
        User owner = createUser("aiNoKey");
        long[] ids = tripWithStage(owner);
        when(placesClient.isConfigured()).thenReturn(false);
        mvc.perform(get(stageUrl(ids)).with(login(owner)))
            .andExpect(content().string(containsString("manca la configurazione di Google Places")))
            .andExpect(content().string(not(containsString("Genera consigli"))));
    }

    // ---------------------------------------------------------------- helper

    /** @return {tripId, stageId} */
    private long[] tripWithStage(User owner) throws Exception {
        MvcResult r = mvc.perform(post("/trips").param("title", "Puglia").with(csrf()).with(login(owner))).andReturn();
        long tripId = Long.parseLong(r.getResponse().getRedirectedUrl().substring("/trips/".length()));
        r = mvc.perform(post("/trips/{id}/stages", tripId).param("title", "Andria")
                .param("startName", "Bari").param("startLat", String.valueOf(START.lat())).param("startLon", String.valueOf(START.lon()))
                .param("endName", "Andria").param("endLat", String.valueOf(END.lat())).param("endLon", String.valueOf(END.lon()))
                .with(csrf()).with(login(owner)))
            .andReturn();
        String url = r.getResponse().getRedirectedUrl();
        return new long[] {tripId, Long.parseLong(url.substring(url.lastIndexOf('/') + 1))};
    }

    private static String stageUrl(long[] ids) {
        return "/trips/" + ids[0] + "/stages/" + ids[1];
    }

    private static String suggestionsUrl(long[] ids) {
        return stageUrl(ids) + "/suggestions";
    }

    private static Place place(String id, String name, String type, double lat, double lon, double rating, int count) {
        return new Place(id, name, type, "Via Test, Andria", lat, lon, rating, count, 1, null,
            "https://maps.google.com/?cid=" + id, null,
            List.of(new Place.Review("Anna B.", null, 5, "2 settimane fa", "Ottimo per chi arriva in moto, consigliato.")));
    }
}
