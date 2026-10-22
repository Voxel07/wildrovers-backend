package model.Forum;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
// Only changed columns are written, so edits never overwrite counters updated atomically in SQL.
@org.hibernate.annotations.DynamicUpdate
// Topic names are unique per category, not across the whole forum.
@Table(name = "FORUM_TOPIC", uniqueConstraints = @jakarta.persistence.UniqueConstraint(
        name = "UK_FORUM_TOPIC_CATEGORY_TOPIC", columnNames = { "category_id", "topic" }))
public class ForumTopic {

    @Id
    @SequenceGenerator(name = "fTopicSeq", sequenceName = "ZSEQ_fTopic_ID",allocationSize = 1,initialValue = 1)
    @GeneratedValue(generator = "fTopicSeq")

    @Column(name = "id", unique = true)
    private Long id;

    @NotBlank(message = "Topic is required")
    @Size(min = 3, max = 35, message = "Topic must be between 3 and 35 characters")
    @Column(name = "topic")
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

    /** Summary of the newest post, filled for topic lists so the UI needs no extra request per topic. */
    public record LatestPost(Long id, String title, String creator, Long creationDate) { }

    @jakarta.persistence.Transient
    private LatestPost latestPost;

    /** True when the current reader has viewed every post of the topic (null when not computed). */
    @jakarta.persistence.Transient
    private Boolean allViewed;

    public ForumTopic(){}

    public LatestPost getLatestPost() {
        return latestPost;
    }

    public void setLatestPost(LatestPost latestPost) {
        this.latestPost = latestPost;
    }

    public Boolean getAllViewed() {
        return allViewed;
    }

    public void setAllViewed(Boolean allViewed) {
        this.allViewed = allViewed;
    }

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
        return creator != null ? creator.getUserName() : "deleted";
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
