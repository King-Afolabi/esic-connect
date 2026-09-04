package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.JustificationFileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Balayage des fichiers orphelins du stockage des justificatifs
 * (EF-JUS-002 ; docs/02 §19.5 — « un balayage périodique détecte et
 * supprime les fichiers orphelins »).
 *
 * <p>Un orphelin est un contenu qu'<strong>aucune ligne active ne
 * référence</strong>. Il apparaît lorsque la suppression du fichier
 * échoue après le passage de la ligne en {@code DELETED} : la base est
 * cohérente, le disque non. Sans balayage, ce fichier — une donnée
 * personnelle — resterait indéfiniment, sans propriétaire ni moyen de le
 * retrouver.
 *
 * <p>Trois précautions :
 *
 * <ul>
 *   <li><strong>lot borné</strong> : le balayage ne prétend pas traiter
 *       tout le stockage en une passe ; il en traite une tranche à chaque
 *       exécution ;</li>
 *   <li><strong>âge de grâce</strong> implicite via la borne de la
 *       réconciliation : un fichier vient d'être écrit alors que sa ligne
 *       n'est pas encore {@code STORED} — mais elle existe déjà en
 *       {@code PENDING_STORAGE}, donc il est référencé et n'est jamais vu
 *       comme orphelin. C'est l'ordre du dépôt (ligne d'abord, fichier
 *       ensuite) qui rend ce balayage sûr ;</li>
 *   <li><strong>journalisation sans chemin ni identité</strong> : un
 *       compte, rien de plus.</li>
 * </ul>
 *
 * <p>Désactivable par {@code app.attendance.orphan-sweep-enabled=false} —
 * un stockage partagé avec un autre système ne doit pas être balayé par
 * celui-ci.
 */
@Component
class JustificationOrphanSweepService {

    private static final Logger log = LoggerFactory.getLogger(JustificationOrphanSweepService.class);

    private final JustificationAttachmentRepository repository;
    private final JustificationFileStorage storage;
    private final boolean enabled;
    private final int batchSize;

    JustificationOrphanSweepService(
            JustificationAttachmentRepository repository,
            JustificationFileStorage storage,
            @Value("${app.attendance.orphan-sweep-enabled:true}") boolean enabled,
            @Value("${app.attendance.orphan-sweep-batch:500}") int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalStateException(
                    "app.attendance.orphan-sweep-batch doit être strictement positif.");
        }
        this.repository = repository;
        this.storage = storage;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.attendance.orphan-sweep-interval-ms:3600000}",
            initialDelayString = "${app.attendance.orphan-sweep-initial-delay-ms:600000}")
    void sweep() {
        if (!enabled) {
            return;
        }
        int removed = sweepOnce();
        if (removed > 0) {
            log.info("Balayage des justificatifs : {} fichier(s) orphelin(s) supprimé(s).", removed);
        }
    }

    /**
     * Une passe. Renvoie le nombre de fichiers supprimés — exposé pour
     * que le test puisse déclencher le balayage sans attendre
     * l'ordonnanceur.
     */
    int sweepOnce() {
        List<String> candidates = storage.listKeys(batchSize);
        if (candidates.isEmpty()) {
            return 0;
        }
        Set<String> referenced = new HashSet<>(repository.findReferencedStorageKeys(candidates));
        int removed = 0;
        for (String key : candidates) {
            if (referenced.contains(key)) {
                continue;
            }
            try {
                storage.delete(key);
                removed++;
            } catch (RuntimeException failure) {
                // Un échec ne doit pas interrompre le lot : le fichier
                // suivant n'y est pour rien.
                log.warn("Suppression d'un fichier orphelin échouée : cause={}",
                        failure.getClass().getSimpleName());
            }
        }
        return removed;
    }
}
