package dev.waiz.datamanager.model;
import java.util.*;
import jakarta.persistence.*;
import lombok.*;

@Data
@Entity
@Table(name = "staff")
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class staff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "staff_id", updatable = false, nullable = false)
    private UUID staffID;

    @OneToOne
    @JoinColumn(name = "account_id", referencedColumnName = "account_id")     
    private account account;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "department", nullable = false)
    private String department;

    @Column(name = "position", nullable = false)
    private String position;

    public staff(account account, String fullName, String department, String position) {
        this.account = account;
        this.fullName = fullName;
        this.department = department;
        this.position = position;
    }

  
    public void setStaffID(String staffID) {
        this.staffID = UUID.fromString(staffID);
    }

    public void setAccount(String accountId) {
        this.account = new account();
        this.account.setAccountId(UUID.fromString(accountId));
    }

    public String toString() {
        return "staff{" +
                "staffID=" + staffID +
                ", account=" + account +
                ", fullName='" + fullName + '\'' +
                ", department='" + department + '\'' +
                ", position='" + position + '\'' +
                '}';
    }
    
}
