package dev.waiz.datamanager.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.waiz.datamanager.model.StaffInvites;


public interface StaffInviteRepository extends JpaRepository<StaffInvites, UUID> {
    
    Optional<StaffInvites> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);


}
