package dev.waiz.datamanager.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Value;

import dev.waiz.datamanager.model.account;
import dev.waiz.datamanager.service.accountservice;

@Component
public class SuperAdminSeeder implements CommandLineRunner {

    @Autowired private accountservice accountService;

    @Value("${app.superadmin.username}")
    private String superAdminUsername;

    @Value("${app.superadmin.password}")
    private String superAdminPassword;

    @Override
    public void run(String... args) {

        if (superAdminUsername == null || superAdminPassword.isBlank() || superAdminUsername == null || superAdminPassword.isBlank()){
            System.out.println("⚠️ SuperAdmin credentials not configured in application.yaml. Skipping seeder.");
            return;
        }

        if(!accountService.usernameExists(superAdminUsername)){
            account superAdmin = new account(
                superAdminUsername,
                superAdminPassword,
                "ADMIN",
                "active"
               
            );
            accountService.createAccount(superAdmin);
             System.out.println("✅ Super admin account created with username: " + superAdminUsername);
        } else {
            
                System.out.println("✅ Super admin account already exists with username: " + superAdminUsername);
        }
    }


}
