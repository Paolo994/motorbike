package com.motorbike.trip;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.motorbike.friend.FriendshipService;
import com.motorbike.friend.UserCard;
import com.motorbike.fuel.FuelPriceService;
import com.motorbike.geo.LatLon;
import com.motorbike.geo.RouteResult;
import com.motorbike.geo.RoutingService;
import com.motorbike.notification.NotificationService;
import com.motorbike.notification.NotificationType;
import com.motorbike.trip.TripEnums.TripRole;
import com.motorbike.trip.TripExceptions.InvalidOperation;
import com.motorbike.trip.TripExceptions.NotAllowed;
import com.motorbike.trip.TripExceptions.TripNotFound;
import com.motorbike.trip.TripViews.BikeParams;
import com.motorbike.trip.TripViews.FuelEstimate;
import com.motorbike.trip.TripViews.MemberView;
import com.motorbike.trip.TripViews.StageDetail;
import com.motorbike.trip.TripViews.StageView;
import com.motorbike.trip.TripViews.StopView;
import com.motorbike.trip.TripViews.Totals;
import com.motorbike.trip.TripViews.TripSummary;
import com.motorbike.trip.TripViews.TripView;
import com.motorbike.user.User;
import com.motorbike.user.UserNotFoundException;
import com.motorbike.user.UserRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Road trip: viaggi, partecipanti, tappe e fermate.
 * Permessi: tutti i partecipanti vedono; OWNER ed EDITOR modificano le tappe; solo OWNER gestisce viaggio e partecipanti.
 */
@Service
public class TripService {

    /** Raggio in cui cercare i distributori più economici vicino alla partenza della tappa. */
    static final double CHEAP_FUEL_RADIUS_KM = 10;

    private final TripRepository trips;
    private final TripMemberRepository members;
    private final TripStageRepository stages;
    private final UserRepository users;
    private final FriendshipService friendships;
    private final NotificationService notifications;
    private final RoutingService routing;
    private final TripCostCalculator costs;
    private final FuelPriceService fuel;
    private final JsonMapper json;
    private final TransactionTemplate tx;

    public TripService(TripRepository trips, TripMemberRepository members, TripStageRepository stages,
                       UserRepository users, FriendshipService friendships, NotificationService notifications,
                       RoutingService routing, TripCostCalculator costs, FuelPriceService fuel, JsonMapper json,
                       TransactionTemplate tx) {
        this.trips = trips;
        this.members = members;
        this.stages = stages;
        this.users = users;
        this.friendships = friendships;
        this.notifications = notifications;
        this.routing = routing;
        this.costs = costs;
        this.fuel = fuel;
        this.json = json;
        this.tx = tx;
    }

    // ================================================================ viaggi

    @Transactional(readOnly = true)
    public List<TripSummary> myTrips(Long userId) {
        List<TripMember> mine = members.findByUserWithTrip(userId);
        if (mine.isEmpty()) {
            return List.of();
        }
        Map<Long, Object[]> stats = new HashMap<>();
        for (Object[] row : stages.statsByTrip(mine.stream().map(m -> m.getTrip().getId()).toList())) {
            stats.put((Long) row[0], row);
        }
        return mine.stream().map(m -> {
            Trip t = m.getTrip();
            Object[] s = stats.get(t.getId());
            return new TripSummary(t.getId(), t.getTitle(), t.getStartDate(), t.getOwner().getUsername(), m.getRole(),
                s == null ? 0 : (Long) s[1], s == null ? 0 : Math.round(((Number) s[2]).doubleValue() / 100) / 10.0);
        }).toList();
    }

    @Transactional
    public long create(Long userId, TripForm form) {
        User owner = users.getReferenceById(userId);
        Trip trip = trips.save(new Trip(owner, form.getTitle().strip(), blankToNull(form.getDescription()),
            form.getStartDate()));
        members.save(new TripMember(trip, owner, TripRole.OWNER));
        return trip.getId();
    }

    @Transactional
    public void update(Long userId, Long tripId, TripForm form) {
        requireOwner(tripId, userId).getTrip()
            .update(form.getTitle().strip(), blankToNull(form.getDescription()), form.getStartDate());
    }

    @Transactional(readOnly = true)
    public TripForm editForm(Long userId, Long tripId) {
        return TripForm.from(requireOwner(tripId, userId).getTrip());
    }

    @Transactional
    public void delete(Long userId, Long tripId) {
        trips.delete(requireOwner(tripId, userId).getTrip());
    }

    @Transactional(readOnly = true)
    public TripView view(Long userId, Long tripId) {
        TripMember me = requireMember(tripId, userId);
        BikeParams bike = costs.bikeOf(users.findById(userId).orElseThrow());
        List<StageView> stageViews = stages.findByTripWithStops(tripId).stream()
            .map(s -> toView(s, bike, false)).toList();
        return buildTripView(me, bike, stageViews);
    }

