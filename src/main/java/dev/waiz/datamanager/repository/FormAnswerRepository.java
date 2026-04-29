package dev.waiz.datamanager.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import dev.waiz.datamanager.model.formanswer;

public interface FormAnswerRepository extends JpaRepository<formanswer,UUID>{

    @Modifying
    @Query("DELETE FROM formanswer a WHERE a.field.fieldId = :fieldId")
    void deleteByField_FieldId(UUID fieldId); 
}
