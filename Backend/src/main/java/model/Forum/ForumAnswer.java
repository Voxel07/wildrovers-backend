package model.Forum;

import java.util.List;
import java.util.ArrayList;

import java.time.LocalDate;

import javax.persistence.Entity;
import javax.persistence.Table;

import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.SequenceGenerator;
import javax.persistence.GeneratedValue;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.FetchType;

import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

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

    @Column(name = "crateDate")
    private LocalDate createDate;

    @Column(name = "editDate")
    private LocalDate editDate;

    @Column(name = "likes")
    private Long likes;

    @Column(name = "dislikes")
    private Long dislikes;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="post_id", referencedColumnName="id")
    private ForumPost post;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name ="user_id", referencedColumnName="id")
    private User creator;

    @OneToMany(mappedBy ="answer", cascade = {CascadeType.ALL}, fetch = FetchType.LAZY)
    private List<ForumPicture> pictures = new ArrayList<>();

    public ForumAnswer(){}

    public ForumAnswer(String content, LocalDate createDate, LocalDate editDate, Long likes, Long dislikes) {
        this.content = content;
        this.createDate = createDate;
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

    public LocalDate getCreateDate() {
        return createDate;
    }

    public void setCreateDate(LocalDate createDate) {
        this.createDate = createDate;
    }

    public LocalDate getEditDate() {
        return editDate;
    }

    public void setEditDate(LocalDate editDate) {
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

    public ForumPost getPost() {
        return post;
    }

    public void setPost(ForumPost post) {
        this.post = post;
    }

    public User getCreator() {
        return creator;
    }

    public void setCreator(User creator) {
        this.creator = creator;
    }

    public List<ForumPicture> getPictures() {
        return pictures;
    }

    public void setPictures(List<ForumPicture> pictures) {
        this.pictures = pictures;
    }
    
}