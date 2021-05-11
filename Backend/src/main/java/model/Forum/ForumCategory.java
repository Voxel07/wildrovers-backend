package model.Forum;
// Datentypen
import java.util.List;
import java.util.ArrayList;
//Datenbank Gedöns
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.SequenceGenerator;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.CascadeType;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
//Models
import model.User;


//Security
@Entity
@Table(name = "FORUM_CATEGORY")
public class ForumCategory {

    @Id
    @SequenceGenerator(name = "fCategorySeq", sequenceName = "ZSEQ_fCategory_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "fCategorySeq")
    
    @Column(name = "id", unique = true)
    private Long id;

    @Column(name = "category", unique = true)
    private String category;

    @Column(name = "creationDate")
    private String creationDate;

    @Column(name = "topicCount")
    private Long topicCount;

    //relationships
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name ="user_id", referencedColumnName="id")
    private User creator;

    @OneToMany(mappedBy="category",cascade = {CascadeType.ALL},fetch=FetchType.LAZY)
    private List<ForumTopic> topics = new ArrayList<>();

    public ForumCategory(){}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ForumCategory(String category, String creationDate, User creator, List<ForumTopic> topics) {
        this.category = category;
        this.creationDate = creationDate;
        this.creator = creator;
        this.topics = topics;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(String creationDate) {
        this.creationDate = creationDate;
    }

    public User getCreator() {
        return creator;
    }

    public void setCreator(User creator) {
        this.creator = creator;
    }

    public List<ForumTopic> getTopics() {
        return topics;
    }

    public void setTopics(List<ForumTopic> topics) {
        this.topics = topics;
    }

    public void incrementPostCount() {
        this.topicCount ++;
    }

    public void decrementPostCount() {
        this.topicCount --;
    }
    
    public void setPostCount(Long topicCount){
        this.topicCount = topicCount;
    }
    
    
}
