package model.Forum;
// Datentypen
import java.util.List;
import java.util.ArrayList;
//Datenbank Gedöns
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.validation.constraints.Digits;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

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

// helper
import helper.Deserialization;
import javax.json.bind.annotation.JsonbTypeDeserializer;
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

    @NotBlank(message = "Category is required")
    @Size(min = 4, max = 100, message = "Category must be between 4 and 100 characters")
    @Column(name = "category", unique = true)
    private String category;

    @Column(name = "creationDate")
    private Long creationDate;

    @Column(name = "topicCount", columnDefinition = "bigint default 0")
    private Long topicCount;

    @JsonbTypeDeserializer(Deserialization.class)
    @Digits(integer = 10, fraction = 0, message = "Position muss eine Nummer sein")
    @Min(value = 0, message = "Position must be at least 0")
    @Column(name = "position", columnDefinition = "bigint default 0")
    private Long position;

    @Column(name = "visibility")
    private String visibility;

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
    public User getCreatorObj(){
        return creator;
    }

    public String getCreator() {
        return creator.getUserName();
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
