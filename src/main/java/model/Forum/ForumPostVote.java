package model.Forum;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.json.bind.annotation.JsonbTransient;
import model.User;

@Entity
@Table(name = "FORUM_POST_VOTES")
public class ForumPostVote {

    @Id
    @SequenceGenerator(name = "forumPostVoteSeq", sequenceName = "ZSEQ_POST_VOTE_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "forumPostVoteSeq")
    @Column(name = "id", unique = true)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private ForumPost post;

    @Column(name = "vote_type")
    private String voteType; // "LIKE" or "DISLIKE"

    public ForumPostVote() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @JsonbTransient
    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @JsonbTransient
    public ForumPost getPost() {
        return post;
    }

    public void setPost(ForumPost post) {
        this.post = post;
    }

    public String getVoteType() {
        return voteType;
    }

    public void setVoteType(String voteType) {
        this.voteType = voteType;
    }
}
