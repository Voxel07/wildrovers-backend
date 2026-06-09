package tools;



import java.util.concurrent.CompletionStage;
import java.io.File;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
//Emailzeug

import io.quarkus.mailer.Mail;

import io.quarkus.mailer.Mailer;
import io.quarkus.mailer.reactive.ReactiveMailer;


import jakarta.enterprise.context.ApplicationScoped;

@Path("/email")
@ApplicationScoped
public class Email {

    //Blocking sync
    @Inject
    Mailer mailer;

    //Async
    @Inject
    ReactiveMailer reactiveMailer;

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "app.base-url", defaultValue = "http://localhost:8080")
    String baseUrl;

    public Response sendVerificationMail(String email, Long userId, String verificationId) {
        String base = baseUrl;
        if (!base.endsWith("/")) {
            base += "/";
        }
        String verificationUrl = base + "secrets/verify?verificationId=" + verificationId + "&userId=" + userId;
        
        String htmlBody = "<!DOCTYPE html>" +
            "<html>" +
            "<head>" +
            "  <meta charset=\"utf-8\">" +
            "  <style>" +
            "    .wrapper { padding: 40px 20px; background-color: #0d1117; font-family: sans-serif; text-align: center; }" +
            "    .card { max-width: 480px; margin: 0 auto; background-color: #1e1e1e; border: 1px solid #2a2a2a; border-radius: 12px; padding: 32px; box-shadow: 0 4px 20px rgba(0,0,0,0.4); text-align: center; }" +
            "    .header { font-size: 24px; font-weight: bold; color: #ff9800; margin-bottom: 20px; }" +
            "    .text { font-size: 16px; color: #b0bec5; line-height: 1.5; margin-bottom: 30px; }" +
            "    .btn { display: inline-block; background-color: #ff9800; color: #121212 !important; text-decoration: none; font-weight: bold; padding: 12px 24px; border-radius: 8px; font-size: 16px; }" +
            "    .footer { font-size: 12px; color: #666; margin-top: 30px; line-height: 1.4; }" +
            "  </style>" +
            "</head>" +
            "<body>" +
            "  <div class=\"wrapper\">" +
            "    <div class=\"card\">" +
            "      <div class=\"header\">Wild Rovers</div>" +
            "      <div class=\"text\">" +
            "        Hallo!<br><br>" +
            "        Vielen Dank für deine Registrierung bei den Wild Rovers. Bitte klicke auf den folgenden Button, um deine E-Mail-Adresse zu verifizieren und dein Konto zu aktivieren:" +
            "      </div>" +
            "      <a href=\"" + verificationUrl + "\" class=\"btn\">Konto verifizieren</a>" +
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

    @GET
    @Path("/async")
    public CompletionStage<Response> sendASimpleEmailAsync() {
        return reactiveMailer.send(
                Mail.withText("to@acme.org", "A reactive email from quarkus", "This is my body"))
                .subscribeAsCompletionStage()
                .thenApply(x -> Response.accepted().build());
    }

    @GET
    @Path("/attachment")
    public Response sendEmailWithAttachment() {
        mailer.send(Mail.withText("to@acme.org", "An email from quarkus with attachment",
                "This is my body")
                .addAttachment("my-file.txt",
                    "content of my file".getBytes(), "text/plain"));
        return Response.accepted().build();
    }

    @GET
    @Path("/html")
    public Response sendingHTML() {
        String body = "<strong>Hello!</strong>" + "\n" +
            "<p>Here is an image for you: <img src=\"cid:my-image@quarkus.io\"/></p>" +
            "<p>Regards</p>";
        mailer.send(Mail.withHtml("to@acme.org", "An email in HTML", body)
            .addInlineAttachment("quarkus-logo.png",
                new File("quarkus-logo.png"),
                "image/png", "<my-image@quarkus.io>"));
        return Response.accepted().build();
}

}
