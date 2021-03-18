package model.Forum;

import model.User;
import model.Forum.ForumAnswers;
import model.Forum.ForumCategory;

import java.time.LocalDate;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.OneToMany;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.CascadeType;


@Entity
@Table(name = "ForumPost")
public class ForumPost {
    @Id
    @SequenceGenerator(name = "forumPostSeq", sequenceName = "ZSEQ_FORUNPOST_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "forumPost")

    @Column(name = "id", unique = true)
    private Long id;

    @Column(name = "titel")
    private String title;

    @Column(name = "content")
    private String content;

    @Column(name = "crateDate")
    private LocalDate createDate;

    @Column(name = "editDate")
    private LocalDate editDate;

    @OneToOne(cascade = CascadeType.ALL)
    @Column(name = "creator")
    private User creator;

    @OneToOne(cascade = CascadeType.ALL)
    @Column(name = "editedBy")
    private User editor;

    @OneToMany()
    @Column(name = "answers")
    private ForumAnswers answers;

    @ManyToOne
    @Column(name="category")
    private ForumCategory category;

    //User
    //Antworten
    //Kategorie
    //Likes
    //Bilder

    
}
