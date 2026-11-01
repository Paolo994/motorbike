package com.motorbike.friend;

import java.time.Instant;

import com.motorbike.user.User;

/** Dati di un utente mostrati in liste e risultati di ricerca, con la relazione verso l'utente corrente. */
public record UserCard(String username, String shownName, String city, String bikeModel, Long photoId,
                       Relation relation, Instant since) {

    static UserCard of(User user, Relation relation, Instant since) {
        return new UserCard(user.getUsername(), user.getShownName(), user.getCity(), user.getBikeModel(),
            user.getPhotoId(), relation, since);
    }
}
