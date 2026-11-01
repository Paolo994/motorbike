package com.motorbike.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return users.findById(id).orElseThrow(() -> new UserNotFoundException(String.valueOf(id)));
    }

    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return users.findByUsernameIgnoreCase(username)
            .filter(User::isEnabled)
            .orElseThrow(() -> new UserNotFoundException(username));
    }

    @Transactional
    public void updateProfile(Long userId, ProfileForm form) {
        User user = getById(userId);
        user.setDisplayName(blankToNull(form.getDisplayName()));
        user.setBio(blankToNull(form.getBio()));
        user.setCity(blankToNull(form.getCity()));
        user.setBikeModel(blankToNull(form.getBikeModel()));
        user.setFuelConsumptionKmPerL(form.getFuelConsumptionKmPerL());
        user.setTankCapacityL(form.getTankCapacityL());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
