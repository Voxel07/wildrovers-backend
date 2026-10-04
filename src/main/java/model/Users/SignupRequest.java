package model.Users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Self-registration payload. Only these fields are accepted from visitors;
 * role, permissions and account status are always set by the server.
 */
public class SignupRequest {

    @NotBlank(message = "Die E-Mail muss gesetzt sein.")
    @Email(message = "Die E-Mail ist ungültig.")
    @Size(max = 254)
    public String email;

    @NotBlank(message = "Der Benutzername muss gesetzt sein.")
    @Size(max = 50)
    public String userName;

    @NotBlank(message = "Das Passwort muss gesetzt sein.")
    @Size(min = 8, max = 256)
    public String password;

    @NotBlank(message = "Der Vorname muss gesetzt sein.")
    @Size(max = 100)
    public String firstName;

    @NotBlank(message = "Der Nachname muss gesetzt sein.")
    @Size(max = 100)
    public String lastName;
}
