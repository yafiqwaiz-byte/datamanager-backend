package dev.waiz.datamanager.model;
import java.util.*;
import jakarta.persistence.*;
import lombok.*;


@Table(name = "users")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Data
public class user {
    @Id
     @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userID;
   
    @OneToOne
    @JoinColumn(name = "account_id", referencedColumnName = "account_id")   
    private account account;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "company_name", nullable = false, unique = true)
    private String companyName;

    @Column(name = "phone_no", nullable = false)
    private String phoneNo;

    @Column(name = "company_address", nullable = false)
    private String companyAddress;

    public user(account account, String fullName, String companyName, String phoneNo, String companyAddress) {
        this.account = account;
        this.fullName = fullName;
        this.companyName = companyName;
        this.phoneNo = phoneNo;
        this.companyAddress = companyAddress;
    }

}
