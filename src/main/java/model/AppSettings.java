package model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Single-row settings table (id = 1 always).
 * Hibernate auto-creates/updates the table via schema-management.strategy=update.
 */
@Entity
@Table(name = "APP_SETTINGS")
public class AppSettings {

    /** Always 1 — singleton row. */
    @Id
    @Column(name = "id")
    private Long id = 1L;

    @Column(name = "signupEnabled", nullable = false)
    private boolean signupEnabled = true;

    public AppSettings() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public boolean isSignupEnabled() { return signupEnabled; }
    public void setSignupEnabled(boolean signupEnabled) { this.signupEnabled = signupEnabled; }
}
