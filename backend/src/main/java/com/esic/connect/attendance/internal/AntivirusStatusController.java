package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.AttachmentMalwareScanner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * État réel de l'analyse antivirus des pièces jointes (dettes T-04 et
 * T-11 ; EF-JUS-002 ; docs/02 §19.2).
 *
 * <p><strong>Pourquoi exposer cet état.</strong> Le produit sait très bien
 * qu'aucun analyseur n'est joignable : chaque pièce est alors marquée
 * {@code NOT_SCANNED}. Mais un utilisateur qui dépose un justificatif —
 * ou un examinateur qui le télécharge — ne lit pas les colonnes de la
 * base. Sans cette route, l'interface ne pourrait que se taire, et un
 * silence sur une protection absente se lit comme une protection
 * présente.
 *
 * <p>Ouvert à tout compte authentifié : c'est une propriété du service,
 * pas un secret. La cacher n'empêcherait aucune attaque et priverait
 * l'utilisateur d'une information qui le concerne.
 */
@RestController
@RequestMapping("/api/v1/attendance/antivirus")
@PreAuthorize("isAuthenticated()")
class AntivirusStatusController {

    private final AttachmentMalwareScanner scanner;
    private final boolean quarantineRequired;

    AntivirusStatusController(AttachmentMalwareScanner scanner,
                              @Value("${app.attendance.antivirus.required:false}") boolean quarantineRequired) {
        this.scanner = scanner;
        this.quarantineRequired = quarantineRequired;
    }

    @GetMapping("/status")
    AntivirusStatus status() {
        return new AntivirusStatus(scanner.isActive(), quarantineRequired);
    }

    /**
     * @param active             un analyseur est réellement configuré.
     *                           {@code false} signifie qu'<strong>aucun
     *                           contrôle antivirus n'a lieu</strong> : les
     *                           pièces sont marquées {@code NOT_SCANNED} et
     *                           ne doivent jamais être présentées comme
     *                           saines.
     * @param quarantineRequired une pièce sans verdict exploitable est
     *                           retenue en quarantaine et n'est pas
     *                           téléchargeable.
     */
    record AntivirusStatus(boolean active, boolean quarantineRequired) {
    }
}
