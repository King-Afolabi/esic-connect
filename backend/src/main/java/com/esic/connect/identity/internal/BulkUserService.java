package com.esic.connect.identity.internal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Opérations de masse et détection de doublons (EF-USER-004,
 * EF-USER-005 ; docs/02 §9.4 et §9.5).
 *
 * <p><strong>Prévisualisation obligatoire.</strong> Une opération
 * groupée est refusée tant qu'elle n'est pas explicitement confirmée
 * (RG-034). L'appel non confirmé produit exactement le même calcul —
 * comptes éligibles, ignorés, refusés — mais <em>n'écrit rien</em>. C'est
 * la seule façon d'éviter qu'un clic suspende cinq cents comptes par
 * erreur.
 *
 * <p><strong>Isolation par compte, et non atomicité du lot.</strong>
 * Chaque bascule s'exécute dans sa propre transaction, celle de
 * {@link UserManagementService}. Un compte refusé — protégé, auto-action,
 * état incompatible — est reporté dans le résultat sans priver les
 * quatre cent quatre-vingt-dix-neuf autres de l'opération.
 *
 * <p>Ce choix est délibéré. Une transaction unique semblerait plus
 * propre, mais elle est en réalité fausse ici : un refus individuel
 * marquerait la transaction englobante {@code rollback-only}, et le lot
 * entier échouerait à la validation — y compris les comptes traités sans
 * problème, et sans que l'appelant comprenne pourquoi. Le contrat de
 * cette opération est précisément de <em>rendre compte compte par
 * compte</em> (docs/02 §9.4 : « les erreurs, les éléments ignorés »).
 *
 * <p><strong>Les doublons ne sont jamais supprimés ici.</strong> Le
 * service les <em>signale</em>. La suppression d'un doublon avéré reste
 * une action humaine, exceptionnelle et doublement confirmée (§9.5) ; la
 * fusion automatique détruirait de l'historique pédagogique.
 */
@Service
public class BulkUserService {

    private final UserAccountRepository userAccountRepository;
    private final UserManagementService userManagementService;
    private final AccountInvitationService invitationService;
    private final UserRoleRepository userRoleRepository;

    BulkUserService(UserAccountRepository userAccountRepository,
                    UserManagementService userManagementService,
                    AccountInvitationService invitationService,
                    UserRoleRepository userRoleRepository) {
        this.userAccountRepository = userAccountRepository;
        this.userManagementService = userManagementService;
        this.invitationService = invitationService;
        this.userRoleRepository = userRoleRepository;
    }

    /**
     * Prévisualise ou exécute une opération groupée.
     *
     * <p>Le contrôle d'autorisation de chaque compte n'est pas
     * réimplémenté ici : chaque bascule passe par
     * {@link UserManagementService}, qui porte déjà les gardes fines
     * (protection du {@code SUPER_ADMIN}, auto-action interdite, dernier
     * rôle actif). Dupliquer ces règles garantirait qu'elles divergent.
     */
    public BulkUserWeb.BulkResult execute(BulkUserWeb.BulkRequest request, String callerSubject,
                                          Collection<String> callerRoles) {
        BulkUserWeb.BulkAction action = parseAction(request.action());
        List<UUID> ids = parseIds(request.userIds());
        Map<UUID, UserAccount> accounts = userAccountRepository.findByPublicIdIn(ids).stream()
                .collect(Collectors.toMap(UserAccount::getPublicId, account -> account));

        List<BulkUserWeb.BulkOutcome> outcomes = new ArrayList<>();
        List<UserAccount> eligible = new ArrayList<>();

        for (UUID id : ids) {
            UserAccount account = accounts.get(id);
            if (account == null) {
                outcomes.add(new BulkUserWeb.BulkOutcome(id, null, "REJECTED",
                        "Aucun compte ne correspond à cet identifiant."));
                continue;
            }
            String skip = reasonToSkip(action, account);
            if (skip != null) {
                outcomes.add(new BulkUserWeb.BulkOutcome(id, account.getEmail(), "IGNORED", skip));
                continue;
            }
            outcomes.add(new BulkUserWeb.BulkOutcome(id, account.getEmail(), "ELIGIBLE",
                    "L'action sera appliquée."));
            eligible.add(account);
        }

        if (!request.isConfirmed()) {
            // Prévisualisation : aucune écriture, mais le même calcul.
            return summarize(false, action, ids.size(), outcomes);
        }

        List<BulkUserWeb.BulkOutcome> executed = new ArrayList<>();
        for (BulkUserWeb.BulkOutcome outcome : outcomes) {
            if (!"ELIGIBLE".equals(outcome.outcome())) {
                executed.add(outcome);
                continue;
            }
            try {
                apply(action, outcome.userId(), request.reason(), callerSubject, callerRoles);
                executed.add(outcome);
            } catch (UserManagementException | InvitationException refused) {
                // Un refus individuel n'annule pas le lot : il est reporté
                // tel quel. Faire échouer les 499 autres parce qu'un compte
                // est protégé serait pire que de le signaler.
                executed.add(new BulkUserWeb.BulkOutcome(outcome.userId(), outcome.email(),
                        "REJECTED", explain(refused)));
            }
        }
        return summarize(true, action, ids.size(), executed);
    }

