package com.esic.connect.coursesession.internal;

import com.esic.connect.coursesession.SessionLifecycle;
import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Séance exceptionnelle (V9).
 *
 * <p>{@code teacherUserId} est une valeur technique (clé étrangère SQL
 * vers {@code user_account}) : aucune relation JPA vers {@code identity},
 * la référence passe par {@link com.esic.connect.identity.TeacherDirectory}.
 * Le cycle de vie est strict — {@link #open} et {@link #close} sont les
 * seules transitions, aucune modification structurante après l'ouverture.
 */
@Entity
@Table(name = "course_session")
@EntityListeners(AuditingEntityListener.class)
class CourseSession extends BaseEntity {

    @Column(name = "teacher_user_id", nullable = false)
    private Long teacherUserId;

    @Column(name = "title")
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionLifecycle status;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "time_zone_id", nullable = false)
    private String timeZoneId;

    // Nullable depuis V13 : une séance d'origine planning n'a pas de motif
    // d'exception (une séance manuelle en garde un — contrôle applicatif).
    @Column(name = "exception_reason")
    private String exceptionReason;

    // V13 (DEC-G1-001) : identifiant public de l'entrée de planning à
    // l'origine de la séance. NULL ⇒ séance exceptionnelle manuelle ;
    // non NULL ⇒ séance issue d'un planning publié (RG-016).
    @Column(name = "planning_entry_public_id", updatable = false)
    private java.util.UUID planningEntryPublicId;

    // V13 (DEC-G1-004 règle 4) : une republication a retiré le créneau
    // d'origine. La séance est alors filtrée de l'affichage.
    @Column(name = "superseded_by_scheduling", nullable = false)
    private boolean supersededByScheduling;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "opened_by_id")
    private Long openedById;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by_id")
    private Long closedById;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by_id")
    private Long createdById;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by_id")
    private Long updatedById;

    @OneToMany(mappedBy = "courseSession", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("id asc")
    private List<SessionClass> classes = new ArrayList<>();

    protected CourseSession() {
        // JPA
    }

    CourseSession(Long teacherUserId, String title, Instant startsAt, Instant endsAt,
                  String timeZoneId, String exceptionReason) {
        this.teacherUserId = teacherUserId;
        this.title = title;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.timeZoneId = timeZoneId;
        this.exceptionReason = exceptionReason;
        this.status = SessionLifecycle.PLANNED;
    }

    /**
     * Séance d'origine <strong>planning</strong> (V13, DEC-G1-001) :
     * pas de motif d'exception, {@code planningEntryPublicId} renseigné.
     * Créée par {@link DefaultPlanningSessionWriter} à la publication d'un
     * planning.
     */
    static CourseSession fromPlanningEntry(java.util.UUID planningEntryPublicId, Long teacherUserId,
                                           String title, Instant startsAt, Instant endsAt, String timeZoneId) {
        CourseSession session = new CourseSession(teacherUserId, title, startsAt, endsAt, timeZoneId, null);
        session.planningEntryPublicId = planningEntryPublicId;
        return session;
    }

    void markCreatedBy(Long actorId) {
        this.createdById = actorId;
        this.updatedById = actorId;
    }

    /**
     * Met à jour les propriétés modifiables d'une séance d'origine
     * planning encore {@code PLANNED} (DEC-G1-004 règle 5). Ne touche ni
     * au statut, ni au lien d'origine.
     */
    void applyPlanningUpdate(Long teacherUserId, String title, Instant startsAt, Instant endsAt,
                             String timeZoneId, Long actorId) {
        this.teacherUserId = teacherUserId;
        this.title = title;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.timeZoneId = timeZoneId;
        this.updatedById = actorId;
    }

    /**
     * Marque une séance planning {@code PLANNED} comme retirée par une
     * republication (DEC-G1-004 règle 4). Aucune suppression physique ;
     * le statut reste {@code PLANNED} tant que l'état {@code CANCELLED}
     * n'existe pas (G1-C).
     */
    void markSupersededByScheduling(Long actorId) {
        this.supersededByScheduling = true;
        this.updatedById = actorId;
    }

    void addClass(Long classGroupId) {
        this.classes.add(new SessionClass(this, classGroupId));
    }

    void open(Instant at, Long actorId) {
        this.status = SessionLifecycle.OPEN;
        this.openedAt = at;
        this.openedById = actorId;
        this.updatedById = actorId;
    }

    void close(Instant at, Long actorId) {
        this.status = SessionLifecycle.CLOSED;
        this.closedAt = at;
        this.closedById = actorId;
        this.updatedById = actorId;
    }

    boolean isPlanned() {
        return status == SessionLifecycle.PLANNED;
    }

    boolean isOpen() {
        return status == SessionLifecycle.OPEN;
    }

    Long getTeacherUserId() {
        return teacherUserId;
    }

    String getTitle() {
        return title;
    }

    SessionLifecycle getStatus() {
        return status;
    }

    Instant getStartsAt() {
        return startsAt;
    }

    Instant getEndsAt() {
        return endsAt;
    }

    String getTimeZoneId() {
        return timeZoneId;
    }

    String getExceptionReason() {
        return exceptionReason;
    }

    java.util.UUID getPlanningEntryPublicId() {
        return planningEntryPublicId;
    }

    boolean isSupersededByScheduling() {
        return supersededByScheduling;
    }

    Instant getOpenedAt() {
        return openedAt;
    }

    Instant getClosedAt() {
        return closedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    List<SessionClass> getClasses() {
        return classes;
    }
}
