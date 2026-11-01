package com.motorbike.friend;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    @Query("""
        select f from Friendship f
        where (f.requester.id = :a and f.addressee.id = :b)
           or (f.requester.id = :b and f.addressee.id = :a)
        """)
    Optional<Friendship> findBetween(Long a, Long b);

    @Query("""
        select f from Friendship f join fetch f.requester join fetch f.addressee
        where f.status = com.motorbike.friend.FriendshipStatus.ACCEPTED
          and (f.requester.id = :userId or f.addressee.id = :userId)
        """)
    List<Friendship> findAccepted(Long userId);

    @Query("""
        select f from Friendship f join fetch f.requester
        where f.addressee.id = :userId and f.status = com.motorbike.friend.FriendshipStatus.PENDING
        order by f.createdAt desc
        """)
    List<Friendship> findIncoming(Long userId);

    @Query("""
        select f from Friendship f join fetch f.addressee
        where f.requester.id = :userId and f.status = com.motorbike.friend.FriendshipStatus.PENDING
        order by f.createdAt desc
        """)
    List<Friendship> findOutgoing(Long userId);

    long countByAddresseeIdAndStatus(Long addresseeId, FriendshipStatus status);

    /** Relazioni tra {@code userId} e uno qualsiasi degli utenti indicati (per i risultati di ricerca). */
    @Query("""
        select f from Friendship f
        where (f.requester.id = :userId and f.addressee.id in :others)
           or (f.addressee.id = :userId and f.requester.id in :others)
        """)
    List<Friendship> findBetweenUserAnd(Long userId, Collection<Long> others);
}
