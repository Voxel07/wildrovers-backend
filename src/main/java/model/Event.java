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
import java.time.LocalDateTime;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "EVENT")
public class Event {

    @Id
    @SequenceGenerator(name = "eventSeq", sequenceName = "ZSEQ_EVENT_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "eventSeq")
    @Column(name = "id", unique = true)
    private Long id;

    @NotBlank(message = "Event-Titel darf nicht leer sein.")
    @Column(name = "title")
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @NotNull(message = "Event-Datum und -Uhrzeit müssen gesetzt sein.")
    @Column(name = "event_date")
    private LocalDateTime eventDate;

    @Column(name = "event_end_date")
    private LocalDateTime eventEndDate;

    @NotBlank(message = "Veranstaltungsort darf nicht leer sein.")
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

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<EventAttendance> attendances = new ArrayList<>();

    @jakarta.persistence.Transient
    private List<String> nonRespondents = new java.util.ArrayList<>();

    public Event() {
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
