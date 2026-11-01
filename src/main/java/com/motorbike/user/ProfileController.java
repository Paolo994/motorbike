package com.motorbike.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.motorbike.auth.AppUserDetails;
import com.motorbike.friend.FriendshipService;
import com.motorbike.photo.PhotoService;

import jakarta.validation.Valid;

@Controller
public class ProfileController {

    private final UserService userService;
    private final FriendshipService friendshipService;
    private final PhotoService photoService;

    public ProfileController(UserService userService, FriendshipService friendshipService,
                             PhotoService photoService) {
        this.userService = userService;
        this.friendshipService = friendshipService;
        this.photoService = photoService;
    }

    @GetMapping("/profile")
    public String myProfile(@AuthenticationPrincipal AppUserDetails me) {
        return "redirect:/u/" + me.getUsername();
    }

    @GetMapping("/u/{username}")
    public String view(@PathVariable String username, @AuthenticationPrincipal AppUserDetails me, Model model) {
        User user = userService.getByUsername(username);
        model.addAttribute("profile", user);
        model.addAttribute("isMe", user.getId().equals(me.getId()));
        model.addAttribute("relation", friendshipService.relation(me.getId(), user.getId()));
        return "profile/view";
    }

    @GetMapping("/profile/edit")
    public String editForm(@AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("form", ProfileForm.from(userService.getById(me.getId())));
        addPhotoState(me, model);
        return "profile/edit";
    }

    @PostMapping("/profile/edit")
    public String save(@AuthenticationPrincipal AppUserDetails me,
                       @Valid @ModelAttribute("form") ProfileForm form, BindingResult binding,
                       RedirectAttributes redirect, Model model) {
        if (binding.hasErrors()) {
            addPhotoState(me, model);
            return "profile/edit";
        }
        userService.updateProfile(me.getId(), form);
        redirect.addFlashAttribute("success", "Profilo aggiornato.");
        return "redirect:/u/" + me.getUsername();
    }

    private void addPhotoState(AppUserDetails me, Model model) {
        model.addAttribute("currentPhotoId", userService.getById(me.getId()).getPhotoId());
        model.addAttribute("photoInReview", photoService.latestNotApproved(me.getId()).orElse(null));
    }
}
