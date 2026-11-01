package com.motorbike.photo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProfilePhotoRepository extends JpaRepository<ProfilePhoto, Long> {

    @Query("select p from ProfilePhoto p join fetch p.user where p.status = :status order by p.createdAt")
    List<ProfilePhoto> findByStatusWithUser(PhotoStatus status);

    long countByStatus(PhotoStatus status);

    /** Ultima foto non ancora approvata (in attesa o rifiutata) dell'utente. */
    Optional<ProfilePhoto> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(Long userId,
                                                                            List<PhotoStatus> statuses);

    @Modifying
    @Query("delete from ProfilePhoto p where p.user.id = :userId and p.status <> com.motorbike.photo.PhotoStatus.APPROVED")
    void deleteNotApproved(Long userId);
}
