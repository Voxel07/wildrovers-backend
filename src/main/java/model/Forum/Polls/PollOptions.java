package model.Forum.Polls;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.FetchType;
import jakarta.json.bind.annotation.JsonbTransient;

@Entity
@Table(name = "FORUM_POLL_OPTIONS")
public class PollOptions {
    @Id
    @SequenceGenerator(name = "forumPollOptSeq", sequenceName = "ZSEQ_fPollOpt_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "forumPollOptSeq")
    @Column(name = "id", unique = true)
    private Long id;

    @Column(name = "option_text")
    private String optionText;

    @Column(name = "votes", columnDefinition = "bigint default '0'")
    private Long votes = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id")
    @JsonbTransient
    private Polls poll;

    public PollOptions() {}

    public PollOptions(String optionText) {
        this.optionText = optionText;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOptionText() { return optionText; }
    public void setOptionText(String optionText) { this.optionText = optionText; }

    public Long getVotes() { return votes; }
    public void setVotes(Long votes) { this.votes = votes; }

    public Polls getPoll() { return poll; }
    public void setPoll(Polls poll) { this.poll = poll; }
}
