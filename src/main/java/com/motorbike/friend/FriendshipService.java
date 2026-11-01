package com.motorbike.friend;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.motorbike.user.User;
import com.motorbike.user.UserNotFoundException;
import com.motorbike.user.UserRepository;

@Service
public class FriendshipService {

    public static final int MIN_QUERY_LENGTH = 2;
    private static final int SEARCH_LIMIT = 20;

    public record Overview(List<UserCard> friends, List<UserCard> incoming, List<UserCard> outgoing) {
    }

    private final FriendshipRepository friendships;
    private final UserRepository users;

    public FriendshipService(FriendshipRepository friendships, UserRepository users) {
        this.friendships = friendships;
        this.users = users;
    }

    // ---------------------------------------------------------------- letture

    @Transactional(readOnly = true)
    public Relation relation(Long meId, Long otherId) {
        if (meId.equals(otherId)) {
            return Relation.SELF;
        }
        return friendships.findBetween(meId, otherId)
            .map(f -> f.relationFor(meId))
            .orElse(Relation.NONE);
    }

    @Transactional(readOnly = true)
    public boolean areFriends(Long a, Long b) {
        return relation(a, b) == Relation.FRIENDS;
    }

    @Transactional(readOnly = true)
    public long countIncoming(Long meId) {
        return friendships.countByAddresseeIdAndStatus(meId, FriendshipStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public Overview overview(Long meId) {
        List<UserCard> friends = friendships.findAccepted(meId).stream()
            .map(f -> UserCard.of(f.other(meId), Relation.FRIENDS, f.getRespondedAt()))
            .sorted(Comparator.comparing(c -> c.username().toLowerCase(Locale.ROOT)))
            .toList();
        List<UserCard> incoming = friendships.findIncoming(meId).stream()
            .map(f -> UserCard.of(f.getRequester(), Relation.INCOMING, f.getCreatedAt()))
            .toList();
        List<UserCard> outgoing = friendships.findOutgoing(meId).stream()
            .map(f -> UserCard.of(f.getAddressee(), Relation.OUTGOING, f.getCreatedAt()))
            .toList();
        return new Overview(friends, incoming, outgoing);
    }

    @Transactional(readOnly = true)
    public List<UserCard> search(Long meId, String query) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (q.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        String escaped = escapeLike(q);
        List<User> found = users.searchByUsername("%" + escaped + "%", escaped + "%", meId,
            PageRequest.of(0, SEARCH_LIMIT));
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, Friendship> byOther = friendships
            .findBetweenUserAnd(meId, found.stream().map(User::getId).toList()).stream()
            .collect(Collectors.toMap(f -> f.other(meId).getId(), Function.identity()));
        return found.stream()
            .map(u -> {
                Friendship f = byOther.get(u.getId());
                return UserCard.of(u, f == null ? Relation.NONE : f.relationFor(meId), null);
            })
            .toList();
    }

    // ---------------------------------------------------------------- azioni

    /**
     * Invia una richiesta. Se l'altro utente mi aveva già scritto, la richiesta viene accettata subito.
     *
     * @return la relazione risultante (OUTGOING oppure FRIENDS)
     */
    @Transactional
    public Relation sendRequest(Long meId, String username) {
        User me = users.getReferenceById(meId);
        User target = activeUser(username);
        if (target.getId().equals(meId)) {
            throw new FriendshipException("Non puoi inviare una richiesta di amicizia a te stesso.");
        }
        var existing = friendships.findBetween(meId, target.getId());
        if (existing.isPresent()) {
            Friendship f = existing.get();
            return switch (f.relationFor(meId)) {
                case FRIENDS -> throw new FriendshipException("Siete già amici.");
                case OUTGOING -> throw new FriendshipException("Hai già inviato una richiesta a questo utente.");
                case INCOMING -> {
                    f.accept();
                    yield Relation.FRIENDS;
                }
                default -> throw new IllegalStateException();
            };
        }
        try {
            friendships.saveAndFlush(new Friendship(me, target));
        } catch (DataIntegrityViolationException e) {
            // richiesta concorrente tra gli stessi due utenti
            throw new FriendshipException("Esiste già una richiesta tra voi due.");
        }
        return Relation.OUTGOING;
    }

    @Transactional
    public void accept(Long meId, String username) {
        pendingFrom(username, meId).accept();
    }

    @Transactional
    public void decline(Long meId, String username) {
        friendships.delete(pendingFrom(username, meId));
    }

    @Transactional
    public void cancel(Long meId, String username) {
        User target = activeUser(username);
        Friendship f = friendships.findBetween(meId, target.getId())
            .filter(x -> x.relationFor(meId) == Relation.OUTGOING)
            .orElseThrow(() -> new FriendshipException("Nessuna richiesta inviata a questo utente."));
        friendships.delete(f);
    }

    @Transactional
    public void remove(Long meId, String username) {
        User target = activeUser(username);
        Friendship f = friendships.findBetween(meId, target.getId())
            .filter(Friendship::isAccepted)
            .orElseThrow(() -> new FriendshipException("Questo utente non è tra i tuoi amici."));
        friendships.delete(f);
    }

    // ---------------------------------------------------------------- helper

    /** Richiesta in attesa inviata da {@code username} a {@code meId}. */
    private Friendship pendingFrom(String username, Long meId) {
        User requester = activeUser(username);
        return friendships.findBetween(meId, requester.getId())
            .filter(f -> f.relationFor(meId) == Relation.INCOMING)
            .orElseThrow(() -> new FriendshipException("Nessuna richiesta in attesa da questo utente."));
    }

    private User activeUser(String username) {
        return users.findByUsernameIgnoreCase(username)
            .filter(User::isEnabled)
            .orElseThrow(() -> new UserNotFoundException(username));
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
