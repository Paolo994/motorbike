package com.motorbike.chat;

/** Messaggio come viene inviato al browser (JSON via HTTP e via WebSocket). */
public record MessageDto(long id, long conversationId, String sender, String senderName, String body,
                         String createdAt) {

    static MessageDto from(Message m, Long conversationId) {
        return new MessageDto(m.getId(), conversationId, m.getSender().getUsername(), m.getSender().getShownName(),
            m.getBody(), m.getCreatedAt().toString());
    }
}
