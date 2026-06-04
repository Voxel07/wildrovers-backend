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
import java.time.LocalDate;
import jakarta.json.bind.annotation.JsonbTransient;

@Entity
@Table(name = "GALLERY")
public class Gallery {

    @Id
    @SequenceGenerator(name = "gallerySeq", sequenceName = "ZSEQ_GALLERY_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "gallerySeq")
    @Column(name = "id", unique = true)
    private Long id;

    @NotBlank(message = "Titel der Galerie darf nicht leer sein.")
    @Column(name = "title")
    private String title;

    @NotBlank(message = "URL der Galerie darf nicht leer sein.")
    @Column(name = "url")
    private String url;

    @NotNull(message = "Datum der Galerie muss gesetzt sein.")
    @Column(name = "gallery_date")
    private LocalDate date;

    @NotBlank(message = "Ort der Galerie darf nicht leer sein.")
    @Column(name = "location")
    private String location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    public Gallery() {
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @JsonbTransient
    public User getCreator() {
        return creator;
    }

    public void setCreator(User creator) {
        this.creator = creator;
    }

    // JSON Helper
    public String getCreatorName() {
        return creator != null ? creator.getUserName() : null;
    }
}
