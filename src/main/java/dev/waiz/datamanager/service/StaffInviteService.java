package dev.waiz.datamanager.service;

import dev.waiz.datamanager.model.StaffInvites;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

import dev.waiz.datamanager.repository.StaffInviteRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StaffInviteService {

    
    
    private final StaffInviteRepository staffInviteRepository;

    public StaffInvites generateInviteCode(String email){
        String code = "STAFF-" + java.util.UUID.randomUUID().toString()
        .substring(0, 8)
        .toUpperCase();

   
        StaffInvites invite = StaffInvites.builder()
        .inviteCode(code)
        .email(email)
        .used(false)
        .createdAt(OffsetDateTime.now())
        .expiresAt(OffsetDateTime.now().plusHours(24))
        .build();

        return staffInviteRepository.save(invite);
    }

    public boolean isValidInviteCode(String code) {
        return staffInviteRepository.findByInviteCode(code)
        .map(invite -> !invite.isUsed() 
        && invite.getExpiresAt().isAfter(OffsetDateTime.now()))
        .orElse(false);
    }

    public void markAsUsed(String code){
        staffInviteRepository.findByInviteCode(code).ifPresent(invite -> {
            invite.setUsed(true);
            invite.setUsedAt(OffsetDateTime.now());
            staffInviteRepository.save(invite);
        });
    }

    
}
