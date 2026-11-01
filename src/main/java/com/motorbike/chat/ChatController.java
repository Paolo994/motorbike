package com.motorbike.chat;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.motorbike.auth.AppUserDetails;
import com.motorbike.chat.ChatExceptions.InvalidMessage;
import com.motorbike.chat.ChatExceptions.NotAllowed;
import com.motorbike.friend.FriendshipService;
import com.motorbike.friend.UserCard;

@Controller
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chat;
    private final FriendshipService friendships;

    public ChatController(ChatService chat, FriendshipService friendships) {
        this.chat = chat;
        this.friendships = friendships;
    }

    // ---------------------------------------------------------------- pagine

    /**
     * Lista conversazioni con ricerca: {@code q} filtra le conversazioni per username/nome e propone
     * gli amici corrispondenti con cui non c'è ancora una conversazione.
     */
    @GetMapping
    public String inbox(@AuthenticationPrincipal AppUserDetails me,
                        @RequestParam(name = "q", required = false) String q, Model model) {
        String query = q == null ? "" : q.strip();
        List<ConversationSummary> conversations = chat.inbox(me.getId());
        List<UserCard> friends = friendships.overview(me.getId()).friends();
        List<UserCard> friendMatches = List.of();

        if (!query.isEmpty()) {
            String lower = query.toLowerCase(Locale.ROOT);
            conversations = conversations.stream().filter(c -> c.matches(lower)).toList();
            Set<String> withConversation = conversations.stream()
                .map(ConversationSummary::otherUsername).collect(Collectors.toSet());
            friendMatches = friends.stream()
                .filter(f -> !withConversation.contains(f.username()))
                .filter(f -> f.username().toLowerCase(Locale.ROOT).contains(lower)
                    || f.shownName().toLowerCase(Locale.ROOT).contains(lower))
                .toList();
        }
        model.addAttribute("q", query);
        model.addAttribute("conversations", conversations);
        model.addAttribute("friends", friends);
        model.addAttribute("friendMatches", friendMatches);
        return "chat/index";
    }

    @GetMapping("/with/{username}")
    public String openWith(@AuthenticationPrincipal AppUserDetails me, @PathVariable String username,
                           RedirectAttributes redirect) {
        try {
            return "redirect:/chat/" + chat.openDirect(me.getId(), username);
        } catch (NotAllowed e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/u/" + username;
        }
    }

    @GetMapping("/{id}")
    public String conversation(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id, Model model) {
        model.addAttribute("view", chat.view(me.getId(), id));
        // il badge della navbar (GlobalModelAdvice) è calcolato prima che view() segni i messaggi come letti
        model.addAttribute("unreadMessages", chat.unreadTotal(me.getId()));
        model.addAttribute("maxLength", Message.MAX_LENGTH);
        model.addAttribute("pageSize", ChatService.PAGE_SIZE);
        return "chat/conversation";
    }

    /** Invio senza JavaScript (fallback del form). */
    @PostMapping("/{id}/messages")
    public String sendForm(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                           @RequestParam(defaultValue = "") String body, RedirectAttributes redirect) {
        try {
            chat.send(me.getId(), id, body);
        } catch (NotAllowed | InvalidMessage e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/chat/" + id;
    }

    // ---------------------------------------------------------------- JSON (usato da conversation.js)

    @PostMapping(path = "/{id}/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto sendJson(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                               @RequestParam(defaultValue = "") String body) {
        return chat.send(me.getId(), id, body);
    }

    @GetMapping(path = "/{id}/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<MessageDto> older(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                                  @RequestParam Long before) {
        return chat.older(me.getId(), id, before);
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id) {
        chat.markRead(me.getId(), id);
    }

    @ExceptionHandler({NotAllowed.class, InvalidMessage.class})
    public ResponseEntity<Map<String, String>> jsonError(RuntimeException e) {
        HttpStatus status = e instanceof NotAllowed ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("error", e.getMessage()));
    }
}
