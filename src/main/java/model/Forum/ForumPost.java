package model.Forum;

import model.User;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.CascadeType;
import com.fasterxml.jackson.annotation.JsonIgnore;


@Entity
@Table(name = "FORUM_POSTS")
public class ForumPost {
    @Id
    @SequenceGenerator(name = "forumPostSeq", sequenceName = "ZSEQ_fPost_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "forumPostSeq")

    @Column(name = "id", unique = true)
    private Long id;

    @Column(name = "title")
    private String title;

    @Column(name = "content",columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "creationDate")
    private Long creationDate;

    @Column(name = "editDate")
    private Long editDate;

    @Column(name = "views")
    private Long views;

    @Column(name = "likes", columnDefinition = "bigint default '0'")
    private Long likes;

    @Column(name = "dislikes", columnDefinition = "bigint default '0'")
    private Long dislikes;

    @Column(name = "answerCount", columnDefinition = "bigint default '0'")
    private Long answerCount;

    @ManyToOne(fetch=FetchType.EAGER)
    @JoinColumn(name ="user_id", referencedColumnName="id")
    private User creator;

    @JsonIgnore
    @JsonbTransient
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="topic_id", referencedColumnName="id")
    private ForumTopic topic;

    @JsonIgnore
    @JsonbTransient
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name = "editor_id",referencedColumnName = "id")
    private User editor;

    @JsonIgnore
    @JsonbTransient
    @OneToMany(mappedBy="post",cascade = {CascadeType.ALL},fetch = FetchType.LAZY )
    private List<ForumAnswer> answers = new ArrayList<>();

    @JsonIgnore
    @JsonbTransient
    @OneToMany(mappedBy ="post", cascade = {CascadeType.ALL}, fetch = FetchType.LAZY)
    private List<ForumPicture> pictures = new ArrayList<>();

    @OneToOne(mappedBy = "post", cascade = {CascadeType.ALL}, fetch = FetchType.EAGER)
    private model.Forum.Polls.Polls poll;

    @jakarta.persistence.Transient
    private boolean viewed;


    public ForumPost(){}

    public ForumPost(String title, String content, Long creationDate, Long editDate, Long likes,
            Long dislikes) {
        this.title = title;
        this.content = content;
        this.creationDate = creationDate;
        this.editDate = editDate;
        this.likes = likes;
        this.dislikes = dislikes;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Long creationDate) {
        this.creationDate = creationDate;
    }

    public Long getEditDate() {
        return editDate;
    }

    public void setEditDate(Long editDate) {
        this.editDate = editDate;
    }

    public Long getLikes() {
        return likes;
    }

    public void setLikes(Long likes) {
        this.likes = likes;
    }

    public Long getDislikes() {
        return dislikes;
    }

    public void setDislikes(Long dislikes) {
        this.dislikes = dislikes;
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
    public User getEditor() {
        return editor;
    }

    @JsonIgnore
    @JsonbTransient
    public void setEditor(User editor) {
        this.editor = editor;
    }
    @JsonIgnore
    @JsonbTransient
    public List<ForumAnswer> getAnswers() {
        return answers;
    }

    public void incAnswerCount(){
        this.answerCount ++;
    }
    public void decAnswerCount(){
        this.answerCount --;
    }

    @JsonIgnore
    @JsonbTransient
    public void setAnswers(List<ForumAnswer> answers) {
        this.answers = answers;
    }
    @JsonIgnore
    @JsonbTransient
    public ForumTopic getTopic() {
        return topic;
    }

    @JsonIgnore
    @JsonbTransient
    public void setTopic(ForumTopic topic) {
        this.topic = topic;
    }

    public boolean isViewed() {
        return viewed;
    }

    public void setViewed(boolean viewed) {
        this.viewed = viewed;
    }

    public Long getTopicId() {
        return topic != null ? topic.getId() : null;
    }

    public String getTopicName() {
        return topic != null ? topic.getTopic() : null;
    }

    public Long getCategoryId() {
        return (topic != null && topic.getCategory() != null) ? topic.getCategory().getId() : null;
    }

    public String getCategoryName() {
        return (topic != null && topic.getCategory() != null) ? topic.getCategory().getCategory() : null;
    }

    public Long getAnswerCount() {
        return answerCount;
    }

    public void setAnswerCount(Long answerCount) {
        this.answerCount = answerCount;
    }
    @JsonIgnore
    @JsonbTransient
    public List<ForumPicture> getPictures() {
        return pictures;
    }

    @JsonIgnore
    @JsonbTransient
    public void setPictures(List<ForumPicture> pictures) {
        this.pictures = pictures;
    }

    public void setPicture(ForumPicture picture) {
        this.pictures.add(picture);
    }


    public Long getViews() {
        return views;
    }

    public void setViews(Long views) {
        this.views = views;
    }

    public model.Forum.Polls.Polls getPoll() {
        return poll;
    }

    public void setPoll(model.Forum.Polls.Polls poll) {
        this.poll = poll;
    }
}
