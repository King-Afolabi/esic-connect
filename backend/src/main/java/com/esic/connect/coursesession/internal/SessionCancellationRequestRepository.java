package com.esic.connect.coursesession.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SessionCancellationRequestRepository extends JpaRepository<SessionCancellationRequest, Long> {

    Optional<SessionCancellationRequest> findByPublicId(UUID publicId);

    Optional<SessionCancellationRequest> findBySession_IdAndStatus(Long sessionId,
                                                                   CancellationRequestStatus status);

    List<SessionCancellationRequest> findBySession_IdOrderByCreatedAtDesc(Long sessionId);

    List<SessionCancellationRequest> findByStatusOrderByCreatedAtAsc(CancellationRequestStatus status);
}
