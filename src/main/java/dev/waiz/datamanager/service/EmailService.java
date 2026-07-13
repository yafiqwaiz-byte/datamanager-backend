package dev.waiz.datamanager.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Async                      // ← ADD THIS
    public void sendInviteCode(String toEmail, String inviteCode) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("You're Invited to Join DataManager- Staff Registration");
            message.setText("Hello,\n\n" +
                "You have been invited to register as a staff member on DataManager.\n\n" +
                "Your invite code: " + inviteCode + "\n\n" +
                "Register here: " + frontendUrl + "/signup\n\n" +
                "Steps:\n" +
                "1. Click the link above\n" +
                "2. Select 'Staff' tab\n" +
                "3. Fill in your details\n" +
                "4. Enter invite code: " + inviteCode + "\n" +
                "5. Submit and wait for admin approval\n\n" +
                "Note: This code expires in 24 hours.\n\n" +
                "If you did not request this, please ignore this email.\n\n" +
                "Regards,\nDataManager Admin");
            mailSender.send(message);
            log.info("SUCCESS: Invite email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("ERROR: Failed to send invite email to {}: {}", toEmail, e.getMessage());
            // Note: do NOT rethrow here — @Async methods
            // run on a separate thread so the caller
            // won't catch it anyway. Log it instead.
        }
    }

    @Async                      // ← ADD THIS
    public void sendApprovalNotification(String toEmail, String username) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("DataManager - Staff Account Approved");
            message.setText("Hello " + username + ",\n\n" +
                "Great news! Your staff account has been approved.\n\n" +
                "You can now login at: " + frontendUrl + "/signin\n\n" +
                "Username: " + username + "\n\n" +
                "Regards,\nDataManager Admin");
            mailSender.send(message);
            log.info("SUCCESS: Approval email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("ERROR: Failed to send approval email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async                      // ← ADD THIS
    public void sendRejectionNotification(String toEmail, String username) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("DataManager - Staff Account Rejected");
            message.setText("Hello " + username + ",\n\n" +
                "We regret to inform you that your staff account registration has been rejected.\n\n" +
                "If you believe this is a mistake, please contact support.\n\n" +
                "Regards,\nDataManager Admin");
            mailSender.send(message);
            log.info("SUCCESS: Rejection email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("ERROR: Failed to send rejection email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async                      // ← ADD THIS
    public void sendStaffRegistrationAlert(String adminEmail, String newStaffUsername) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(adminEmail);
            message.setSubject("DataManager - New Staff Registration Pending Approval");
            message.setText(
                "Hello Admin,\n\n" +
                "A new staff account is pending your approval.\n\n" +
                "Username: " + newStaffUsername + "\n\n" +
                "Please login to the Admin Dashboard to approve or reject this account.\n\n" +
                "Regards,\nDataManager System"
            );
            mailSender.send(message);
            log.info("SUCCESS: Admin alert email sent to {}", adminEmail);
        } catch (Exception e) {
            log.error("ERROR: Failed to send admin alert email to {}: {}", adminEmail, e.getMessage());
        }
    }
}