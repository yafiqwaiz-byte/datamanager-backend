package dev.waiz.datamanager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import dev.waiz.datamanager.model.formsubmission;
import dev.waiz.datamanager.model.user;

public interface FormSubmissionRepository extends JpaRepository<formsubmission,UUID>{
          Page<formsubmission> findByUser(user user,Pageable pageable);
          List<formsubmission> findByTemplate_TemplateId(UUID templateId);
}
