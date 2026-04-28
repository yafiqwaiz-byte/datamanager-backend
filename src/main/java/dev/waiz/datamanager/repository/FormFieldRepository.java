package dev.waiz.datamanager.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;


import dev.waiz.datamanager.model.formfield;

public interface FormFieldRepository extends JpaRepository<formfield,UUID>{

    @Modifying
    @Query("DELETE FROM formfield f WHERE f.template.templateId = :templateId")
    void deleteByTemplate_TemplateId(UUID templateId);

    List<formfield> findByTemplate_TemplateId(UUID templateId);
}
