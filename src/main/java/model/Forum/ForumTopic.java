package model.Forum;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.FetchType;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.CascadeType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.JoinColumn;

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
    private Long creationDate;

    @Column(name = "postCount", columnDefinition = "bigint default 0")
    private Long postCount;

    @Column(name = "views")
    private Long views;

    //relationships
    @ManyToOne(fetch=FetchType.EAGER)
    @JoinColumn(name ="user_id", referencedColumnName="id")
    private User creator;

    @JsonIgnore
    @JsonbTransient
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name ="category_id", referencedColumnName="id")
    private ForumCategory category;

    @JsonIgnore
    @JsonbTransient
    @OneToMany(mappedBy = "topic",cascade = {CascadeType.ALL},fetch = FetchType.LAZY)
    private List<ForumPost> posts = new ArrayList<>();

    public ForumTopic(){}

    public ForumTopic(String topic, Long creationDate, User creator, ForumCategory category) {
        this.topic = topic;
        this.creationDate = creationDate;
        this.creator = creator;
        this.category = category;
    }

    public Long getPostCount() {
        return postCount;
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

    public Long getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(long creationDate) {
        this.creationDate = creationDate;
    }
    @JsonIgnore
    @JsonbTransient
    public User getCreatorObj(){
        return creator;
    }
    public String getCreator() {
        return creator.getUserName();
    }

    public void setCreator(User creator) {
        this.creator = creator;
    }
    @JsonIgnore
    @JsonbTransient
    public ForumCategory getCategory() {
        return category;
    }

    @JsonIgnore
    @JsonbTransient
    public void setCategory(ForumCategory category) {
        this.category = category;
    }

    public Long getCategoryId() {
        return category != null ? category.getId() : null;
    }

    public String getCategoryName() {
        return category != null ? category.getCategory() : null;
    }
    @JsonIgnore
    @JsonbTransient
    public List<ForumPost> getPosts() {
        return posts;
    }

    @JsonIgnore
    @JsonbTransient
    public void setPosts(List<ForumPost> posts) {
        this.posts = posts;
    }

    public void incPostCount() {
        this.postCount ++;
    }

    public void decPostCount() {
        this.postCount --;
    }

    public void setPostCount(Long postCount){
        this.postCount = postCount;
    }

    public Long getViews() {
        return views;
    }

    public void setViews(Long views) {
        this.views = views;
    }
}
