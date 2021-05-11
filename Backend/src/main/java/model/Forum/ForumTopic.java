package model.Forum;

import java.util.ArrayList;
import java.util.List;
import javax.persistence.FetchType;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.JoinColumn;

import model.User;

@Entity
@Table(name = "FORUM_TOPIC")
public class ForumTopic {

    @Id
    @SequenceGenerator(name = "fTopicSeq", sequenceName = "ZSEQ_fTopic_ID",allocationSize = 1,initialValue = 1)
    @GeneratedValue(generator = "fTopicSeq")

    @Column(name = "id", unique = true)
    private Long id;

    @Column(name = "topic", unique = true)
    private String topic;

    @Column(name = "creationDate")
    private String creationDate;

    @Column(name = "postCount")
    private Long postCount;

    //relationships
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name ="user_id", referencedColumnName="id")
    private User creator;
   
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name ="category_id", referencedColumnName="id")
    private ForumCategory category;

    @OneToMany(mappedBy = "topic",cascade = {CascadeType.ALL},fetch = FetchType.LAZY)
    private List<ForumPost> posts = new ArrayList<>();

    public ForumTopic(){}
    
    public ForumTopic(String topic, String creationDate, User creator, ForumCategory category, List<ForumPost> posts) {
        this.topic = topic;
        this.creationDate = creationDate;
        this.creator = creator;
        this.category = category;
        this.posts = posts;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
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

    public ForumCategory getCategory() {
        return category;
    }

    public void setCategory(ForumCategory category) {
        this.category = category;
    }

    public List<ForumPost> getPosts() {
        return posts;
    }

    public void setPosts(List<ForumPost> posts) {
        this.posts = posts;
    }

    public void incrementPostCount() {
        this.postCount ++;
    }

    public void decrementPostCount() {
        this.postCount --;
    }
    
    public void setPostCount(Long postCount){
        this.postCount = postCount;
    }
    
}
