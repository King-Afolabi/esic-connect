package com.esic.connect.studentimport.internal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;

/**
 * Génère un numéro étudiant {@code ESIC-{annéeDébut}-{séquence}} quand la
 * colonne CSV {@code student_number} est absente (rapport §3.2). La
 * séquence est allouée <strong>dans la transaction de la confirmation</strong>
 * (propagation {@code REQUIRED}, jamais {@code REQUIRES_NEW}) : verrou de
 * ligne sur {@code student_number_sequence(start_year)}, annulé au
 * rollback. L'unicité SQL {@code uq_student_profile_student_number} reste
 * la seule autorité — la génération n'est qu'un pré-remplissage ; la
 * nouvelle tentative sur collision est gérée par l'orchestrateur.
 */
@Component
class StudentNumberAllocator {

    private final StudentNumberSequenceRepository sequenceRepository;
    private final StudentImportProperties properties;
    private final Clock clock;

    StudentNumberAllocator(StudentNumberSequenceRepository sequenceRepository,
                           StudentImportProperties properties,
                           Clock clock) {
        this.sequenceRepository = sequenceRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Alloue et formate le prochain numéro pour {@code startYear}. À
     * appeler dans la transaction de la confirmation.
     *
     * @throws StudentImportException {@link StudentImportException.Kind#STUDENT_NUMBER_EXHAUSTED}
     *                                si la borne de largeur est atteinte pour l'année
     */
    @Transactional
    String allocate(int startYear) {
        sequenceRepository.bump(startYear, clock.instant());
        Integer nextValue = sequenceRepository.selectNextValue(startYear);
        if (nextValue == null) {
            throw new IllegalStateException("Séquence de numéro étudiant introuvable après bump.");
        }
        int allocated = nextValue - 1;
        if (allocated >= properties.numberSequenceUpperBound()) {
            throw new StudentImportException(StudentImportException.Kind.STUDENT_NUMBER_EXHAUSTED);
        }
        return format(startYear, allocated);
    }

    String format(int startYear, int sequence) {
        return String.format(Locale.ROOT, "ESIC-%04d-%0" + properties.numberSequenceWidth() + "d",
                startYear, sequence);
    }
}
