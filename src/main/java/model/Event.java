package model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "EVENT", indexes = @jakarta.persistence.Index(name = "IDX_EVENT_FORUM_POST", columnList = "forum_post_id"))
public class Event {

    @Id
    @SequenceGenerator(name = "eventSeq", sequenceName = "ZSEQ_EVENT_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "eventSeq")
    @Column(name = "id", unique = true)
    private Long id;

    @NotBlank(message = "Event-Titel darf nicht leer sein.")
    // The forum post title adds about 30 characters and must fit into 255.
    @Size(max = 200, message = "Der Event-Titel darf höchstens 200 Zeichen lang sein.")
    @Column(name = "title")
    private String title;

    public static final int DESCRIPTION_MAX_LENGTH = 1000;

    @Column(name = "description", length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @NotNull(message = "Event-Datum und -Uhrzeit müssen gesetzt sein.")
    @Column(name = "event_date")
    private LocalDateTime eventDate;

    @Column(name = "event_end_date")
    private LocalDateTime eventEndDate;

    @NotBlank(message = "Veranstaltungsort darf nicht leer sein.")
    @Size(max = 255, message = "Der Veranstaltungsort darf höchstens 255 Zeichen lang sein.")
    @Column(name = "location")
    private String location;

    @Column(name = "google_calendar_event_id")
    private String googleCalendarEventId;

    @JsonIgnore
    @JsonbTransient
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    @Column(name = "forum_post_url")
    private String forumPostUrl;

    /** Discussion post of this event (no JPA relation: the post may be deleted on its own). */
    @Column(name = "forum_post_id")
    private Long forumPostId;

    /**
     * Set (epoch millis) when the Google Calendar entry must be created or updated, null once it
     * is in sync. Calendar calls run after the database commit and are retried by a scheduler.
     */
    @JsonIgnore
    @JsonbTransient
    @Column(name = "calendar_sync_requested")
    private Long calendarSyncRequested;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<EventAttendance> attendances = new ArrayList<>();

    @jakarta.persistence.Transient
    private List<String> nonRespondents = new java.util.ArrayList<>();

    public Event() {
    }

    /**
     * Detached copy for one response with its own non-respondent list. Cached events are shared
     * between requests, so per-reader data must never be written into them.
     */
    public Event copyWithNonRespondents(List<String> nonRespondents) {
        Event copy = new Event();
        copy.id = id;
        copy.title = title;
        copy.description = description;
        copy.eventDate = eventDate;
        copy.eventEndDate = eventEndDate;
        copy.location = location;
        copy.googleCalendarEventId = googleCalendarEventId;
        copy.creator = creator;
        copy.forumPostUrl = forumPostUrl;
        copy.forumPostId = forumPostId;
        copy.attendances = attendances == null ? new ArrayList<>() : new ArrayList<>(attendances);
        copy.nonRespondents = nonRespondents == null ? new ArrayList<>() : nonRespondents;
        return copy;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDateTime eventDate) {
        this.eventDate = eventDate;
    }

    public LocalDateTime getEventEndDate() {
        return eventEndDate;
    }

    public void setEventEndDate(LocalDateTime eventEndDate) {
        this.eventEndDate = eventEndDate;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getGoogleCalendarEventId() {
        return googleCalendarEventId;
    }

    public void setGoogleCalendarEventId(String googleCalendarEventId) {
        this.googleCalendarEventId = googleCalendarEventId;
    }

    @JsonIgnore
    @JsonbTransient
    public User getCreator() {
        return creator;
    }

    @JsonIgnore
    @JsonbTransient
    public void setCreator(User creator) {
        this.creator = creator;
    }

    // JSON Helper
    public String getCreatorName() {
        return creator != null ? creator.getUserName() : null;
    }

    public String getForumPostUrl() {
        return forumPostUrl;
    }

    public void setForumPostUrl(String forumPostUrl) {
        this.forumPostUrl = forumPostUrl;
    }

    public Long getForumPostId() {
        return forumPostId;
    }

    public void setForumPostId(Long forumPostId) {
        this.forumPostId = forumPostId;
    }

    @JsonIgnore
    @JsonbTransient
    public Long getCalendarSyncRequested() {
        return calendarSyncRequested;
    }

    @JsonIgnore
    @JsonbTransient
    public void setCalendarSyncRequested(Long calendarSyncRequested) {
        this.calendarSyncRequested = calendarSyncRequested;
    }

    public List<EventAttendance> getAttendances() {
        return attendances;
    }

    public void setAttendances(List<EventAttendance> attendances) {
        this.attendances = attendances;
    }

    public List<String> getNonRespondents() {
        return nonRespondents;
    }

    public void setNonRespondents(List<String> nonRespondents) {
        this.nonRespondents = nonRespondents;
    }
}
