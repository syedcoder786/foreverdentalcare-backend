package com.clinic.service;

import com.clinic.dto.AdminDtos.AuditLogResponse;
import com.clinic.dto.response.PageResponse;
import com.clinic.entity.AuditLog;
import com.clinic.repository.AuditLogRepository;
import com.clinic.security.AdminPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records important administrative changes. Runs inside the caller's transaction, so an audit row exists only if the
 * change itself was committed. Summaries must never contain patient clinical details.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    public void record(String action, String entityType, Object entityId, String summary) {
        AuditLog entry = new AuditLog();
        entry.setAdminEmail(currentAdminEmail());
        entry.setAction(action);
        entry.setEntityType(entityType);
        String id = entityId == null ? null : String.valueOf(entityId);
        entry.setEntityId(id != null && id.length() > 50 ? id.substring(0, 50) : id); // column is VARCHAR(50)
        entry.setSummary(summary == null ? null : summary.length() > 500 ? summary.substring(0, 500) : summary);
        repository.save(entry);
        log.info("AUDIT {} {} {} by {}: {}", action, entityType, entityId, entry.getAdminEmail(), entry.getSummary());
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(int page, int size) {
        return PageResponse.from(repository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)), AuditService::toResponse);
    }

    public static AuditLogResponse toResponse(AuditLog a) {
        return new AuditLogResponse(a.getId(), a.getAdminEmail(), a.getAction(), a.getEntityType(), a.getEntityId(),
                a.getSummary(), a.getCreatedAt());
    }

    private static String currentAdminEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AdminPrincipal p) {
            return p.email();
        }
        return "system";
    }
}
