package com.esic.connect.enrollment.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StudentGroupMemberRepository extends JpaRepository<StudentGroupMember, Long> {

    List<StudentGroupMember> findByStudentGroupIdAndStatus(Long studentGroupId,
                                                           StudentGroupMemberStatus status);

    Optional<StudentGroupMember> findByStudentGroupIdAndEnrollmentIdAndStatus(
            Long studentGroupId, Long enrollmentId, StudentGroupMemberStatus status);

    long countByStudentGroupIdAndStatus(Long studentGroupId, StudentGroupMemberStatus status);

    List<StudentGroupMember> findByStudentGroupIdInAndStatus(Collection<Long> studentGroupIds,
                                                             StudentGroupMemberStatus status);

    Optional<StudentGroupMember> findByPublicIdAndStudentGroupId(UUID publicId, Long studentGroupId);
}
