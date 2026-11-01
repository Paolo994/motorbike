package com.motorbike.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegistrationForm {

    @NotBlank(message = "Lo username è obbligatorio")
    @Pattern(regexp = "^[A-Za-z0-9_.]{3,30}$",
        message = "Da 3 a 30 caratteri: lettere, numeri, punto e underscore")
    private String username;

    @NotBlank(message = "L'email è obbligatoria")
    @Email(message = "Email non valida")
    @Size(max = 254)
    private String email;

    // BCrypt considera solo i primi 72 byte
    @NotBlank(message = "La password è obbligatoria")
    @Size(min = 8, max = 72, message = "La password deve avere da 8 a 72 caratteri")
    private String password;

    private String confirmPassword;

    @AssertTrue(message = "Le password non coincidono")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
