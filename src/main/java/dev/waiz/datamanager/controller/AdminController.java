package dev.waiz.datamanager.controller;


import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import dev.waiz.datamanager.model.StaffInvites;
import dev.waiz.datamanager.model.account;
import dev.waiz.datamanager.service.EmailService;
import dev.waiz.datamanager.service.StaffInviteService;
import dev.waiz.datamanager.service.accountservice;
import lombok.RequiredArgsConstructor;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {

     private final accountservice accountService;
     private final StaffInviteService staffInviteService;
     private final EmailService emailService;


    
    @PostMapping("/invite/generate")
    public ResponseEntity<?> generateInvite(@RequestBody Map<String,String> body){
        String email = body.get("email");
        if(email == null || email.isBlank()){
            return ResponseEntity.badRequest().body("Email is required to generate an invite code.");
        }

        StaffInvites invite = staffInviteService.generateInviteCode(email);
        emailService.sendInviteCode(email, invite.getInviteCode());

        return ResponseEntity.ok(Map.of(
        "message","Invite code generated and sent to " + email,
        "inviteCode", invite.getInviteCode(),
        "expiresAt", invite.getExpiresAt().toString()
        ));

    }

    @GetMapping("/accounts/pending")
    public ResponseEntity<?> getPendingAccounts(){
        List<account> pendingAccounts = accountService.getAccountsByStatus("pending");
        return ResponseEntity.ok(pendingAccounts);
    }

    @GetMapping("/accounts/staff")
    public ResponseEntity<?> getAllStaffAccounts(){
        List<account> staffAccounts = accountService.getAccountsByRole("STAFF");
        return ResponseEntity.ok(staffAccounts);
    }

    @PatchMapping("/accounts/{accountId}/approve")
    public ResponseEntity<?> approveStaffAccount(@PathVariable UUID accountId){
       account updated = accountService.updateAccountStatus(accountId, "active");
       if(updated == null){
        return ResponseEntity.status(404).body("Account not found");
       }

       try{
        emailService.sendApprovalNotification(updated.getUsername(), updated.getUsername());
       } catch (Exception e){
        // Log the error but don't fail the request since the account is already approved
        System.out.println("Email failed but account approved: " + e.getMessage());
       }
       return ResponseEntity.ok(Map.of("message","Account approved successfully",
        "username", updated.getUsername(),
        "status", updated.getStatus()));
       
    }

    @PatchMapping("/accounts/{accountId}/reject")
    public ResponseEntity<?> rejectStaff(@PathVariable UUID accountId){
        account updated = accountService.updateAccountStatus(accountId,"rejeccted");
        if (updated == null){
            return ResponseEntity.status(404).body("Account not found");
        }

        try{
            emailService.sendRejectionNotification(updated.getUsername(),updated.getUsername());
        } catch (Exception e){
            System.out.println("Email failed but account rejected:" + e.getMessage());
        }

        return ResponseEntity.ok(Map.of(
            "message","Staff account rejected.",
            "username",updated.getUsername(),
            "status",updated.getStatus()
        ));
    }


    @PatchMapping("/accounts/{accountId}/reactivate")
    public ResponseEntity<?> reactivateAccount(@PathVariable UUID accountId){
        account updated = accountService.updateAccountStatus(accountId, "active");
        if (updated == null) {
            return ResponseEntity.status(404).body("Account not found");
        }
        return ResponseEntity.ok(Map.of(
            "message", "Account reactivated",
            "status", updated.getStatus()
        ));
    }
}
