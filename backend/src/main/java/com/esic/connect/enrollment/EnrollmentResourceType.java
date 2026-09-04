package com.esic.connect.enrollment;

/** Type de ressource du module {@code enrollment} concernée par un changement audité. */
public enum EnrollmentResourceType {
    STUDENT_PROFILE,
    ENROLLMENT,
    /** Autorisation de suivi à distance individuel (EF-ENR-004). */
    REMOTE_ATTENDANCE_AUTHORIZATION,
    /** Groupe temporaire d'apprenants (EF-ACA-007). */
    STUDENT_GROUP
}
