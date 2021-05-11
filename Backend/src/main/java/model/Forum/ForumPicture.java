package model.Forum;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Column;

@Entity
@Table(name = "FORUM_PICTURE")
public class ForumPicture {
   
    @Id
    @SequenceGenerator(name = "fPictureSeq", sequenceName = "ZSEQ_fPicture_ID",allocationSize = 1,initialValue = 1)
    @GeneratedValue(generator = "fPictureSeq")

    @Column(name = "id", unique = true)
    private Long id;

    @Column(name = "tile", unique = true)
    private String title;

    @Column(name = "creationDate")
    private String creationDate;

    @Column(name = "path")
    private String pathToPicture;

    //TODO:
    // @ManyToMany -> Post
    // @ManyToMany -> Answer
    
}
