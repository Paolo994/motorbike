package com.motorbike.trip;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TripStageRepository extends JpaRepository<TripStage, Long> {

    @Query("select distinct s from TripStage s left join fetch s.stops where s.trip.id = :tripId order by s.position")
    List<TripStage> findByTripWithStops(Long tripId);

    @Query("select s from TripStage s left join fetch s.stops where s.id = :id and s.trip.id = :tripId")
    Optional<TripStage> findInTrip(Long tripId, Long id);

    @Query("select coalesce(max(s.position), 0) from TripStage s where s.trip.id = :tripId")
    int maxPosition(Long tripId);

    @Query("select s.trip.id, count(s), coalesce(sum(s.distanceM), 0) from TripStage s where s.trip.id in :tripIds group by s.trip.id")
    List<Object[]> statsByTrip(List<Long> tripIds);
}
