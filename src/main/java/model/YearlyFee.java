package model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "YEARLY_FEE")
public class YearlyFee {

    @Id
    @SequenceGenerator(name = "yearlyFeeSeq", sequenceName = "ZSEQ_YEARLY_FEE_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "yearlyFeeSeq")
    @Column(name = "id", unique = true)
    private Long id;

    @Column(name = "fee_year", nullable = false)
    private Integer feeYear;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public YearlyFee() {
    }

    public YearlyFee(Integer feeYear, User user) {
        this.feeYear = feeYear;
        this.user = user;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getFeeYear() {
        return feeYear;
    }

    public void setFeeYear(Integer feeYear) {
        this.feeYear = feeYear;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
