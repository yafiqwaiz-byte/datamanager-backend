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

        @Query("""
                SELECT DISTINCT s FROM formsubmission s
                LEFT JOIN FETCH s.answers a
                LEFT JOIN FETCH a.field
                WHERE s.user =:user
                ORDER BY s.submittedAt DESC
                """)
        List<formsubmission> findByUserWithAnswers(@Param("user") user user,Pageable pageable);

        @Query("SELECT COUNT(s) FROM formsubmission s WHERE s.user =:user")
        long countUser(@Param("user")user user);

         @Query("""
        SELECT DISTINCT s FROM formsubmission s
        LEFT JOIN FETCH s.answers a
        LEFT JOIN FETCH a.field
        WHERE s.template.templateId = :templateId
        ORDER BY s.submittedAt DESC
        """)
    List<formsubmission> findByTemplateIdWithAnswers(@Param("templateId") UUID templateId, Pageable pageable);

    long countByTemplate_TemplateId(UUID templateId);

    @Query("""
           SELECT DISTINCT s FROM formsubmission s 
           LEFT JOIN FETCH s.answers a
           LEFT JOIN FETCH a.field 
           WHERE s.template.templateId IN :templateIds
           ORDER BY  s.submittedAt DESC         
                    """)
    List<formsubmission> findByTemplateIdsWithAnswers(@Param("templateIds") List<UUID> templateIds,Pageable pageable);

    long countByTemplate_TemplateIdIn(List<UUID> tempalateIds);

 
}