    /**
     * Groupes de comptes soupçonnés d'être des doublons (EF-USER-005).
     *
     * <p>Deux signatures, volontairement conservatrices : un même nom
     * complet normalisé (accents et casse neutralisés), ou un même
     * numéro de téléphone. Une adresse identique est impossible —
     * l'unicité l'interdit déjà (RG-001).
     *
     * <p>Aucune fusion, aucune suppression : le service produit une
     * liste à examiner.
     */
    @Transactional(readOnly = true)
    public List<BulkUserWeb.DuplicateGroup> findDuplicates() {
        List<UserAccount> accounts = userAccountRepository.findByStatusNot(AccountStatus.ARCHIVED);

        List<BulkUserWeb.DuplicateGroup> groups = new ArrayList<>();
        groupBy(accounts, account -> normalizedFullName(account))
                .forEach((signature, members) -> groups.add(new BulkUserWeb.DuplicateGroup(
                        signature, "Même nom et prénom, à la casse et aux accents près.",
                        members.stream().map(BulkUserService::toCandidate).toList())));
        groupBy(accounts, account -> normalizedPhone(account))
                .forEach((signature, members) -> groups.add(new BulkUserWeb.DuplicateGroup(
                        signature, "Même numéro de téléphone.",
                        members.stream().map(BulkUserService::toCandidate).toList())));
        return groups;
    }

    // ------------------------------------------------------------------

    private void apply(BulkUserWeb.BulkAction action, UUID userId, String reason,
                       String callerSubject, Collection<String> callerRoles) {
        switch (action) {
            case SUSPEND -> userManagementService.suspend(userId, reason, callerSubject, callerRoles);
            case RESTORE -> userManagementService.restore(userId, reason, callerSubject, callerRoles);
            case ARCHIVE -> userManagementService.archive(userId, reason, callerSubject, callerRoles);
            case RESEND_INVITATION -> resendInvitation(userId, callerSubject);
        }
    }

    private void resendInvitation(UUID userId, String callerSubject) {
        UserAccount account = userAccountRepository.findByPublicId(userId)
                .orElseThrow(() -> new UserManagementException(
                        UserManagementException.Kind.USER_NOT_FOUND));
        invitationService.issue(account.getEmail(), primaryRoleOf(account), callerSubject);
    }

    /**
     * Rôle à réattribuer lors d'une réémission groupée : celui que le
     * compte détient déjà. Réémettre ne doit jamais changer les droits.
     */
    private RoleCode primaryRoleOf(UserAccount account) {
        return userRoleRepository.findActiveRoleCodesByUserId(account.getId()).stream()
                .findFirst()
                .orElse(RoleCode.STUDENT);
    }

