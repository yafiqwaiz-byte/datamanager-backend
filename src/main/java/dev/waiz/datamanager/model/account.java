package dev.waiz.datamanager.model;

import java.time.OffsetDateTime;
import java.util.*;
import jakarta.persistence.*;
import lombok.*;

@Data
@Table(name = "Account")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class account {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "account_id",updatable = false, nullable = false)
    private UUID accountId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private  OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    public account(String username, String passwordHash, String role, String status) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public String toString() {
        return "account{" +
                "accountId=" + accountId +
                ", username='" + username + '\'' +
                ", passwordHash='" + passwordHash + '\'' +
                ", role='" + role + '\'' +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
    
}