    // ================================================================ partecipanti

    @Transactional(readOnly = true)
    public List<UserCard> invitableFriends(Long userId, Long tripId) {
        requireOwner(tripId, userId);
        Set<Long> already = members.findByTripWithUser(tripId).stream()
            .map(m -> m.getUser().getId()).collect(Collectors.toSet());
        return friendships.overview(userId).friends().stream()
            .filter(f -> users.findByUsernameIgnoreCase(f.username()).map(u -> !already.contains(u.getId())).orElse(false))
            .toList();
    }

    @Transactional
    public void invite(Long userId, Long tripId, String username, TripRole role) {
        TripMember owner = requireOwner(tripId, userId);
        if (role == TripRole.OWNER) {
            throw new InvalidOperation("Il viaggio ha già un organizzatore.");
        }
        User invitee = users.findByUsernameIgnoreCase(username).filter(User::isEnabled)
            .orElseThrow(() -> new UserNotFoundException(username));
        if (!friendships.areFriends(userId, invitee.getId())) {
            throw new InvalidOperation("Puoi invitare solo i tuoi amici.");
        }
        if (members.findByTripIdAndUserId(tripId, invitee.getId()).isPresent()) {
            throw new InvalidOperation(invitee.getUsername() + " partecipa già al viaggio.");
        }
        Trip trip = owner.getTrip();
        members.save(new TripMember(trip, invitee, role));
        notifications.notify(invitee, NotificationType.TRIP_INVITE,
            owner.getUser().getUsername() + " ti ha aggiunto a un road trip",
            trip.getTitle() + (role == TripRole.EDITOR ? " · puoi modificare le tappe" : ""), "/trips/" + tripId);
    }

    @Transactional
    public void changeRole(Long userId, Long tripId, Long memberUserId, TripRole role) {
        requireOwner(tripId, userId);
        TripMember m = members.findByTripIdAndUserId(tripId, memberUserId).orElseThrow(TripNotFound::new);
        if (m.getRole() == TripRole.OWNER || role == TripRole.OWNER) {
            throw new InvalidOperation("Il ruolo dell'organizzatore non può essere cambiato.");
        }
        m.setRole(role);
    }

    @Transactional
    public void removeMember(Long userId, Long tripId, Long memberUserId) {
        requireOwner(tripId, userId);
        TripMember m = members.findByTripIdAndUserId(tripId, memberUserId).orElseThrow(TripNotFound::new);
        if (m.getRole() == TripRole.OWNER) {
            throw new InvalidOperation("L'organizzatore non può essere rimosso: elimina il viaggio.");
        }
        members.delete(m);
    }

    @Transactional
    public void leave(Long userId, Long tripId) {
        TripMember me = requireMember(tripId, userId);
        if (me.getRole() == TripRole.OWNER) {
            throw new InvalidOperation("Sei l'organizzatore: per lasciare il viaggio eliminalo.");
        }
        members.delete(me);
    }

    // ================================================================ tappe

