package model.Forum;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.json.bind.annotation.JsonbTransient;
import javax.persistence.Column;
import javax.persistence.FetchType;

@Entity
@Table(name = "FORUM_PICTURE")
public class ForumPicture {

    @Id
    @SequenceGenerator(name = "fPictureSeq", sequenceName = "ZSEQ_fPicture_ID",allocationSize = 1,initialValue = 1)
    @GeneratedValue(generator = "fPictureSeq")

    @Column(name = "id", unique = true)
    private Long id;

    @Column(name = "title", unique = true)
    private String title;

    @Column(name = "creationDate")
    private String creationDate;

    @Column(name = "path")
    private String pathToPicture;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", referencedColumnName = "id")
    private ForumAnswer answer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", referencedColumnName = "id")
    private ForumPost post;

    public ForumPicture(){}

    public ForumPicture(String pathToPicture) {
        this.pathToPicture = pathToPicture;
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

    public String getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(String creationDate) {
        this.creationDate = creationDate;
    }

    public String getPathToPicture() {
        return pathToPicture;
    }

    public void setPathToPicture(String pathToPicture) {
        this.pathToPicture = pathToPicture;
    }
    @JsonbTransient
    public ForumAnswer getAnswer() {
        return answer;
    }

    public void setAnswer(ForumAnswer answer) {
        this.answer = answer;
    }
    @JsonbTransient
    public ForumPost getPost() {
        return post;
    }

    public void setPost(ForumPost post) {
        this.post = post;
    }
}
