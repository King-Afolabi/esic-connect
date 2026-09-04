package com.esic.connect.planning.internal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Correction d'une ligne de planning en anomalie, avant publication
 * (EF-PLAN-003 ; docs/02 §13.6 : « corriger une ligne en anomalie sans
 * recommencer l'import »).
 *
 * <p>Le fichier téléversé n'est jamais conservé (RG-036) : la correction
 * modifie les valeurs déjà persistées, puis <strong>rejoue l'analyse de
 * tout le travail</strong>. Ce dernier point n'est pas un excès de zèle :
 * les conflits de planning sont croisés — formateur, classe et salle se
 * disputent un créneau <em>entre</em> lignes. Corriger une heure peut
 * lever le conflit d'une autre ligne, ou en créer un ailleurs. Ne
 * revalider que la ligne touchée laisserait le lot dans un état faux et
 * plausible.
 */
@Service
public class PlanningRowCorrectionService {

    /**
     * Champs corrigeables, avec le nom canonique des colonnes du fichier
     * de planning. Liste fermée : un champ inconnu est refusé, jamais
     * deviné.
     */
    private static final List<String> CORRECTABLE_FIELDS = List.of(
            "slot_key", "session_date", "start_time", "end_time",
            "time_zone_id", "title", "teacher_public_id", "room_code");

    private final PlanningImportJobRepository jobRepository;
    private final PlanningImportRowRepository rowRepository;
    private final PlanningSimulationService simulationService;
    private final Clock clock;

    PlanningRowCorrectionService(PlanningImportJobRepository jobRepository,
                                 PlanningImportRowRepository rowRepository,
                                 PlanningSimulationService simulationService,
                                 Clock clock) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.simulationService = simulationService;
        this.clock = clock;
    }

    /**
     * Applique une correction et renvoie le travail réanalysé.
     *
     * @param corrections valeurs par nom de colonne ; une valeur vide
     *                    efface le champ — une colonne renseignée par
     *                    erreur doit pouvoir être vidée
     */
    @Transactional
    public PlanningImportJob correct(UUID jobPublicId, UUID rowPublicId,
                                     Map<String, String> corrections) {
        requireKnownFields(corrections);
        PlanningImportJob job = jobRepository.findByPublicId(jobPublicId)
                .orElseThrow(() -> new PlanningException(PlanningException.Kind.JOB_NOT_FOUND));
        if (job.getStatus() != PlanningImportJobStatus.SIMULATED) {
            // Publié, annulé ou expiré : plus rien n'y est modifiable.
            // Corriger après publication laisserait croire qu'on peut
            // changer ce qui est déjà devenu des séances.
            throw new PlanningException(PlanningException.Kind.JOB_NOT_SIMULATED);
        }
        if (job.getExpiresAt() != null && job.getExpiresAt().isBefore(clock.instant())) {
            throw new PlanningException(PlanningException.Kind.JOB_EXPIRED);
        }

        PlanningImportRow row = rowRepository.findByPublicId(rowPublicId)
                .filter(candidate -> candidate.getJob().getId().equals(job.getId()))
                .orElseThrow(() -> new PlanningException(PlanningException.Kind.ROW_NOT_FOUND));

        row.setInputs(
                pick(corrections, "slot_key", row.getInputSlotKey()),
                pick(corrections, "session_date", row.getInputSessionDate()),
                pick(corrections, "start_time", row.getInputStartTime()),
                pick(corrections, "end_time", row.getInputEndTime()),
                pick(corrections, "time_zone_id", row.getInputTimeZoneId()),
                pick(corrections, "title", row.getInputTitle()),
                pick(corrections, "teacher_public_id", row.getInputTeacherPublicId()),
                pick(corrections, "room_code", row.getInputRoomCode()));
        rowRepository.saveAndFlush(row);

        return simulationService.revalidate(job);
    }

    static void requireKnownFields(Map<String, String> corrections) {
        boolean unknown = corrections.keySet().stream()
                .anyMatch(field -> !CORRECTABLE_FIELDS.contains(field));
        if (unknown) {
            throw new PlanningException(PlanningException.Kind.CORRECTION_UNKNOWN_FIELD);
        }
    }

    private static String pick(Map<String, String> corrections, String field, String current) {
        if (!corrections.containsKey(field)) {
            return current;
        }
        String value = corrections.get(field);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
