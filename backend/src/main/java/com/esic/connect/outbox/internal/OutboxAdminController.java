package com.esic.connect.outbox.internal;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * File d'échec des effets de bord (EF-OPS-005 ; docs/02 §34.2, écrans du
 * super administrateur : « file d'échec des effets de bord »).
 *
 * <p>Réservé à l'administration technique et fonctionnelle. Rejouer un
 * effet de bord peut envoyer un courriel ou créer une notification : ce
 * n'est pas une lecture, et cela ne relève ni d'un responsable
 * pédagogique, ni de l'administration scolaire.
 */
@RestController
@RequestMapping("/api/v1/outbox/messages")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
class OutboxAdminController {

    private final OutboxAdminService service;

    OutboxAdminController(OutboxAdminService service) {
        this.service = service;
    }

    @GetMapping
    OutboxResponses.MessagePage list(@RequestParam(required = false) String status,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return service.list(status, page, size);
    }

    @GetMapping("/summary")
    OutboxResponses.Summary summary() {
        return service.summary();
    }

    @PostMapping("/{publicId}/replay")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void replay(@PathVariable String publicId) {
        service.replay(publicId);
    }
}
