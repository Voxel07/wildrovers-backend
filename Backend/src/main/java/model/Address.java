package model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.OneToOne;

@Entity
@Table(name = "USER")
public class Address {
    @Id
    @SequenceGenerator(name = "addressSeq", sequenceName = "ZSEQ_ADDRESS_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "addressSeq")

    @Column(name = "id", unique = true)
    private Long id;
    
    @Column(name = "Street")
    private String street;

    @Column(name = "State")
    private String state;

    @Column(name = "Postcode")
    private Long postcode;

    @Column(name = "StreetNumber")
    private Long streetNumber;

    @Column(name = "AddressSupplements")
    private String addressSupplements;

    @OneToOne(mappedBy = "address")
    private User user;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Long getPostcode() {
        return postcode;
    }

    public void setPostcode(Long postcode) {
        this.postcode = postcode;
    }

    public Long getStreetNumber() {
        return streetNumber;
    }

    public void setStreetNumber(Long streetNumber) {
        this.streetNumber = streetNumber;
    }

    public String getAddressSupplements() {
        return addressSupplements;
    }

    public void setAddressSupplements(String addressSupplements) {
        this.addressSupplements = addressSupplements;
    }
    public Address(){

    }

    public Address(String street, String state, Long postcode, Long streetNumber,
            String addressSupplements) {
        this.street = street;
        this.state = state;
        this.postcode = postcode;
        this.streetNumber = streetNumber;
        this.addressSupplements = addressSupplements;
    }

}