    @Transactional(readOnly = true)
    public StageDetail stageDetail(Long userId, Long tripId, Long stageId) {
        TripMember me = requireMember(tripId, userId);
        BikeParams bike = costs.bikeOf(users.findById(userId).orElseThrow());
        List<TripStage> all = stages.findByTripWithStops(tripId);
        int index = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(stageId)) {
                index = i;
            }
        }
        if (index < 0) {
            throw new TripNotFound();
        }
        TripStage stage = all.get(index);
        List<StageView> views = all.stream().map(s -> s.getId().equals(stageId) ? toView(s, bike, true)
            : toView(s, bike, false)).toList();
        return new StageDetail(buildTripView(me, bike, views), views.get(index),
            fuel.cheapestNear(new LatLon(stage.getStartLat(), stage.getStartLon()), CHEAP_FUEL_RADIUS_KM, 5),
            index > 0 ? all.get(index - 1).getId() : null,
            index < all.size() - 1 ? all.get(index + 1).getId() : null);
    }

    /** Nuova tappa: parte da dove finisce l'ultima, il giorno dopo. */
    @Transactional(readOnly = true)
    public StageForm newStageForm(Long userId, Long tripId) {
        TripMember me = requireEditor(tripId, userId);
        StageForm form = new StageForm();
        List<TripStage> all = stages.findByTripWithStops(tripId);
        if (all.isEmpty()) {
            form.setStageDate(me.getTrip().getStartDate());
            form.setTitle("Tappa 1");
        } else {
            TripStage last = all.getLast();
            form.setStartName(last.getEndName());
            form.setStartLat(last.getEndLat());
            form.setStartLon(last.getEndLon());
            form.setStageDate(last.getStageDate() == null ? null : last.getStageDate().plusDays(1));
            form.setTitle("Tappa " + (all.size() + 1));
            form.setAvoidMotorways(last.isAvoidMotorways());
            form.setAvoidTolls(last.isAvoidTolls());
        }
        return form;
    }

    @Transactional(readOnly = true)
    public StageForm stageForm(Long userId, Long tripId, Long stageId) {
        requireEditor(tripId, userId);
        return StageForm.from(stages.findInTrip(tripId, stageId).orElseThrow(TripNotFound::new));
    }

    /**
     * Crea o aggiorna una tappa e ne calcola il percorso. Il routing (servizio esterno, fino a 30 s)
     * avviene fuori dalla transazione per non tenere occupata una connessione al database.
     *
     * @param stageId null per una nuova tappa
     * @return id della tappa
     */
    public long saveStage(Long userId, Long tripId, Long stageId, StageForm form) {
        tx.executeWithoutResult(s -> requireEditor(tripId, userId));

        List<StageForm.StopForm> stopForms = form.getStops().stream().filter(StageForm.StopForm::isFilled).toList();
        List<LatLon> points = new ArrayList<>();
        points.add(new LatLon(form.getStartLat(), form.getStartLon()));
        stopForms.forEach(sf -> points.add(new LatLon(sf.getLat(), sf.getLon())));
        points.add(new LatLon(form.getEndLat(), form.getEndLon()));
        RouteResult route = routing.route(points, form.isAvoidMotorways(), form.isAvoidTolls());

        return tx.execute(s -> {
            TripMember me = requireEditor(tripId, userId);
            TripStage stage = stageId == null
                ? new TripStage(me.getTrip(), stages.maxPosition(tripId) + 1)
                : stages.findInTrip(tripId, stageId).orElseThrow(TripNotFound::new);
            stage.setTitle(form.getTitle().strip());
            stage.setStageDate(form.getStageDate());
            stage.setStart(form.getStartName().strip(), form.getStartLat(), form.getStartLon());
            stage.setEnd(form.getEndName().strip(), form.getEndLat(), form.getEndLon());
            stage.setAvoidMotorways(form.isAvoidMotorways());
            stage.setAvoidTolls(form.isAvoidTolls());
            stage.setNotes(blankToNull(form.getNotes()));
            stage.setHighlights(blankToNull(form.getHighlights()));
            stage.setAccommodation(form.getAccommodationType(), blankToNull(form.getAccommodationName()),
                blankToNull(form.getAccommodationAddress()), form.getAccommodationPrice(),
                blankToNull(form.getAccommodationUrl()));
            List<TripStop> newStops = new ArrayList<>();
            for (int i = 0; i < stopForms.size(); i++) {
                StageForm.StopForm sf = stopForms.get(i);
                String name = sf.getName() == null || sf.getName().isBlank() ? "Fermata " + (i + 1) : sf.getName().strip();
                newStops.add(new TripStop(stage, i + 1, name,
                    sf.getType() == null ? TripEnums.StopType.OTHER : sf.getType(),
                    sf.getLat(), sf.getLon(), blankToNull(sf.getNotes())));
            }
            stage.replaceStops(newStops);
            stage.applyRoute(route, json.writeValueAsString(route.maneuvers()));
            stage.touch();
            me.getTrip().touch();
            return stages.save(stage).getId();
        });
    }

    /** Ricalcola il percorso (es. se al salvataggio il servizio di routing non rispondeva). */
    public void reroute(Long userId, Long tripId, Long stageId) {
        TripStage stage = tx.execute(s -> {
            requireEditor(tripId, userId);
            TripStage st = stages.findInTrip(tripId, stageId).orElseThrow(TripNotFound::new);
            st.getStops().size();                                   // inizializza la collezione
            return st;
        });
        RouteResult route = routing.route(stage.waypoints(), stage.isAvoidMotorways(), stage.isAvoidTolls());
        tx.executeWithoutResult(s -> stages.findInTrip(tripId, stageId).ifPresent(st -> {
            st.applyRoute(route, json.writeValueAsString(route.maneuvers()));
            st.touch();
        }));
    }

    @Transactional
    public void deleteStage(Long userId, Long tripId, Long stageId) {
        requireEditor(tripId, userId);
        stages.delete(stages.findInTrip(tripId, stageId).orElseThrow(TripNotFound::new));
        stages.flush();
        renumber(tripId);
    }

    @Transactional
    public void moveStage(Long userId, Long tripId, Long stageId, boolean up) {
        requireEditor(tripId, userId);
        List<TripStage> all = new ArrayList<>(stages.findByTripWithStops(tripId));
        int i = -1;
        for (int k = 0; k < all.size(); k++) {
            if (all.get(k).getId().equals(stageId)) {
                i = k;
            }
        }
        if (i < 0) {
            throw new TripNotFound();
        }
        int j = up ? i - 1 : i + 1;
        if (j < 0 || j >= all.size()) {
            return;
        }
        TripStage a = all.get(i);
        all.set(i, all.get(j));
        all.set(j, a);
        for (int k = 0; k < all.size(); k++) {
            all.get(k).setPosition(k + 1);
        }
    }

    // ================================================================ helper

    private void renumber(Long tripId) {
        List<TripStage> all = stages.findByTripWithStops(tripId);
        for (int k = 0; k < all.size(); k++) {
            all.get(k).setPosition(k + 1);
        }
    }

    TripMember requireMember(Long tripId, Long userId) {
        return members.findByTripIdAndUserId(tripId, userId).orElseThrow(TripNotFound::new);
    }

    TripMember requireEditor(Long tripId, Long userId) {
        TripMember m = requireMember(tripId, userId);
        if (!m.getRole().canEdit()) {
            throw new NotAllowed("Hai accesso in sola lettura a questo viaggio.");
        }
        return m;
    }

    TripMember requireOwner(Long tripId, Long userId) {
        TripMember m = requireMember(tripId, userId);
        if (m.getRole() != TripRole.OWNER) {
            throw new NotAllowed("Solo l'organizzatore può farlo.");
        }
        return m;
    }

    private TripView buildTripView(TripMember me, BikeParams bike, List<StageView> stageViews) {
        Trip trip = me.getTrip();
        List<MemberView> memberViews = members.findByTripWithUser(trip.getId()).stream()
            .sorted(Comparator.comparing((TripMember m) -> m.getRole().ordinal()).thenComparing(m -> m.getUser().getUsername()))
            .map(m -> new MemberView(m.getUser().getId(), m.getUser().getUsername(), m.getUser().getShownName(),
                m.getUser().getPhotoId(), m.getRole()))
            .toList();
        return new TripView(trip.getId(), trip.getTitle(), trip.getDescription(), trip.getStartDate(), me.getRole(),
            me.getRole().canEdit(), me.getRole() == TripRole.OWNER, memberViews, stageViews, totals(stageViews), bike);
    }

    private StageView toView(TripStage s, BikeParams bike, boolean withDirections) {
        FuelEstimate fuelEstimate = costs.estimate(s, bike);
        List<StopView> stopViews = s.getStops().stream()
            .map(st -> new StopView(st.getName(), st.getType().getLabel(), st.getType().getIcon(), st.getLat(),
                st.getLon(), st.getNotes()))
            .toList();
        return new StageView(s.getId(), s.getPosition(), s.getTitle(), s.getStageDate(),
            s.getStartName(), s.getStartLat(), s.getStartLon(), s.getEndName(), s.getEndLat(), s.getEndLon(),
            s.isAvoidMotorways(), s.isAvoidTolls(), s.getRouteStatus(),
            s.getDistanceM() == null ? null : Math.round(s.getDistanceM() / 100.0) / 10.0,
            s.getDurationS(), TripViews.formatDuration(s.getDurationS()), s.getHasMotorway(), s.getHasToll(),
            s.getRouteShape(),
            withDirections ? directions(s.getDirectionsJson()) : List.of(),
            s.getNotes(), s.getHighlights(),
            s.getAccommodationType() == null ? null : s.getAccommodationType().getLabel(),
            s.getAccommodationName(), s.getAccommodationAddress(), s.getAccommodationPrice(), s.getAccommodationUrl(),
            stopViews, fuelEstimate);
    }

    private List<RouteResult.Maneuver> directions(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return List.of();
        }
        List<RouteResult.Maneuver> result = new ArrayList<>();
        for (JsonNode n : json.readTree(jsonText)) {
            result.add(new RouteResult.Maneuver(n.path("text").asString(""), n.path("km").asDouble()));
        }
        return result;
    }

    private static Totals totals(List<StageView> views) {
        double km = 0;
        int seconds = 0;
        double liters = 0;
        BigDecimal fuelCost = BigDecimal.ZERO;
        BigDecimal accommodation = BigDecimal.ZERO;
        for (StageView v : views) {
            if (v.km() != null) {
                km += v.km();
            }
            if (v.durationSeconds() != null) {
                seconds += v.durationSeconds();
            }
            if (v.fuel() != null) {
                liters += v.fuel().liters();
                if (v.fuel().cost() != null) {
                    fuelCost = fuelCost.add(v.fuel().cost());
                }
            }
            if (v.accommodationPrice() != null) {
                accommodation = accommodation.add(v.accommodationPrice());
            }
        }
        return new Totals(Math.round(km * 10) / 10.0, seconds > 0 ? TripViews.formatDuration(seconds) : null,
            Math.round(liters * 10) / 10.0, fuelCost, accommodation, fuelCost.add(accommodation));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
