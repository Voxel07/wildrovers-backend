package model.Forum.Polls;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;
import model.User;
import java.util.List;
import java.util.ArrayList;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.FetchType;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.Transient;
import java.time.LocalDate;

@Entity
@Table(name = "FORUM_POLL_OPTIONS", indexes = {
        // Deleting an event clears the link of its poll option.
        @jakarta.persistence.Index(name = "IDX_FORUM_POLL_OPTIONS_EVENT", columnList = "event_id") })
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

    /** First day of a date option (null for plain text options). */
    @Column(name = "option_date")
    private LocalDate optionDate;

    /** Last day of a multi-day date option (null for single days and text options). */
    @Column(name = "option_end_date")
    private LocalDate optionEndDate;

    /**
     * Event created from this option (no JPA relation, like Event.forumPostId: the event may be
     * deleted on its own, which clears this id). Never taken from the client.
     */
    @Column(name = "event_id")
    private Long eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id")
    @JsonbTransient
    private Polls poll;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "FORUM_POLL_OPTION_VOTES", joinColumns = @JoinColumn(name = "option_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    @JsonbTransient
    private List<User> votedUsers = new ArrayList<>();

    @Transient
    private List<String> voterNames;

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

    public LocalDate getOptionDate() { return optionDate; }
    public void setOptionDate(LocalDate optionDate) { this.optionDate = optionDate; }

    public LocalDate getOptionEndDate() { return optionEndDate; }
    public void setOptionEndDate(LocalDate optionEndDate) { this.optionEndDate = optionEndDate; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public Polls getPoll() { return poll; }
    public void setPoll(Polls poll) { this.poll = poll; }

    public List<User> getVotedUsers() {
        if (votedUsers == null) {
            votedUsers = new ArrayList<>();
        }
        return votedUsers;
    }

    public void setVotedUsers(List<User> votedUsers) {
        this.votedUsers = votedUsers;
    }

    public List<String> getVoterNames() {
        return voterNames;
    }

    public void setVoterNames(List<String> voterNames) {
        this.voterNames = voterNames;
    }
}
