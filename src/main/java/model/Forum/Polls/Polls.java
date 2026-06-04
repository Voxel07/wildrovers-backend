package model.Forum.Polls;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Column;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import jakarta.persistence.SequenceGenerator;
import jakarta.json.bind.annotation.JsonbTransient;
import model.Forum.ForumPost;
import model.User;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "FORUM_POLLS")
public class Polls {
    @Id
    @SequenceGenerator(name = "forumPollSeq", sequenceName = "ZSEQ_fPoll_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "forumPollSeq")
    @Column(name = "id", unique = true)
    private Long id;

    @Column(name = "question")
    private String question;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    @JsonbTransient
    private ForumPost post;

    @OneToMany(mappedBy = "poll", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private List<PollOptions> options = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "FORUM_POLL_VOTES", joinColumns = @JoinColumn(name = "poll_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    @JsonbTransient
    private List<User> votedUsers = new ArrayList<>();

    @Column(name = "allow_multiple", columnDefinition = "boolean default false")
    private Boolean allowMultiple = false;

    public Polls() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getQuestion() {
        return question;
    }

    public Boolean getAllowMultiple() {
        return allowMultiple;
    }

    public void setAllowMultiple(Boolean allowMultiple) {
        this.allowMultiple = allowMultiple;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public ForumPost getPost() {
        return post;
    }

    public void setPost(ForumPost post) {
        this.post = post;
    }

    public List<PollOptions> getOptions() {
        return options;
    }

    public void setOptions(List<PollOptions> options) {
        this.options = options;
    }

    public List<User> getVotedUsers() {
        return votedUsers;
    }

    public void setVotedUsers(List<User> votedUsers) {
        this.votedUsers = votedUsers;
    }
}
