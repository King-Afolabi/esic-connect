package com.esic.connect.audit.internal;

import com.esic.connect.audit.AuditDashboardDirectory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implémentation du port {@link AuditDashboardDirectory}. Confinée à
 * {@code audit.internal}, bornée à quelques lignes par appel.
 */
@Component
class DefaultAuditDashboardDirectory implements AuditDashboardDirectory {

    /** Action écrite par {@code AttendanceAuditListener} pour un export. */
    private static final String EXPORT_ACTION = "ATTENDANCE_REPORT_EXPORTED";
    private static final int MAX = 20;

    private final AuditEventRepository repository;

    DefaultAuditDashboardDirectory(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLine> recent(int limit) {
        return repository.findAll(PageRequest.of(0, bound(limit),
                        Sort.by(Sort.Direction.DESC, "occurredAt")))
                .map(DefaultAuditDashboardDirectory::toLine)
                .getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLine> recentExports(int limit) {
        return repository.findAll(AuditEventSpecifications.hasAction(EXPORT_ACTION),
                        PageRequest.of(0, bound(limit), Sort.by(Sort.Direction.DESC, "occurredAt")))
                .map(DefaultAuditDashboardDirectory::toLine)
                .getContent();
    }

    private static int bound(int limit) {
        return Math.max(1, Math.min(limit, MAX));
    }

    private static AuditLine toLine(AuditEvent event) {
        return new AuditLine(event.getOccurredAt(), event.getActorDisplaySnapshot(),
                event.getAction(), event.getResult());
    }
}
