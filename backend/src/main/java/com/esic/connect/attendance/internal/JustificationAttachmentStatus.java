package com.esic.connect.attendance.internal;

/**
 * Cycle de vie d'une pièce jointe de justificatif (V16 ; bloc G1-E ;
 * DEC-G1-009). Le système de fichiers n'étant pas transactionnel avec
 * MySQL, la pièce transite par un état intermédiaire :
 *
 * <ul>
 *   <li>{@link #PENDING_STORAGE} : ligne insérée dans la transaction
 *       métier, contenu pas encore déplacé dans sa zone définitive.
 *       <strong>Jamais</strong> renvoyée par l'API. Nettoyée par la
 *       tâche de réconciliation si elle reste trop longtemps ainsi
 *       (crash entre le commit et le déplacement du fichier).</li>
 *   <li>{@link #STORED} : fichier en place, pièce visible et
 *       téléchargeable.</li>
 *   <li>{@link #DELETED} : suppression logique. Le fichier est retiré
 *       (immédiatement en best effort, sinon par la réconciliation).</li>
 * </ul>
 */
enum JustificationAttachmentStatus {
    PENDING_STORAGE,
    STORED,
    DELETED
}
