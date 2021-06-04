package model.Forum;

import model.User;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.OneToMany;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.json.bind.annotation.JsonbTransient;
import javax.persistence.CascadeType;


@Entity
@Table(name = "FORUM_POSTS")
public class ForumPost {
    @Id
    @SequenceGenerator(name = "forumPostSeq", sequenceName = "ZSEQ_fPost_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "forumPost")

    @Column(name = "id", unique = true)
    private Long id;

    @Column(name = "title")
    private String title;

    @Column(name = "content")
    private String content;

    @Column(name = "creationDate")
    private String creationDate;

    @Column(name = "editDate")
    private String editDate;

    @Column(name = "views")
    private Long views;

    @Column(name = "likes", columnDefinition = "bigint default '0'")
    private Long likes;

    @Column(name = "dislikes", columnDefinition = "bigint default '0'")
    private Long dislikes;

    @Column(name = "answerCount", columnDefinition = "bigint default '0'")
    private Long answerCount;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name ="user_id", referencedColumnName="id")
    private User creator;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="topic_id", referencedColumnName="id")
    private ForumTopic topic;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "editor_id",referencedColumnName = "id")
    private User editor;

    @OneToMany(mappedBy="post",cascade = {CascadeType.ALL},fetch=FetchType.LAZY )
    private List<ForumAnswer> answers = new ArrayList<>();   

    @OneToMany(mappedBy ="post", cascade = {CascadeType.ALL}, fetch = FetchType.LAZY)
    private List<ForumPicture> pictures = new ArrayList<>();

    public ForumPost(){}

    public ForumPost(String title, String content, String creationDate, String editDate, Long likes,
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

    public String getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(String creationDate) {
        this.creationDate = creationDate;
    }

    public String getEditDate() {
        return editDate;
    }

    public void setEditDate(String editDate) {
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
    @JsonbTransient
    public User getCreator() {
        return creator;
    }

    public void setCreator(User creator) {
        this.creator = creator;
    }
    @JsonbTransient
    public User getEditor() {
        return editor;
    }

    public void setEditor(User editor) {
        this.editor = editor;
    }
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
    
    public void setAnswers(List<ForumAnswer> answers) {
        this.answers = answers;
    }
    @JsonbTransient
    public ForumTopic getTopic() {
        return topic;
    }

    public void setTopic(ForumTopic topic) {
        this.topic = topic;
    }    

    public Long getAnswerCount() {
        return answerCount;
    }

    public void setAnswerCount(Long answerCount) {
        this.answerCount = answerCount;
    }
    @JsonbTransient
    public List<ForumPicture> getPictures() {
        return pictures;
    }

    public void setPictures(List<ForumPicture> pictures) {
        this.pictures = pictures;
    }

    public Long getViews() {
        return views;
    }

    public void setViews(Long views) {
        this.views = views;
    }
}
