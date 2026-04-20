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
    @Column(name = "staff_id", updatable = false, nullable = false)
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

    public UUID getUserID() {
        return userID;
    }

    public account getAccount() {
        return account;
    }

    public String getFullName() {
        return fullName;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getPhoneNo() {
        return phoneNo;
    }

    public String getCompanyAddress() {
        return companyAddress;
    }

    public void setAccount(account account) {
        this.account = account;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public void setPhoneNo(String phoneNo) {
        this.phoneNo = phoneNo;
    }

    public void setCompanyAddress(String companyAddress) {
        this.companyAddress = companyAddress;
    }

    public void setUserID(UUID userID) {
        this.userID = userID;
    }

    public String toString() {
        return "user{" +
                "userID=" + userID +
                ", account=" + account +
                ", fullName='" + fullName + '\'' +
                ", companyName='" + companyName + '\'' +
                ", phoneNo='" + phoneNo + '\'' +
                ", companyAddress='" + companyAddress + '\'' +
                '}';
    }


}
