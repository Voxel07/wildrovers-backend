package   model;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.json.bind.annotation.JsonbTransient;
import javax.persistence.Column;


@Entity
@Table(name ="PHONE")
public class Phone{

    @Id
    @SequenceGenerator(name = "phoneSeq", sequenceName = "ZSEQ_phone_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "phoneSeq")
    
    @Column(name = "id", unique = true)
    private Long id;
    
    @Column(name = "number", unique = true)
    private String number;
    
    @Column(name = "type")
    private String type;
    //Uni ohne irgendwas
    
    //Bi    
	 @ManyToOne(fetch=FetchType.LAZY)
	 @JoinColumn(name ="user_id", referencedColumnName="id")
	 private User usr;
    
      
    public Phone() {
    	
    }
    
    public Phone(String number, String type) {
    	this.number = number;
    	this.type = type;
    }
    
    public void setId(Long id) {
        this.id = id;
    } 
    public Long getId() {
        return id;
    }
    
    public String getNumber() {
        return number;
    }

    public void setNumber(String name) {
        this.number = name;
    }
    
    public String getType() {
        return type;
    }

    public void setType (String name) {
        this.type = name;
    }
    
   //Ab hier zusammenhang mit der Phone Klasse
   @JsonbTransient
    public User getUsr() {
		return usr;
	}

	public void setUsr(User usr) {
		this.usr = usr;
	}

	@Override
	public String toString() {
		return "Phone [id=" + id + ", number=" + number + ", type=" + type + "]";
	}
    

   
   
}