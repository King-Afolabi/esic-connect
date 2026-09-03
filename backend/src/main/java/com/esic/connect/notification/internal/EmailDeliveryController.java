package com.esic.connect.notification.internal;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Journal de délivrabilité des courriels (EF-USER-008 ; docs/02 §11.3).
 *
 * <p>Réservé aux rôles qui émettent des invitations et doivent corriger
 * une adresse erronée. Aucune adresse en clair n'est renvoyée : seule la
 * forme masquée, suffisante pour reconnaître une faute de frappe.
 */
@RestController
@RequestMapping("/api/v1/email-deliveries")
class EmailDeliveryController {

    private static final String READ_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER')";

    private final EmailDeliveryService service;

    EmailDeliveryController(EmailDeliveryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    EmailDeliveryResponses.Page list(@RequestParam(required = false) String internalStatus,
                                     @RequestParam(required = false) String providerStatus,
                                     @RequestParam(required = false) String messageType,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return service.list(internalStatus, providerStatus, messageType, page, size);
    }
}
