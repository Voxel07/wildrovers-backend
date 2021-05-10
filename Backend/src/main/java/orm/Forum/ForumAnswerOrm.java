package orm.Forum;
//
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
//Logging
import java.util.logging.Logger;
//Time
import java.time.format.DateTimeFormatter;  
import java.time.LocalDateTime;    


public class ForumAnswerOrm {
    private static final Logger log = Logger.getLogger(ForumAnswerOrm.class.getName());
    @Inject
    EntityManager em; 
    @Inject
    ForumAnswerOrm forumAnswerOrm; 
}
