package com.motorbike.trip;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TripMemberRepository extends JpaRepository<TripMember, Long> {

    Optional<TripMember> findByTripIdAndUserId(Long tripId, Long userId);

    @Query("select m from TripMember m join fetch m.user where m.trip.id = :tripId order by m.role, m.createdAt")
    List<TripMember> findByTripWithUser(Long tripId);

    @Query("select m from TripMember m join fetch m.trip t join fetch t.owner where m.user.id = :userId order by t.startDate desc nulls last, t.createdAt desc")
    List<TripMember> findByUserWithTrip(Long userId);
}
