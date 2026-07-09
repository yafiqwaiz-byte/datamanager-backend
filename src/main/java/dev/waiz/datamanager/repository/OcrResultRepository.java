package dev.waiz.datamanager.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.waiz.datamanager.model.ocrresult;

public interface OcrResultRepository extends JpaRepository<ocrresult, UUID> {

    // ── Single result — used when OCR ID maps 1:1 to upload ───────
    Optional<ocrresult> findByUpload_UploadId(UUID uploadId);

    long countByStatus(String status);

    // ── List results — used for user's submission history ─────────
    List<ocrresult> findAllByUpload_UploadId(UUID uploadId);

    // ── User's own submissions — fetch upload + selectedTemplate ───
    // Both associations are LAZY on the entity; fetch-joining them
    // here prevents LazyInitializationException in the controller.
    @Query("SELECT o FROM ocrresult o " +
           "LEFT JOIN FETCH o.upload u " +
           "LEFT JOIN FETCH u.user us " +
           "LEFT JOIN FETCH us.account a " +
           "LEFT JOIN FETCH o.selectedTemplate " +
           "WHERE a.username = :username")
    List<ocrresult> findAllByUsernameWithUpload(@Param("username") String username);

    // ── Staff queue — pending only ─────────────────────────────────
    // ✅ Also fetch selectedTemplate to avoid LazyInitializationException
    // when getPendingQueue() calls ocr.getSelectedTemplate().getTemplateName()
    @Query("SELECT o FROM ocrresult o " +
           "LEFT JOIN FETCH o.upload " +
           "LEFT JOIN FETCH o.selectedTemplate " +
           "WHERE o.status = :status")
    List<ocrresult> findByStatusWithUpload(@Param("status") String status);

    // ── Staff queue — all statuses ─────────────────────────────────
    // ✅ Same fix — fetch selectedTemplate alongside upload
    @Query("SELECT o FROM ocrresult o " +
           "LEFT JOIN FETCH o.upload " +
           "LEFT JOIN FETCH o.selectedTemplate " +
           "WHERE o.status IS NOT NULL")
    List<ocrresult> findAllByStatusNotNullWithUpload();


       // ── Staff queue — all letter requests ─────────────────────────
   @Query("SELECT o FROM ocrresult o " +
       "LEFT JOIN FETCH o.upload " +
       "LEFT JOIN FETCH o.selectedTemplate " +
       "WHERE o.selectedTemplate IS NOT NULL")
       List<ocrresult> findAllLetterRequestsWithUpload();


}