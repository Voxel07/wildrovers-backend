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
 
    public Response sendVerificationMail(String email,Long userId, String verificationId) {
            mailer.send(Mail.withText(email, "WildRovers anmeldung", "http://localhost:8080/secrets/verify?verificationId="+verificationId+"&userId="+userId));
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
