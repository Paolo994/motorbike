package com.motorbike.chat;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByDirectKey(String directKey);

    /** Crea la conversazione diretta se non esiste; sicuro anche con richieste concorrenti. */
    @Modifying
    @Query(value = """
        insert into conversations (type, direct_key, created_at)
        values ('DIRECT', :key, now())
        on conflict (direct_key) do nothing
        """, nativeQuery = true)
    void insertDirectIfAbsent(String key);
}