    /** @return le motif d'omission, ou {@code null} si l'action s'applique */
    private String reasonToSkip(BulkUserWeb.BulkAction action, UserAccount account) {
        return switch (action) {
            case SUSPEND -> account.getStatus() == AccountStatus.SUSPENDED
                    ? "Ce compte est déjà suspendu." : null;
            case RESTORE -> account.getStatus() == AccountStatus.ACTIVE
                    ? "Ce compte est déjà actif." : null;
            case ARCHIVE -> account.getStatus() == AccountStatus.ARCHIVED
                    ? "Ce compte est déjà archivé." : null;
            case RESEND_INVITATION -> account.getStatus() != AccountStatus.PENDING_ACTIVATION
                    ? "Ce compte n'est plus en attente d'activation." : null;
        };
    }

    private static BulkUserWeb.BulkResult summarize(boolean applied, BulkUserWeb.BulkAction action,
                                                    int requested,
                                                    List<BulkUserWeb.BulkOutcome> outcomes) {
        int eligible = (int) outcomes.stream().filter(o -> "ELIGIBLE".equals(o.outcome())).count();
        int ignored = (int) outcomes.stream().filter(o -> "IGNORED".equals(o.outcome())).count();
        int rejected = (int) outcomes.stream().filter(o -> "REJECTED".equals(o.outcome())).count();
        return new BulkUserWeb.BulkResult(applied, action.name(), requested, eligible, ignored,
                rejected, outcomes);
    }

    private static BulkUserWeb.BulkAction parseAction(String raw) {
        try {
            return BulkUserWeb.BulkAction.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new UserManagementException(UserManagementException.Kind.INVALID_FILTER);
        }
    }

    private static List<UUID> parseIds(List<String> raw) {
        return raw.stream().map(value -> {
            try {
                return UUID.fromString(value.trim());
            } catch (IllegalArgumentException notAUuid) {
                throw new UserManagementException(UserManagementException.Kind.USER_NOT_FOUND);
            }
        }).distinct().toList();
    }

    private static String explain(RuntimeException refused) {
        if (refused instanceof UserManagementException managementFailure) {
            return switch (managementFailure.kind()) {
                case SUPER_ADMIN_PROTECTED -> "Compte super administrateur : action refusée.";
                case SELF_ACTION_FORBIDDEN -> "Vous ne pouvez pas appliquer cette action à votre propre compte.";
                case INVALID_STATE_TRANSITION -> "L'état du compte ne permet pas cette action.";
                case NOT_AUTHORIZED -> "Vous n'êtes pas autorisé à agir sur ce compte.";
                default -> "Action refusée.";
            };
        }
        return "Action refusée.";
    }

    private static Map<String, List<UserAccount>> groupBy(List<UserAccount> accounts,
                                                          java.util.function.Function<UserAccount, String> key) {
        return accounts.stream()
                .filter(account -> key.apply(account) != null)
                .collect(Collectors.groupingBy(key))
                .entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .sorted(Comparator.comparing(UserAccount::getEmail))
                                .toList()));
    }

    /** Nom complet normalisé : accents retirés, casse et espaces neutralisés. */
    private static String normalizedFullName(UserAccount account) {
        String raw = (account.getFirstName() + " " + account.getLastName()).trim();
        if (raw.isBlank()) {
            return null;
        }
        String withoutAccents = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return withoutAccents.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String normalizedPhone(UserAccount account) {
        String phone = account.getPhone();
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        // Moins de neuf chiffres : trop court pour identifier qui que ce soit.
        return digits.length() >= 9 ? digits : null;
    }

    private static BulkUserWeb.DuplicateCandidate toCandidate(UserAccount account) {
        return new BulkUserWeb.DuplicateCandidate(account.getPublicId(), account.getEmail(),
                account.getFirstName(), account.getLastName(), account.getStatus().name(),
                account.getCreatedAt());
    }
}
