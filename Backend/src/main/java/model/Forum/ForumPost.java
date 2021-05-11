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
    private ForumCategory topic;

    //User
    //Antworten
    //Kategorie
    //Likes
    //Bilder

    
}
