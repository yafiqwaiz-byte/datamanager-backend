package dev.waiz.datamanager.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import dev.waiz.datamanager.model.formsubmission;
import dev.waiz.datamanager.model.user;

public interface FormSubmissionRepository extends JpaRepository<formsubmission,UUID>{
          List<formsubmission> findByUser(user user);
}
