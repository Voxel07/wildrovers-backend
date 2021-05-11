package model.Forum;

import javax.persistence.Entity;
import javax.persistence.Table;

import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.SequenceGenerator;
import javax.persistence.GeneratedValue;

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

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="post_id", referencedColumnName="id")
    private ForumPost post;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name ="user_id", referencedColumnName="id")
    private User creator;

    //TODO: Relation to Picture
    
}
