package model;
import jakarta.persistence.CascadeType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.OneToOne;
import jakarta.persistence.JoinColumn;
@Entity
@Table(name = "Address")
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

    @OneToOne(cascade = CascadeType.ALL)
    // @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User userAddress;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return userAddress;
    }

    public void setUser(User userAddress) {
        this.userAddress = userAddress;
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
