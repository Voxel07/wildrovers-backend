package model.Forum;
// Datentypen
import java.util.List;
import java.util.ArrayList;
//Datenbank Gedöns
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.CascadeType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

// helper
import helper.Deserialization;
import jakarta.json.bind.annotation.JsonbTypeDeserializer;
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
    @Size(min = 4, max = 35, message = "Category must be between 4 and 35 characters")
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
    @ManyToOne(fetch=FetchType.EAGER)
    @JoinColumn(name ="user_id", referencedColumnName="id")
    private User creator;

    @JsonIgnore
    @JsonbTransient
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

    public void setPosition(Long order) {
        this.position = order;
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
    public List<ForumTopic> getTopics() {
        return topics;
    }

    @JsonIgnore
    @JsonbTransient
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
