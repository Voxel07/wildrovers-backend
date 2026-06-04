package model.Forum;

import java.util.List;
import java.util.ArrayList;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.GeneratedValue;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.CascadeType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;

import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import model.User;

@Entity
@Table(name = "FORUM_ANSWERS")
public class ForumAnswer {

    @Id
    @SequenceGenerator(name = "fAnswerSeq", sequenceName = "ZSEQ_fAnswer_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "fAnswerSeq")

    @Column(name = "id", unique = true)
    private Long id;

    @Column(name = "content")
    private String content;

    @Column(name = "creationDate")
    private Long creationDate;

    @Column(name = "editDate")
    private String editDate;

    @Column(name = "likes", columnDefinition = "bigint default '0'")
    private Long likes;

    @Column(name = "dislikes", columnDefinition = "bigint default '0'")
    private Long dislikes;

    @JsonIgnore
    @JsonbTransient
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="post_id", referencedColumnName="id")
    private ForumPost post;

    @ManyToOne(fetch=FetchType.EAGER)
    @JoinColumn(name ="user_id", referencedColumnName="id")
    private User creator;

    @JsonIgnore
    @JsonbTransient
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name = "editor_id",referencedColumnName = "id")
    private User editor;

    @JsonIgnore
    @JsonbTransient
    @OneToMany(mappedBy ="answer", cascade = {CascadeType.ALL}, fetch = FetchType.LAZY)
    private List<ForumPicture> pictures = new ArrayList<>();

    public ForumAnswer(){}

    public ForumAnswer(String content, Long creationDate, String editDate, Long likes, Long dislikes) {
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(long creationDate) {
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
    @JsonIgnore
    @JsonbTransient
    public ForumPost getPost() {
        return post;
    }

    @JsonIgnore
    @JsonbTransient
    public void setPost(ForumPost post) {
        this.post = post;
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
    public List<ForumPicture> getPictures() {
        return pictures;
    }

    @JsonIgnore
    @JsonbTransient
    public void setPictures(List<ForumPicture> pictures) {
        this.pictures = pictures;
    }

}
