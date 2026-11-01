package com.motorbike.chat;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("""
        select m from Message m join fetch m.sender
        where m.conversation.id = :conversationId
        order by m.id desc
        """)
    List<Message> findLatest(Long conversationId, Pageable page);

    @Query("""
        select m from Message m join fetch m.sender
        where m.conversation.id = :conversationId and m.id < :beforeId
        order by m.id desc
        """)
    List<Message> findBefore(Long conversationId, Long beforeId, Pageable page);

    @Query("select max(m.id) from Message m where m.conversation.id = :conversationId")
    Optional<Long> findLastId(Long conversationId);
}
