package dev.waiz.datamanager.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.waiz.datamanager.model.StaffInvites;

@Repository
public interface StaffInviteRepository extends JpaRepository<StaffInvites, UUID> {
    
    Optional<StaffInvites> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);


}
