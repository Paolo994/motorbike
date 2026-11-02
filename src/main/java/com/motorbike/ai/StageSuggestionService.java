package com.motorbike.ai;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.motorbike.ai.TravelCurator.Candidate;
import com.motorbike.ai.TravelCurator.Curation;
import com.motorbike.ai.TravelCurator.CurationException;
import com.motorbike.ai.TravelCurator.Pick;
import com.motorbike.config.AppProperties;
import com.motorbike.geo.LatLon;
import com.motorbike.trip.StageAiContext;
import com.motorbike.trip.TripEnums.AccommodationType;
import com.motorbike.trip.TripEnums.StopType;
import com.motorbike.trip.TripService;

/**
 * Consigli per una tappa: luoghi reali da Google Places, scelti e motivati da Claude.
 * Se Claude non è disponibile i luoghi vengono comunque mostrati, ordinati per valutazione.
 * I risultati restano solo in memoria (le condizioni di Google Places non consentono di archiviarli).
 */
@Service
public class StageSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(StageSuggestionService.class);

    static final int LODGING_RADIUS_M = 8_000;
    static final int ATTRACTION_RADIUS_M = 15_000;
    static final int RESTAURANT_RADIUS_M = 8_000;
    static final int CANDIDATES_PER_SEARCH = 10;
    static final int MAX_PREFERENCES = 200;

    public enum Status {
        /** interruttore spento: la funzione non compare */
        DISABLED,
        /** acceso ma senza chiave Google Places: non ci sono luoghi da proporre */
        MISSING_PLACES_KEY,
        /** pronto (con o senza Claude: senza, ordina per valutazione) */
        READY
    }

    public record Suggestion(Place place, String reason, double distanceKm, String near) {
    }

    public record Section(PlaceCategory category, List<Suggestion> items) {
    }

    /**
     * @param aiGenerated false se Claude non era disponibile e l'ordine è per valutazione
     */
    public record Suggestions(long stageId, Instant createdAt, boolean aiGenerated, String summary,
                              String preferences, List<Section> sections) {

        public boolean isEmpty() {
            return sections.stream().allMatch(s -> s.items().isEmpty());
        }

        /**
         * Sezioni da mostrare. Con una sola categoria richiesta la si mostra anche vuota ("nessun luogo adatto");
         * con più categorie si nascondono quelle lasciate vuote, ad esempio perché la richiesta riguardava
         * solo un ostello.
         */
        public List<Section> visibleSections() {
            if (sections.size() == 1 || isEmpty()) {
                return sections;
            }
            return sections.stream().filter(s -> !s.items().isEmpty()).toList();
        }

        Optional<Suggestion> find(String placeId) {
            return sections.stream().flatMap(s -> s.items().stream())
                .filter(i -> i.place().id().equals(placeId)).findFirst();
        }

        Optional<PlaceCategory> categoryOf(String placeId) {
            return sections.stream().filter(s -> s.items().stream().anyMatch(i -> i.place().id().equals(placeId)))
                .map(Section::category).findFirst();
        }
    }

    public static class SuggestionException extends RuntimeException {
        public SuggestionException(String message) {
            super(message);
        }
    }

    private record Cached(Instant stageUpdatedAt, Suggestions suggestions) {
    }

    private final AppProperties.Ai config;
    private final PlacesClient places;
    private final TravelCurator curator;
    private final TripService trips;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();
    private final Map<Long, Deque<Instant>> requestsByUser = new ConcurrentHashMap<>();

    public StageSuggestionService(AppProperties props, PlacesClient places, TravelCurator curator, TripService trips) {
        this.config = props.ai();
        this.places = places;
        this.curator = curator;
        this.trips = trips;
    }

    public Status status() {
        if (!config.enabled()) {
            return Status.DISABLED;
        }
        return places.isConfigured() ? Status.READY : Status.MISSING_PLACES_KEY;
    }

    public boolean isAiConfigured() {
        return curator.isConfigured();
    }

    /** Consigli già generati per la tappa, se ancora validi (stessa versione della tappa, entro il TTL). */
    public Optional<Suggestions> cached(Long userId, Long tripId, Long stageId) {
        if (status() == Status.DISABLED) {
            return Optional.empty();
        }
        Cached c = cache.get(stageId);
        if (c == null || c.suggestions().createdAt().plus(config.cacheTtl()).isBefore(Instant.now())) {
            return Optional.empty();
        }
        StageAiContext ctx = trips.aiContext(userId, tripId, stageId, false);   // controlla l'accesso
        return c.stageUpdatedAt().equals(ctx.updatedAt()) ? Optional.of(c.suggestions()) : Optional.empty();
    }

    /**
     * Consigli per una tappa salvata.
     *
     * @param categories categorie richieste; vuoto = tutte. Si cercano e si mostrano solo queste.
     */
    public Suggestions generate(Long userId, Long tripId, Long stageId, String rawPreferences,
                                Set<PlaceCategory> categories) {
        checkReady();
        StageAiContext stage = trips.aiContext(userId, tripId, stageId, true);
        checkRateLimit(userId);
        String preferences = cleanPreferences(rawPreferences);
        Set<PlaceCategory> requested = categories == null || categories.isEmpty()
            ? EnumSet.allOf(PlaceCategory.class) : EnumSet.copyOf(categories);

        Map<PlaceCategory, List<Candidate>> candidates =
            findCandidates(requested, stage.end(), stage.halfway(), stage.endName());
        if (candidates.values().stream().allMatch(List::isEmpty)) {
            throw new SuggestionException("Non ho trovato luoghi nei dintorni di questa tappa.");
        }
        Suggestions result = curateOrRank(stageId, new TravelCurator.StageContext(stage.title(), stage.date(),
            stage.startName(), stage.endName(), stage.km(), stage.duration(), stage.avoidMotorways(),
            stage.stops(), stage.notes(), stage.highlights(), preferences), candidates, preferences);
        cache.put(stageId, new Cached(stage.updatedAt(), result));
        return result;
    }

    /** Tappa non ancora salvata (form): partenza, fermate e arrivo come inseriti dall'utente. */
    public record Draft(String title, String startName, String endName, LatLon start, LatLon end,
                        List<LatLon> stops, boolean avoidMotorways, String preferences) {
    }

    /**
     * Consigli di una sola categoria per il form della tappa (anche prima del salvataggio).
     * Non vanno in cache: l'utente sceglie e il form viene compilato nel browser.
     */
    public Suggestions preview(Long userId, Long tripId, Draft draft, PlaceCategory category) {
        checkReady();
        trips.checkCanEdit(userId, tripId);
        checkRateLimit(userId);
        String preferences = cleanPreferences(draft.preferences());
        List<LatLon> points = new ArrayList<>();
        points.add(draft.start());
        points.addAll(draft.stops());
        points.add(draft.end());
        Map<PlaceCategory, List<Candidate>> candidates = findCandidates(EnumSet.of(category), draft.end(),
            TripService.halfway(points), draft.endName());
        if (candidates.values().stream().allMatch(List::isEmpty)) {
            throw new SuggestionException("Non ho trovato luoghi nei dintorni.");
        }
        return curateOrRank(0, new TravelCurator.StageContext(draft.title(), null, draft.startName(),
            draft.endName(), null, null, draft.avoidMotorways(), List.of(), null, null, preferences),
            candidates, preferences);
    }

    private Suggestions curateOrRank(long stageId, TravelCurator.StageContext context,
                                     Map<PlaceCategory, List<Candidate>> candidates, String preferences) {
        try {
            return fromCuration(stageId, curator.curate(context, candidates), candidates, preferences);
        } catch (CurationException e) {
            log.warn("Consigli AI non disponibili ({}): ordino per valutazione", e.getMessage());
            return byRating(stageId, candidates, preferences);
        }
    }

    private void checkReady() {
        if (status() == Status.DISABLED) {
            throw new SuggestionException("I consigli AI non sono attivi.");
        }
        if (status() == Status.MISSING_PLACES_KEY) {
            throw new SuggestionException("I consigli non sono disponibili: manca la configurazione di Google Places.");
        }
    }

    private static String cleanPreferences(String raw) {
        String p = raw == null ? null : raw.strip();
        if (p == null || p.isEmpty()) {
            return null;
        }
        return p.length() > MAX_PREFERENCES ? p.substring(0, MAX_PREFERENCES) : p;
    }

    // ---------------------------------------------------------------- azioni sui consigli

    /** Aggiunge un ristorante o una cosa da vedere come fermata della tappa (ricalcola il percorso). */
    public String addAsStop(Long userId, Long tripId, Long stageId, String placeId) {
        Suggestions s = currentOrFail(stageId);
        Suggestion item = s.find(placeId).orElseThrow(() -> new SuggestionException("Consiglio non trovato: rigenera i consigli."));
        PlaceCategory category = s.categoryOf(placeId).orElseThrow();
        if (category == PlaceCategory.LODGING) {
            throw new SuggestionException("Per gli alloggi usa \"Usa come alloggio\".");
        }
        Place p = item.place();
        trips.addStop(userId, tripId, stageId, p.name(),
            category == PlaceCategory.RESTAURANT ? StopType.FOOD : StopType.ATTRACTION,
            p.lat(), p.lon(), "Suggerito dai consigli AI");
        cache.remove(stageId);                                  // la tappa è cambiata
        return p.name();
    }

    /** Imposta un alloggio consigliato come alloggio di fine tappa. */
    public String useAsAccommodation(Long userId, Long tripId, Long stageId, String placeId) {
        Suggestions s = currentOrFail(stageId);
        Suggestion item = s.find(placeId).orElseThrow(() -> new SuggestionException("Consiglio non trovato: rigenera i consigli."));
        if (s.categoryOf(placeId).orElseThrow() != PlaceCategory.LODGING) {
            throw new SuggestionException("Questo luogo non è un alloggio.");
        }
        Place p = item.place();
        trips.setAccommodation(userId, tripId, stageId, accommodationType(p), p.name(), p.address(),
            p.website() != null ? p.website() : p.mapsUrl());
        cache.remove(stageId);
        return p.name();
    }

    // ---------------------------------------------------------------- interni

    /** Cerca solo le categorie richieste: la mappa restituita contiene solo quelle chiavi. */
    private Map<PlaceCategory, List<Candidate>> findCandidates(Set<PlaceCategory> categories, LatLon end,
                                                               LatLon middle, String endName) {
        Map<PlaceCategory, List<Candidate>> result = new EnumMap<>(PlaceCategory.class);
        String arrival = "arrivo (" + endName + ")";
        String halfway = "metà percorso";
        if (categories.contains(PlaceCategory.LODGING)) {
            result.put(PlaceCategory.LODGING, search(PlaceCategory.LODGING, end, LODGING_RADIUS_M, arrival));
        }
        if (categories.contains(PlaceCategory.ATTRACTION)) {
            // cose da vedere: lungo il percorso e all'arrivo, senza doppioni
            Map<String, Candidate> attractions = new LinkedHashMap<>();
            for (Candidate c : search(PlaceCategory.ATTRACTION, middle, ATTRACTION_RADIUS_M, halfway)) {
                attractions.putIfAbsent(c.place().id(), c);
            }
            for (Candidate c : search(PlaceCategory.ATTRACTION, end, LODGING_RADIUS_M, arrival)) {
                attractions.putIfAbsent(c.place().id(), c);
            }
            result.put(PlaceCategory.ATTRACTION, new ArrayList<>(attractions.values()));
        }
        if (categories.contains(PlaceCategory.RESTAURANT)) {
            result.put(PlaceCategory.RESTAURANT, search(PlaceCategory.RESTAURANT, middle, RESTAURANT_RADIUS_M, halfway));
        }
        return result;
    }

    private List<Candidate> search(PlaceCategory category, LatLon center, int radius, String near) {
        try {
            return places.searchNearby(category, center, radius, CANDIDATES_PER_SEARCH).stream()
                .map(p -> new Candidate(p, Math.round(center.distanceKm(new LatLon(p.lat(), p.lon())) * 10) / 10.0, near))
                .toList();
        } catch (PlacesClient.PlacesUnavailableException e) {
            log.warn("Ricerca {} non riuscita: {}", category, e.getMessage());
            return List.of();
        }
    }

    /** Solo id presenti tra i candidati, senza doppioni e nel limite della categoria. */
    static Suggestions fromCuration(long stageId, Curation curation, Map<PlaceCategory, List<Candidate>> candidates,
                                    String preferences) {
        List<Section> sections = new ArrayList<>();
        for (PlaceCategory category : candidates.keySet()) {               // solo le categorie richieste
            Map<String, Candidate> byId = new LinkedHashMap<>();
            candidates.getOrDefault(category, List.of()).forEach(c -> byId.put(c.place().id(), c));
            List<Suggestion> items = new ArrayList<>();
            for (Pick pick : curation.picks().getOrDefault(category, List.of())) {
                Candidate c = byId.remove(pick.placeId());          // remove: evita doppioni
                if (c != null && items.size() < category.maxPicks()) {
                    items.add(new Suggestion(c.place(), pick.reason(), c.distanceKm(), c.near()));
                }
            }
            sections.add(new Section(category, items));
        }
        return new Suggestions(stageId, Instant.now(), true, curation.summary(), preferences, sections);
    }

    static Suggestions byRating(long stageId, Map<PlaceCategory, List<Candidate>> candidates, String preferences) {
        List<Section> sections = new ArrayList<>();
        for (PlaceCategory category : candidates.keySet()) {
            List<Suggestion> items = candidates.getOrDefault(category, List.of()).stream()
                .filter(c -> c.place().rating() != null)
                .sorted(Comparator.comparingDouble((Candidate c) -> c.place().score()).reversed())
                .limit(category.maxPicks())
                .map(c -> new Suggestion(c.place(), null, c.distanceKm(), c.near()))
                .toList();
            sections.add(new Section(category, items));
        }
        return new Suggestions(stageId, Instant.now(), false, null, preferences, sections);
    }

    private Suggestions currentOrFail(Long stageId) {
        Cached c = cache.get(stageId);
        if (c == null) {
            throw new SuggestionException("I consigli sono scaduti: generali di nuovo.");
        }
        return c.suggestions();
    }

    private void checkRateLimit(Long userId) {
        Deque<Instant> q = requestsByUser.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (q) {
            Instant hourAgo = Instant.now().minusSeconds(3600);
            while (!q.isEmpty() && q.peekFirst().isBefore(hourAgo)) {
                q.pollFirst();
            }
            if (q.size() >= config.maxRequestsPerHour()) {
                throw new SuggestionException("Hai raggiunto il limite di " + config.maxRequestsPerHour()
                    + " richieste di consigli all'ora. Riprova più tardi.");
            }
            q.addLast(Instant.now());
        }
    }

    private static AccommodationType accommodationType(Place p) {
        String t = p.typeLabel() == null ? "" : p.typeLabel().toLowerCase();
        if (t.contains("ostello") || t.contains("hostel")) {
            return AccommodationType.HOSTEL;
        }
        if (t.contains("b&b") || t.contains("bed")) {
            return AccommodationType.BNB;
        }
        return AccommodationType.OTHER;
    }
}
