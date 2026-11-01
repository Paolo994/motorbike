package com.motorbike.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.motorbike.chat.ChatExceptions.ConversationNotFound;
import com.motorbike.chat.ChatExceptions.InvalidMessage;
import com.motorbike.chat.ChatExceptions.NotAllowed;
import com.motorbike.friend.FriendshipService;
import com.motorbike.user.User;
import com.motorbike.user.UserNotFoundException;
import com.motorbike.user.UserRepository;

@Service
public class ChatService {

    static final int PAGE_SIZE = 50;

    private final ConversationRepository conversations;
    private final ParticipantRepository participants;
    private final MessageRepository messages;
    private final UserRepository users;
    private final FriendshipService friendships;
    private final ApplicationEventPublisher events;
    private final JdbcClient jdbc;

    public ChatService(ConversationRepository conversations, ParticipantRepository participants,
                       MessageRepository messages, UserRepository users, FriendshipService friendships,
                       ApplicationEventPublisher events, JdbcClient jdbc) {
        this.conversations = conversations;
        this.participants = participants;
        this.messages = messages;
        this.users = users;
        this.friendships = friendships;
        this.events = events;
        this.jdbc = jdbc;
    }

    /** Restituisce (creandola se serve) la conversazione 1 a 1 con un amico. */
    @Transactional
    public long openDirect(Long meId, String username) {
        User other = users.findByUsernameIgnoreCase(username)
            .filter(User::isEnabled)
            .orElseThrow(() -> new UserNotFoundException(username));
        if (other.getId().equals(meId)) {
            throw new NotAllowed("Non puoi aprire una chat con te stesso.");
        }
        if (!friendships.areFriends(meId, other.getId())) {
            throw new NotAllowed("Puoi chattare solo con i tuoi amici.");
        }
        String key = Conversation.directKey(meId, other.getId());
        conversations.insertDirectIfAbsent(key);
        Conversation c = conversations.findByDirectKey(key).orElseThrow();
        participants.insertIfAbsent(c.getId(), meId);
        participants.insertIfAbsent(c.getId(), other.getId());
        return c.getId();
    }

    /** Ultimi messaggi della conversazione; segna tutto come letto. */
    @Transactional
    public ConversationView view(Long meId, Long conversationId) {
        Participant me = requireParticipant(meId, conversationId);
        User other = otherParticipant(conversationId, meId);

        List<Message> latest = messages.findLatest(conversationId, PageRequest.of(0, PAGE_SIZE + 1));
        boolean hasOlder = latest.size() > PAGE_SIZE;
        List<MessageDto> page = toChronological(latest.subList(0, Math.min(PAGE_SIZE, latest.size())), conversationId);
        if (!page.isEmpty()) {
            me.markReadUpTo(page.getLast().id());
        }
        return new ConversationView(conversationId, other.getUsername(), other.getShownName(), other.getPhotoId(),
            canSend(me.getConversation(), meId, other), page, hasOlder);
    }

    /** Pagina di messaggi più vecchi di {@code beforeId}, in ordine cronologico. */
    @Transactional(readOnly = true)
    public List<MessageDto> older(Long meId, Long conversationId, Long beforeId) {
        requireParticipant(meId, conversationId);
        return toChronological(messages.findBefore(conversationId, beforeId, PageRequest.of(0, PAGE_SIZE)),
            conversationId);
    }

    @Transactional
    public MessageDto send(Long meId, Long conversationId, String rawBody) {
        String body = rawBody == null ? "" : rawBody.strip();
        if (body.isEmpty()) {
            throw new InvalidMessage("Il messaggio è vuoto.");
        }
        if (body.length() > Message.MAX_LENGTH) {
            throw new InvalidMessage("Il messaggio supera i " + Message.MAX_LENGTH + " caratteri.");
        }
        Participant me = requireParticipant(meId, conversationId);
        Conversation conversation = me.getConversation();
        List<Participant> all = participants.findWithUser(conversationId);
        User other = all.stream().map(Participant::getUser).filter(u -> !u.getId().equals(meId))
            .findFirst().orElseThrow();
        if (!canSend(conversation, meId, other)) {
            throw new NotAllowed("Non siete più amici: non puoi inviare nuovi messaggi.");
        }

        Message saved = messages.save(new Message(conversation, me.getUser(), body));
        conversation.touch(saved.getCreatedAt());
        me.markReadUpTo(saved.getId());

        MessageDto dto = MessageDto.from(saved, conversationId);
        List<String> recipients = all.stream().map(p -> p.getUser().getUsername()).toList();
        events.publishEvent(new MessageSentEvent(dto, recipients));
        return dto;
    }

    @Transactional
    public void markRead(Long meId, Long conversationId) {
        Participant me = requireParticipant(meId, conversationId);
        messages.findLastId(conversationId).ifPresent(me::markReadUpTo);
    }

    /** Conversazioni con almeno un messaggio, dalla più recente. */
    @Transactional(readOnly = true)
    public List<ConversationSummary> inbox(Long meId) {
        return jdbc.sql("""
                select c.id,
                       ou.username                              as other_username,
                       coalesce(ou.display_name, ou.username)   as other_name,
                       ou.photo_id                              as other_photo_id,
                       lm.body                                  as last_body,
                       (lm.sender_id = :me)                     as last_from_me,
                       c.last_message_at,
                       (select count(*) from messages m
                         where m.conversation_id = c.id and m.sender_id <> :me
                           and m.id > coalesce(p.last_read_message_id, 0)) as unread
                  from conversation_participants p
                  join conversations c on c.id = p.conversation_id
                  join conversation_participants op on op.conversation_id = c.id and op.user_id <> :me
                  join users ou on ou.id = op.user_id
                  join lateral (select m.body, m.sender_id from messages m
                                 where m.conversation_id = c.id order by m.id desc limit 1) lm on true
                 where p.user_id = :me and c.type = 'DIRECT'
                 order by c.last_message_at desc
                """)
            .param("me", meId)
            .query(ConversationSummary.class)
            .list();
    }

    /** Totale messaggi non letti (badge nella navbar). */
    @Transactional(readOnly = true)
    public long unreadTotal(Long meId) {
        return jdbc.sql("""
                select count(*) from messages m
                  join conversation_participants p on p.conversation_id = m.conversation_id and p.user_id = :me
                 where m.sender_id <> :me and m.id > coalesce(p.last_read_message_id, 0)
                """)
            .param("me", meId)
            .query(Long.class)
            .single();
    }

    // ---------------------------------------------------------------- helper

    private Participant requireParticipant(Long meId, Long conversationId) {
        return participants.findByConversationIdAndUserId(conversationId, meId)
            .orElseThrow(() -> new ConversationNotFound(conversationId));
    }

    private User otherParticipant(Long conversationId, Long meId) {
        return participants.findWithUser(conversationId).stream()
            .map(Participant::getUser)
            .filter(u -> !u.getId().equals(meId))
            .findFirst()
            .orElseThrow(() -> new ConversationNotFound(conversationId));
    }

    private boolean canSend(Conversation conversation, Long meId, User other) {
        return conversation.getType() != ConversationType.DIRECT
            || (other.isEnabled() && friendships.areFriends(meId, other.getId()));
    }

    private static List<MessageDto> toChronological(List<Message> newestFirst, Long conversationId) {
        List<MessageDto> result = new ArrayList<>(newestFirst.size());
        for (Message m : newestFirst) {
            result.add(MessageDto.from(m, conversationId));
        }
        Collections.reverse(result);
        return result;
    }
}
