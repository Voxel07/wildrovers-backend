package resources;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import model.*;
import orm.*;
import javax.ws.rs.QueryParam;

@Path("/user")
public class UserResouce {
    @ApplicationScoped
    @Inject
    UserOrm userOrm;


@GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public List<User> getUser(	@QueryParam("userId") Long userId,			
                                @QueryParam("username") String userName)

    {   
    	System.out.println("UserResource/getUser");
        if(userId != null){
        	System.out.println("getUserById");
          return userOrm.getUserById(userId);
        } 
        else if(userName!=null){
        	System.out.println("getUserByUsername");
            return userOrm.getUserByUsername(userName);
        }
        else{  
        	System.out.println("getUsers");
            return  userOrm.getUsers();
        }
    }

    // @PUT
    // @Path("{companyId}")
    // @Produces(MediaType.APPLICATION_JSON)
    // @Consumes(MediaType.APPLICATION_JSON)
    // public String updateUser(User usr,@PathParam("companyId") Long companyId) 
    // { 	
    // 	System.out.println("UserResource/updateUser");
    //     return userOrm.updateUser(usr,companyId);
    // }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String addUser(User usr) 
    {
    	System.out.println("UserResource/addUser");
        return userOrm.addUser(usr);
    }
}
