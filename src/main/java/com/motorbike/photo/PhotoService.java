package com.motorbike.photo;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.motorbike.notification.NotificationService;
import com.motorbike.notification.NotificationType;
import com.motorbike.photo.ImageProcessor.InvalidImageException;
import com.motorbike.user.User;
import com.motorbike.user.UserRepository;

@Service
public class PhotoService {

    /** Foto pronta da servire al browser. */
    public record PhotoContent(byte[] data, String contentType, boolean publiclyCacheable) {
    }

    private final ProfilePhotoRepository photos;
    private final UserRepository users;
    private final NotificationService notifications;

    public PhotoService(ProfilePhotoRepository photos, UserRepository users, NotificationService notifications) {
        this.photos = photos;
        this.users = users;
        this.notifications = notifications;
    }

    /**
     * Carica una nuova foto in attesa di approvazione; sostituisce eventuali foto in attesa o rifiutate.
     * La foto approvata attuale resta visibile finché la nuova non viene approvata.
     *
     * @throws InvalidImageException se il file non è un'immagine accettabile
     */
    @Transactional
    public void upload(Long userId, byte[] file) {
        if (file == null || file.length == 0) {
            throw new InvalidImageException("Seleziona un'immagine da caricare.");
        }
        byte[] jpeg = ImageProcessor.toSquareJpeg(file);
        User user = users.getReferenceById(userId);
        photos.deleteNotApproved(userId);
        photos.flush();
        photos.save(new ProfilePhoto(user, jpeg));
        notifications.notifyAdmins(NotificationType.PHOTO_PENDING_REVIEW,
            "Nuova foto profilo da approvare", "Caricata da @" + user.getUsername(), "/admin/photos");
    }

    /** Rimuove la foto approvata in uso e quelle non approvate. */
    @Transactional
    public void removeAll(Long userId) {
        User user = users.findById(userId).orElseThrow();
        Long current = user.getPhotoId();
        user.setPhotoId(null);
        photos.deleteNotApproved(userId);
        if (current != null) {
            photos.deleteById(current);
        }
    }

    /** Ultima foto in attesa o rifiutata (per mostrare lo stato all'utente). */
    @Transactional(readOnly = true)
    public Optional<ProfilePhoto> latestNotApproved(Long userId) {
        return photos.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(userId,
            List.of(PhotoStatus.PENDING, PhotoStatus.REJECTED));
    }

    // ---------------------------------------------------------------- moderazione

    @Transactional(readOnly = true)
    public List<ProfilePhoto> pending() {
        return photos.findByStatusWithUser(PhotoStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public long pendingCount() {
        return photos.countByStatus(PhotoStatus.PENDING);
    }

    @Transactional
    public void approve(Long adminId, Long photoId) {
        ProfilePhoto photo = pendingPhoto(photoId);
        User owner = photo.getUser();
        Long previous = owner.getPhotoId();
        photo.approve(adminId);
        owner.setPhotoId(photo.getId());
        if (previous != null) {
            photos.deleteById(previous);
        }
        notifications.notify(owner, NotificationType.PHOTO_APPROVED,
            "La tua foto profilo è stata approvata", "Ora è visibile a tutti.", "/u/" + owner.getUsername());
    }

    @Transactional
    public void reject(Long adminId, Long photoId, String reason) {
        ProfilePhoto photo = pendingPhoto(photoId);
        String cleaned = reason == null || reason.isBlank() ? null : reason.strip();
        if (cleaned != null && cleaned.length() > 300) {
            cleaned = cleaned.substring(0, 300);
        }
        photo.reject(adminId, cleaned);
        notifications.notify(photo.getUser(), NotificationType.PHOTO_REJECTED,
            "La tua foto profilo non è stata approvata",
            cleaned != null ? "Motivo: " + cleaned : "Puoi caricarne un'altra dal tuo profilo.", "/profile/edit");
    }

    // ---------------------------------------------------------------- lettura

    /**
     * Foto approvate: visibili a ogni utente autenticato. In attesa o rifiutate: solo al proprietario e agli admin.
     */
    @Transactional(readOnly = true)
    public Optional<PhotoContent> load(Long photoId, Long viewerId, boolean viewerIsAdmin) {
        return photos.findById(photoId)
            .filter(p -> p.getStatus() == PhotoStatus.APPROVED || viewerIsAdmin
                || p.getUser().getId().equals(viewerId))
            .map(p -> new PhotoContent(p.getData(), p.getContentType(), p.getStatus() == PhotoStatus.APPROVED));
    }

    private ProfilePhoto pendingPhoto(Long photoId) {
        return photos.findById(photoId)
            .filter(p -> p.getStatus() == PhotoStatus.PENDING)
            .orElseThrow(() -> new PhotoNotFoundException(photoId));
    }
}
