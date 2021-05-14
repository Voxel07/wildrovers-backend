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

    @Column(name = "crateDate")
    private LocalDate createDate;

    @Column(name = "editDate")
    private LocalDate editDate;

    @Column(name = "likes")
    private Long likes;

    @Column(name = "dislikes")
    private Long dislikes;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name ="user_id", referencedColumnName="id")
    private User creator;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "editor_id",referencedColumnName = "id")
    private User editor;

    @OneToMany(mappedBy="post",cascade = {CascadeType.ALL},fetch=FetchType.LAZY )
    private List<ForumAnswer> answers = new ArrayList<>();

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="topic_id", referencedColumnName="id")
    private ForumTopic topic;

    @OneToMany(mappedBy ="post", cascade = {CascadeType.ALL}, fetch = FetchType.LAZY)
    private List<ForumPicture> pictures = new ArrayList<>();

    public ForumPost(){}

    public ForumPost(String title, String content, LocalDate createDate, LocalDate editDate, Long likes,
            Long dislikes) {
        this.title = title;
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

    public User getCreator() {
        return creator;
    }

    public void setCreator(User creator) {
        this.creator = creator;
    }

    public User getEditor() {
        return editor;
    }

    public void setEditor(User editor) {
        this.editor = editor;
    }

    public List<ForumAnswer> getAnswers() {
        return answers;
    }

    public void setAnswers(List<ForumAnswer> answers) {
        this.answers = answers;
    }

    public ForumTopic getTopic() {
        return topic;
    }

    public void setTopic(ForumTopic topic) {
        this.topic = topic;
    }    
}
