package com.motorbike.chat;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    Optional<Participant> findByConversationIdAndUserId(Long conversationId, Long userId);

    @Query("select p from Participant p join fetch p.user where p.conversation.id = :conversationId")
    List<Participant> findWithUser(Long conversationId);

    @Modifying
    @Query(value = """
        insert into conversation_participants (conversation_id, user_id, joined_at)
        values (:conversationId, :userId, now())
        on conflict (conversation_id, user_id) do nothing
        """, nativeQuery = true)
    void insertIfAbsent(Long conversationId, Long userId);
}
