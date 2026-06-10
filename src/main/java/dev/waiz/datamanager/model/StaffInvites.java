package dev.waiz.datamanager.model;



import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.*;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "staff_invites")
public class StaffInvites {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID inviteId;
    
    @Column(name = "invite_code", nullable = false, unique = true)
    private String inviteCode;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "used", nullable = false)
    @Builder.Default
    private boolean used = false;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Builder.Default
    private OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(24);

    private OffsetDateTime usedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
        if (this.expiresAt == null) {
        this.expiresAt = OffsetDateTime.now().plusHours(24);// Invite expires in 1 day
        }
    
    }
}
