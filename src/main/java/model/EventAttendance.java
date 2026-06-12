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
import jakarta.json.bind.annotation.JsonbTransient;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "EVENT_ATTENDANCES")
public class EventAttendance {

    @Id
    @SequenceGenerator(name = "attendanceSeq", sequenceName = "ZSEQ_ATTENDANCE_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "attendanceSeq")
    @Column(name = "id", unique = true)
    private Long id;

    @JsonIgnore
    @JsonbTransient
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @JsonIgnore
    @JsonbTransient
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(name = "status")
    private String status; // "YES", "NO", "MAYBE"

    public EventAttendance() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @JsonIgnore
    @JsonbTransient
    public User getUser() {
        return user;
    }

    @JsonIgnore
    @JsonbTransient
    public void setUser(User user) {
        this.user = user;
    }

    @JsonIgnore
    @JsonbTransient
    public Event getEvent() {
        return event;
    }

    @JsonIgnore
    @JsonbTransient
    public void setEvent(Event event) {
        this.event = event;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    // JSON Helper fields
    public String getUserName() {
        if (user == null) {
            return null;
        }
        try {
            if (io.quarkus.arc.Arc.container().requestContext().isActive()) {
                helper.UserPrincipalResolver resolver = io.quarkus.arc.Arc.container().instance(helper.UserPrincipalResolver.class).get();
                if (resolver != null) {
                    model.User currentUser = resolver.resolveUser();
                    if (currentUser == null || !model.Users.Roles.hasRequiredRole(currentUser.getRole(), model.Users.Roles.FRESHMAN)) {
                        return "Anonym";
                    }
                }
            }
        } catch (Exception e) {
            // fallback
        }
        return user.getUserName();
    }

    public Long getUserId() {
        if (user == null) {
            return null;
        }
        try {
            if (io.quarkus.arc.Arc.container().requestContext().isActive()) {
                helper.UserPrincipalResolver resolver = io.quarkus.arc.Arc.container().instance(helper.UserPrincipalResolver.class).get();
                if (resolver != null) {
                    model.User currentUser = resolver.resolveUser();
                    if (currentUser == null || !model.Users.Roles.hasRequiredRole(currentUser.getRole(), model.Users.Roles.FRESHMAN)) {
                        return null;
                    }
                }
            }
        } catch (Exception e) {
            // fallback
        }
        return user.getId();
    }
}
