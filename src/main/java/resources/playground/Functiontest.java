package resources.playground;
import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.print.attribute.standard.Media;

//Logging
import java.util.logging.Logger;
import java.util.HashMap;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import orm.Secrets.SecretOrm;
import resources.JWT;

import javax.ws.rs.core.NewCookie;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import io.vertx.core.http.HttpServerRequest;

@Path("/tests")

@ApplicationScoped
public class Functiontest {

        private static final Logger log = Logger.getLogger(Functiontest.class.getName());

        private static HashMap<String,Long> blockList = new HashMap<String,Long>();

        private static Long count = 0L;

        private static String IP;

        private static String getIp() {
            return IP;
        }

        private static void setIp(String newIp){
            if (!newIp.equals(getIp()))
            {
                IP=newIp;
                rstCnt();
            }
            incCount();
        }

        private static void updateList(String newIp)
        {
            Long tries  = blockList.get(newIp);

            if(tries == null){
                blockList.put(newIp, 0L);
            }
            else{
                blockList.put(newIp, blockList.get(newIp)+1);
            }
        }
        private static Long getValFromList(String key)
        {
            Long val = blockList.get(key);
            if (val == null) return 0L;
            return val;
        }

        private static void rstCnt(){
            count=0L;
        }
        private static void incCount(){
            count += 1;
        }

        private static Long getCount(){
            return count;
        }

        @Inject
        SecretOrm secretOrm;

        @Context
        HttpServerRequest request;

        @Context
        UriInfo info;


        @GET
        @Path("/uuid")
        @Produces(MediaType.APPLICATION_JSON)
        @Consumes(MediaType.APPLICATION_JSON)
        public String testUUID(){
            log.info("Functiontes/testUUID");
            return secretOrm.generateVerificationId();
        }

        @GET
        @Path("/response")
        @Produces(MediaType.APPLICATION_JSON)
        public Response testResponse()
        {

            JsonObject object = Json.createObjectBuilder()
            .add("JWT", 123)
            .add("USER", Json.createObjectBuilder()
                .add("User", "Camo")
                .add("Rolle", "Admin").build()
                )
            .add("Auth", "ja")
            .build();

            NewCookie klaus = new NewCookie("rolf", "4567", "/","localhost","test",3600,true,true);

            return Response.status(200).entity(object)
            .cookie(klaus)
            .build();
        }

        @GET
        @Path("/remove")
        @Produces(MediaType.APPLICATION_JSON)
        public Response removeCookie()
        {
            NewCookie klaus = new NewCookie("rolf", "deleted", "/","localhost","test",0,true,true);

            return Response.status(200).entity("object").header("Set-Cookie", klaus).build();
        }

        @GET
        @Path("/count")
        @Produces(MediaType.TEXT_PLAIN)
        public Response statisch()
        {
            // incCount();

            // JsonObject object = Json.createObjectBuilder()
            // .add("count", getCount())
            // .add("HttpServerRequest", Json.createObjectBuilder()
            //     .add("host", request.host())
            //     .add("cookieCount", request.cookieCount())
            //     .add("remoteAddress", request.remoteAddress().toString())
            //     .add("localAddress", request.localAddress().toString())
            //     .add("Rolle", "Admin")
            //     .add("HttpServerRequest.response", Json.createObjectBuilder()
            //         .add("getStatusCode", request.response().getStatusCode())
            //         .add("getStatusMessage", request.response().getStatusMessage())
            //         .add("getClass", request.response().getClass().toString())
            //         .build())
            //     .add("HttpServerRequest.connection", Json.createObjectBuilder()
            //     .add("getWindowSize", request.connection().getWindowSize())
            //     .add("isSsl", request.connection().isSsl())
            //     .add("getClass", request.connection().sslSession().getSessionContext().getSessionCacheSize())
            //     .build())
            //     .build())
            // .add("Auth", "ja")
            // .build();
            // log.info("Info: " + info.getQueryParameters() + "|" + info.getBaseUri() + "|" + info.getPath() + "|" + info.getAbsolutePath());
            // return Response.status(200).entity(object).build();

            setIp(request.remoteAddress().toString());
            Response res;
            log.info(blockList.toString());

            Long tries = getValFromList(request.remoteAddress().toString());

            if(tries > 10){
            updateList(request.remoteAddress().toString());

                res = Response.status(401).entity("why"+tries).build();
            }
            else{
            updateList(request.remoteAddress().toString());

                res = Response.status(200).entity("ok"+tries).build();
            }

            return res;
        }
}


