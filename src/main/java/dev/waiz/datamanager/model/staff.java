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
    private UUID staffId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", referencedColumnName = "account_id")     
    private account account;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "department", nullable = false)
    private String department;

    @Column(name = "position", nullable = false)
    private String position;

  
}
