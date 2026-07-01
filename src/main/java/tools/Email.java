package tools;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class Email {

    //Blocking sync
    @Inject
    Mailer mailer;

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "app.base-url", defaultValue = "http://localhost:8080")
    String baseUrl;

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "app.frontend-url", defaultValue = "http://localhost:5173")
    String appFrontendUrl;

    public Response sendVerificationMail(String email, String verificationCode) {
        String htmlBody = "<!DOCTYPE html>" +
            "<html>" +
            "<head>" +
            "  <meta charset=\"utf-8\">" +
            "  <style>" +
            "    .wrapper { padding: 40px 20px; background-color: #0d1117; font-family: sans-serif; text-align: center; }" +
            "    .card { max-width: 480px; margin: 0 auto; background-color: #1e1e1e; border: 1px solid #2a2a2a; border-radius: 12px; padding: 32px; box-shadow: 0 4px 20px rgba(0,0,0,0.4); text-align: center; }" +
            "    .header { font-size: 24px; font-weight: bold; color: #ff9800; margin-bottom: 20px; }" +
            "    .text { font-size: 16px; color: #b0bec5; line-height: 1.5; margin-bottom: 30px; }" +
            "    .code-box { display: inline-block; background-color: #2a2a2a; color: #ff9800; font-family: monospace; font-size: 32px; font-weight: bold; letter-spacing: 6px; padding: 12px 24px; border-radius: 8px; border: 1px solid #3a3a3a; margin-bottom: 30px; }" +
            "    .footer { font-size: 12px; color: #666; margin-top: 30px; line-height: 1.4; }" +
            "  </style>" +
            "</head>" +
            "<body>" +
            "  <div class=\"wrapper\">" +
            "    <div class=\"card\">" +
            "      <div class=\"header\">Wild Rovers</div>" +
            "      <div class=\"text\">" +
            "        Hallo!<br><br>" +
            "        Vielen Dank für deine Registrierung bei den Wild Rovers. Bitte gib den folgenden 6-stelligen Verifizierungscode auf der Registrierungsseite ein, um dein Konto zu verifizieren und zu aktivieren:" +
            "      </div>" +
            "      <div class=\"code-box\">" + verificationCode + "</div>" +
            "      <div class=\"footer\">" +
            "        Wenn du dieses Konto nicht erstellt hast, kannst du diese E-Mail einfach ignorieren.<br><br>" +
            "        Wild Rovers Team" +
            "      </div>" +
            "    </div>" +
            "  </div>" +
            "</body>" +
            "</html>";

        mailer.send(Mail.withHtml(email, "Wild Rovers - E-Mail verifizieren", htmlBody));
        return Response.accepted().build();
    }

    public Response sendPasswordResetMail(String email, String resetToken) {
        String resetLink = appFrontendUrl + "/password-reset?token=" + resetToken;

        String htmlBody = "<!DOCTYPE html>" +
            "<html>" +
            "<head>" +
            "  <meta charset=\"utf-8\">" +
            "  <style>" +
            "    .wrapper { padding: 40px 20px; background-color: #0d1117; font-family: sans-serif; text-align: center; }" +
            "    .card { max-width: 480px; margin: 0 auto; background-color: #1e1e1e; border: 1px solid #2a2a2a; border-radius: 12px; padding: 32px; box-shadow: 0 4px 20px rgba(0,0,0,0.4); text-align: center; }" +
            "    .header { font-size: 24px; font-weight: bold; color: #ff9800; margin-bottom: 20px; }" +
            "    .text { font-size: 16px; color: #b0bec5; line-height: 1.5; margin-bottom: 30px; text-align: center; }" +
            "    .btn-container { margin-bottom: 30px; text-align: center; }" +
            "    .btn { display: inline-block; background-color: #ff9800; color: #1e1e1e; font-weight: bold; font-size: 16px; padding: 12px 24px; border-radius: 8px; text-decoration: none; }" +
            "    .footer { font-size: 12px; color: #666; margin-top: 30px; line-height: 1.4; text-align: center; }" +
            "  </style>" +
            "</head>" +
            "<body>" +
            "  <div class=\"wrapper\">" +
            "    <div class=\"card\">" +
            "      <div class=\"header\">Wild Rovers</div>" +
            "      <div class=\"text\">" +
            "        Hallo!<br><br>" +
            "        Du hast eine Anfrage zum Zurücksetzen deines Passworts gestellt. Klicke auf den folgenden Button, um dein Passwort zurückzusetzen:" +
            "      </div>" +
            "      <div class=\"btn-container\">" +
            "        <a class=\"btn\" href=\"" + resetLink + "\">Passwort zurücksetzen</a>" +
            "      </div>" +
            "      <div class=\"footer\">" +
            "        Wenn du diese Anfrage nicht gestellt hast, kannst du diese E-Mail einfach ignorieren. Dein Passwort bleibt unverändert.<br><br>" +
            "        Wild Rovers Team" +
            "      </div>" +
            "    </div>" +
            "  </div>" +
            "</body>" +
            "</html>";

        mailer.send(Mail.withHtml(email, "Wild Rovers - Passwort zurücksetzen", htmlBody));
        return Response.accepted().build();
    }


}
