package dev.waiz.datamanager.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import dev.waiz.datamanager.model.activitylog;
import dev.waiz.datamanager.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    /**
     * Records a staff/system activity for the dashboard feed.
     * Call this at the point an action completes successfully —
     * never let a logging failure break the calling operation.
     */
    public void log(String type, String description, String module, String status) {
        try {
            String performedBy = currentUsername();

            activitylog entry = new activitylog();
            entry.setType(type);
            entry.setDescription(description);
            entry.setModule(module);
            entry.setStatus(status);
            entry.setPerformedBy(performedBy);

            activityLogRepository.save(entry);
        } catch (Exception e) {
            // Never let activity logging break the actual business operation
            log.warn("Failed to write activity log [{} / {}]: {}", type, module, e.getMessage());
        }
    }

    public List<activitylog> getRecent(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return activityLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    private String currentUsername() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            return null;
        }
    }
}