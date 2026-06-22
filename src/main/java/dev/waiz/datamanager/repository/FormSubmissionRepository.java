package dev.waiz.datamanager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.waiz.datamanager.model.formsubmission;
import dev.waiz.datamanager.model.user;




public interface FormSubmissionRepository extends JpaRepository<formsubmission,UUID>{

    @Query("SELECT f.submissionId FROM formsubmission f WHERE f.user =:user ORDER BY f.submittedAt DESC")
    Page<UUID> findIdsByUser(@Param("user")user user,Pageable pageable);

   @Query("SELECT DISTINCT f FROM formsubmission f LEFT JOIN FETCH f.template LEFT JOIN FETCH f.answers a LEFT JOIN FETCH a.field WHERE f.submissionId IN :ids ORDER BY f.submittedAt DESC")
    List<formsubmission> findByIdsWithAnswers(@Param("ids") List<UUID> ids);

    @Query("SELECT f.submissionId FROM formsubmission f WHERE f.template.templateId =:templateId ORDER BY f.submittedAt DESC")
    Page<UUID> findIdsByTemplateId(@Param("templateId") UUID templateId,Pageable pageable);

    @Query("SELECT f.submissionId FROM formsubmission f WHERE f.template.templateId IN :templateIds ORDER BY f.submittedAt DESC")
    Page<UUID> findIdsByTemplateIds(@Param("templateIds") List<UUID> templateIds,Pageable pageable);

    // Used by FormTemplateService.deleteTemplate to block hard-deleting a template
    // that already has submissions attached — staff should deactivate instead.
    @Query("SELECT COUNT(f) FROM formsubmission f WHERE f.template.templateId = :templateId")
    long countByTemplateId(@Param("templateId") UUID templateId);


 
}