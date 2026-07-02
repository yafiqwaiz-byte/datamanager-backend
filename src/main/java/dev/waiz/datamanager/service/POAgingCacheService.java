package dev.waiz.datamanager.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.waiz.datamanager.dto.POAgingDashboardDTO;
import dev.waiz.datamanager.model.fileupload;
import dev.waiz.datamanager.model.poagingcache;
import dev.waiz.datamanager.model.staff;
import dev.waiz.datamanager.repository.POAgingCacheRepository;
import dev.waiz.datamanager.repository.staffrepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class POAgingCacheService {

    private final POAgingCacheRepository cacheRepository;

    private final staffrepository staffRepository;

    private final ObjectMapper objectMapper;

    public void saveCache(UUID uploadId,fileupload upload,String staffUsername,POAgingDashboardDTO dashboard,boolean hasCleared) {
        try {
            staff currentstaff = staffRepository.findByAccount_Username(staffUsername)
                    .orElseThrow(() -> new RuntimeException("Staff not found: " + staffUsername));

            cacheRepository.deleteByUpload_UploadId(uploadId);

           String json = objectMapper.writeValueAsString(dashboard);
           
            poagingcache cache = poagingcache.builder()
                    .upload(upload)
                    .staff(currentstaff)
                    .dashboardJson(json)
                    .cachedAt(LocalDateTime.now())
                    .hasCleared(hasCleared)
                    .build();

            cacheRepository.save(cache);
            log.info("Dashboard cached for staff:{},uploadId: {}",staffUsername,uploadId);
        } catch (Exception e) {
            log.error("Failed to save PO Aging cache for uploadId {}: {}", uploadId, e.getMessage());
        }
    }

    public Optional<POAgingDashboardDTO> loadLatestCache(String staffUsername){

        try{
            staff currentstaff = staffRepository.findByAccount_Username(staffUsername)
            .orElse(null);

            if (currentstaff == null){
                return Optional.empty();
            }

            return cacheRepository
            .findTopByStaffOrderByCachedAtDesc(currentstaff)
            .map(cache ->{
                try{
                    POAgingDashboardDTO dto = objectMapper.readValue(cache.getDashboardJson(),POAgingDashboardDTO.class);
                    log.info("Loaded cached dashboard for staff:{},cached at:{}",staffUsername,cache.getCachedAt());
                    return dto;
                } catch (Exception e){
                    log.error("Failed to deserialize cached dashboard for staff:{}: {}",staffUsername,e.getMessage());
                    return null;
                }
            });
        } catch (Exception e){
            log.error("Failed to load cached : {}",e.getMessage());
            return Optional.empty();
        }
    }

     public boolean hasCachedDashboard(String staffUsername) {
        staff currentStaff = staffRepository
            .findByAccount_Username(staffUsername)
            .orElse(null);
        if (currentStaff == null) return false;
        return cacheRepository
            .findTopByStaffOrderByCachedAtDesc(currentStaff)
            .isPresent();
    }

    // Add this method to your existing POAgingCacheService.java
// (alongside loadLatestCache and saveCache)

public Optional<POAgingDashboardDTO> loadCacheByUploadId(UUID uploadId) {
    try {
        return cacheRepository.findByUpload_UploadId(uploadId)
            .map(cache -> {
                try {
                    return objectMapper.readValue(
                        cache.getDashboardJson(), POAgingDashboardDTO.class);
                } catch (Exception e) {
                    log.warn("Failed to deserialize cache for uploadId {}: {}",
                        uploadId, e.getMessage());
                    return null;
                }
            });
    } catch (Exception e) {
        log.warn("Failed to load cache by uploadId {}: {}", uploadId, e.getMessage());
        return Optional.empty();
    }
}
}
