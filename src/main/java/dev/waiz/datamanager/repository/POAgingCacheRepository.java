package dev.waiz.datamanager.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import dev.waiz.datamanager.model.poagingcache;
import dev.waiz.datamanager.model.staff;



public interface POAgingCacheRepository extends JpaRepository<poagingcache, UUID> {

    Optional<poagingcache> findByUpload_UploadId(UUID uploadId);

    Optional<poagingcache> findTopByStaffOrderByCachedAtDesc(staff staff);

    @Transactional
    void deleteByUpload_UploadId(UUID uploadId);
}
