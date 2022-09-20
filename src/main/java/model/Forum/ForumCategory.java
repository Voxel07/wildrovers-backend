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
import javax.json.bind.annotation.JsonbTransient;
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
    private Long creationDate;

    @Column(name = "topicCount", columnDefinition = "bigint default 0")
    private Long topicCount;

    @Column(name = "position", columnDefinition = "bigint default 0")
    private Long position;

    @Column(name = "visibility")
    private String visibility;

    @Column(name = "userName")
    private String userName;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    //relationships
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name ="user_id", referencedColumnName="id")
    private User creator;

    @OneToMany(mappedBy="category",cascade = {CascadeType.ALL},fetch=FetchType.LAZY)
    private List<ForumTopic> topics = new ArrayList<>();

    public ForumCategory(){}

    public ForumCategory(String category, Long creationDate, Long topicCount, Long position, String visibility, User creator,
            List<ForumTopic> topics) {
        this.category = category;
        this.creationDate = creationDate;
        this.topicCount = topicCount;
        this.position = position;
        this.visibility = visibility;
        this.creator = creator;
        this.topics = topics;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getPosition() {
        return position;
    }

    public void setPosition(long order) {
        this.position = order;
    }

    public Long getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(long creationDate) {
        this.creationDate = creationDate;
    }

    @JsonbTransient
    public User getCreator() {
        return creator;
    }

    public void setCreator(User creator) {
        this.creator = creator;
    }

    @JsonbTransient
    public List<ForumTopic> getTopics() {
        return topics;
    }

    public void setTopics(List<ForumTopic> topics) {
        this.topics = topics;
    }

    public void incTopicCount() {
        this.topicCount ++;
    }

    public void decTopicCount() {
        this.topicCount --;
    }

    public void setTopicCount(Long topicCount){
        this.topicCount = topicCount;
    }

    public Long getTopicCount() {
        return topicCount;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }


}
