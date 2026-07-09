package dev.waiz.datamanager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.waiz.datamanager.model.lettertemplate;

public interface LetterTemplateRepository extends JpaRepository<lettertemplate,UUID> {

    List<lettertemplate> findByStaffId_StaffId(UUID staffId);

    List<lettertemplate> findByIsDeletedFalse();
}
