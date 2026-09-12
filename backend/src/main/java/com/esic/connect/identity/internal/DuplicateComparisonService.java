package com.esic.connect.identity.internal;

import com.esic.connect.identity.DuplicateDependencyContributor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Comparaison contrôlée de deux comptes signalés comme doublons
 * (ANO-USER-001 ; docs/02 §9.5).
 *
 * <p><strong>Strictement en lecture seule.</strong> La transaction est
 * {@code readOnly} ; le service ne charge que des comptes et des
 * décomptes, n'appelle aucun émetteur d'événement, aucune outbox, aucun
 * journal d'audit, aucune notification, et ne touche jamais
 * {@code updated_at}. Il ne fusionne rien : aucune route de fusion
 * n'existe, et la suppression d'un doublon reste une action humaine,
 * exceptionnelle et doublement confirmée (docs/02 §9.5). Le verdict
 * {@link DuplicateComparisonWeb.Assessment} est <em>informatif</em> — il
 * oriente la revue, il ne l'exécute pas.
 *
 * <p>Le volume de données rattaché de part et d'autre
 * ({@code enrollments}, {@code attendanceRecords}, {@code justifications},
 * {@code claims}, {@code notifications}…) est remonté par les
 * {@link DuplicateDependencyContributor} publiés par les autres modules :
 * {@code identity} ne peut pas les interroger directement sans créer un
 * cycle. Deux invocations par comparaison (une par compte) : le coût est
 * borné (NFR-PERF-08).
 */
@Service
class DuplicateComparisonService {

    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final AccountInvitationRepository invitationRepository;
    private final WebAuthnCredentialRepository webAuthnCredentialRepository;
    private final TrustedDeviceRepository trustedDeviceRepository;
    private final MfaCredentialRepository mfaCredentialRepository;
    private final List<DuplicateDependencyContributor> contributors;

    DuplicateComparisonService(UserAccountRepository userAccountRepository,
                               UserRoleRepository userRoleRepository,
                               AccountInvitationRepository invitationRepository,
                               WebAuthnCredentialRepository webAuthnCredentialRepository,
                               TrustedDeviceRepository trustedDeviceRepository,
                               MfaCredentialRepository mfaCredentialRepository,
                               List<DuplicateDependencyContributor> contributors) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.invitationRepository = invitationRepository;
        this.webAuthnCredentialRepository = webAuthnCredentialRepository;
        this.trustedDeviceRepository = trustedDeviceRepository;
        this.mfaCredentialRepository = mfaCredentialRepository;
        this.contributors = contributors;
    }

    @Transactional(readOnly = true)
    DuplicateComparisonWeb.ComparisonResponse compare(UUID firstPublicId, UUID secondPublicId) {
        if (firstPublicId.equals(secondPublicId)) {
            // Comparer un compte avec lui-même n'a pas de sens : erreur
            // d'appel du client, pas un 500 (docs/02 §30.1).
            throw new UserManagementException(UserManagementException.Kind.SAME_USER);
        }

        UserAccount firstAccount = userAccountRepository.findByPublicId(firstPublicId)
                .orElseThrow(() -> new UserManagementException(
                        UserManagementException.Kind.USER_NOT_FOUND));
        UserAccount secondAccount = userAccountRepository.findByPublicId(secondPublicId)
                .orElseThrow(() -> new UserManagementException(
                        UserManagementException.Kind.USER_NOT_FOUND));

        Snapshot first = snapshot(firstAccount);
        Snapshot second = snapshot(secondAccount);

        List<String> matching = matchingFields(first, second);
        List<String> different = differentFields(first, second);
        List<DuplicateComparisonWeb.Note> conflicts = conflicts(first, second);
        List<DuplicateComparisonWeb.Note> warnings = warnings(first, second);
        List<String> consequences = consequences(first, second);

        Map<String, Long> dependencySummary = new LinkedHashMap<>();
        mergeCounts(dependencySummary, first.dependencyCounts);
        mergeCounts(dependencySummary, second.dependencyCounts);

        List<String> reasons = new ArrayList<>();
        DuplicateComparisonWeb.Assessment assessment = assess(first, second, conflicts, warnings, reasons);

        return new DuplicateComparisonWeb.ComparisonResponse(
                first.toSide(), second.toSide(), matching, different, conflicts, warnings,
                consequences, dependencySummary, assessment, reasons);
    }

    // ------------------------------------------------------------------
    // Chargement d'un compte
    // ------------------------------------------------------------------

    private Snapshot snapshot(UserAccount account) {
        Long id = account.getId();
        List<String> roles = userRoleRepository.findActiveRoleCodesByUserId(id).stream()
                .map(Enum::name).sorted().toList();

        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, String> attributes = new LinkedHashMap<>();
        for (DuplicateDependencyContributor contributor : contributors) {
            mergeCounts(counts, contributor.countsFor(id));
            contributor.attributesFor(id).forEach(attributes::putIfAbsent);
        }

        // Décomptes que porte identity lui-même.
        counts.put("invitations", invitationRepository.countByUserId(id));
        counts.put("passkeys", webAuthnCredentialRepository.countByUserIdAndStatus(
                id, WebAuthnCredentialStatus.ACTIVE));
        counts.put("trustedDevices", trustedDeviceRepository.countByUserIdAndStatus(
                id, TrustedDeviceStatus.ACTIVE));
        counts.put("activeRoles", (long) roles.size());

        boolean mfaConfigured = mfaCredentialRepository.existsByUserIdAndStatus(
                id, MfaCredentialStatus.ACTIVE);

        return new Snapshot(account, roles, counts, attributes, mfaConfigured);
    }

    private record Snapshot(UserAccount account, List<String> roles, Map<String, Long> dependencyCounts,
                            Map<String, String> attributes, boolean mfaConfigured) {

        /** Refonte 2026-09 : porté directement par {@code user_account}, plus par un {@code student_profile}. */
        String studentNumber() {
            return account.getStudentNumber();
        }

        /** Le rôle {@code STUDENT} est l'unique source de vérité du statut apprenant (refonte 2026-09). */
        boolean isStudent() {
            return roles.contains("STUDENT");
        }

        boolean hasActiveEnrollment() {
            return count("activeEnrollments") > 0;
        }

        boolean hasLoginCredential() {
            return account.getPasswordHash() != null || count("passkeys") > 0;
        }

        boolean isTeacherLike() {
            return roles.contains("TEACHER") || roles.contains("PEDAGOGICAL_MANAGER");
        }

        long count(String key) {
            return dependencyCounts.getOrDefault(key, 0L);
        }

        long totalDependencies() {
            return dependencyCounts.values().stream().mapToLong(Long::longValue).sum();
        }

        DuplicateComparisonWeb.ComparisonSide toSide() {
            return new DuplicateComparisonWeb.ComparisonSide(
                    account.getPublicId(),
                    (account.getFirstName() + " " + account.getLastName()).trim(),
                    account.getEmail(),
                    account.getPhone(),
                    account.getStatus().name(),
                    roles,
                    isStudent(),
                    studentNumber(),
                    hasActiveEnrollment(),
                    hasLoginCredential(),
                    mfaConfigured,
                    count("passkeys"),
                    count("trustedDevices"),
                    account.getCreatedAt(),
                    account.getLastLoginAt());
        }
    }

    // ------------------------------------------------------------------
    // Critères de comparaison
    // ------------------------------------------------------------------

    private List<String> matchingFields(Snapshot a, Snapshot b) {
        List<String> fields = new ArrayList<>();
        if (equalsIgnoreCaseTrimmed(a.account.getEmail(), b.account.getEmail())) {
            fields.add("email");
        }
        if (normalizedName(a.account) != null
                && normalizedName(a.account).equals(normalizedName(b.account))) {
            fields.add("normalizedName");
        }
        if (normalizedPhone(a.account) != null
                && normalizedPhone(a.account).equals(normalizedPhone(b.account))) {
            fields.add("phone");
        }
        if (a.studentNumber() != null
                && a.studentNumber().equalsIgnoreCase(b.studentNumber())) {
            fields.add("studentNumber");
        }
        if (a.account.getStatus() == b.account.getStatus()) {
            fields.add("status");
        }
        return fields;
    }

    private List<String> differentFields(Snapshot a, Snapshot b) {
        List<String> fields = new ArrayList<>();
        if (!equalsIgnoreCaseTrimmed(a.account.getFirstName(), b.account.getFirstName())
                || !equalsIgnoreCaseTrimmed(a.account.getLastName(), b.account.getLastName())) {
            fields.add("name");
        }
        if (!equalsIgnoreCaseTrimmed(a.account.getEmail(), b.account.getEmail())) {
            fields.add("email");
        }
        if (!java.util.Objects.equals(normalizedPhone(a.account), normalizedPhone(b.account))) {
            fields.add("phone");
        }
        if (a.account.getStatus() != b.account.getStatus()) {
            fields.add("status");
        }
        if (!a.roles.equals(b.roles)) {
            fields.add("roles");
        }
        if (a.isStudent() != b.isStudent()) {
            fields.add("isStudent");
        }
        if (!java.util.Objects.equals(nullSafe(a.studentNumber()), nullSafe(b.studentNumber()))) {
            fields.add("studentNumber");
        }
        if (a.hasActiveEnrollment() != b.hasActiveEnrollment()) {
            fields.add("activeEnrollment");
        }
        return fields;
    }

    private List<DuplicateComparisonWeb.Note> conflicts(Snapshot a, Snapshot b) {
        List<DuplicateComparisonWeb.Note> notes = new ArrayList<>();

        if (a.studentNumber() != null && b.studentNumber() != null
                && !a.studentNumber().equalsIgnoreCase(b.studentNumber())) {
            notes.add(new DuplicateComparisonWeb.Note("DIFFERENT_STUDENT_NUMBERS",
                    "Les deux comptes portent un numéro étudiant distinct et valide : "
                            + "ce sont deux inscriptions différentes."));
        }
        if (a.hasActiveEnrollment() && b.hasActiveEnrollment()) {
            notes.add(new DuplicateComparisonWeb.Note("BOTH_HAVE_ACTIVE_ENROLLMENT",
                    "Chaque compte a une inscription active : un apprenant n'appartient "
                            + "qu'à une seule classe principale active (RG-022)."));
        }
        boolean identityMatch = matchingFields(a, b).contains("normalizedName")
                || matchingFields(a, b).contains("phone")
                || matchingFields(a, b).contains("email");
        if (!identityMatch) {
            notes.add(new DuplicateComparisonWeb.Note("DIFFERENT_IDENTITY",
                    "Ni le nom normalisé, ni le téléphone, ni l'adresse ne concordent : "
                            + "rien n'indique qu'il s'agisse de la même personne."));
        }
        boolean studentVsTeacher =
                (a.isStudent() && b.isTeacherLike() && !b.isStudent())
                        || (b.isStudent() && a.isTeacherLike() && !a.isStudent());
        if (studentVsTeacher) {
            notes.add(new DuplicateComparisonWeb.Note("INCOMPATIBLE_PROFILES",
                    "Un compte porte le rôle apprenant, l'autre un rôle d'intervenant pédagogique : "
                            + "comptes non rattachables automatiquement."));
        }
        return notes;
    }

    private List<DuplicateComparisonWeb.Note> warnings(Snapshot a, Snapshot b) {
        List<DuplicateComparisonWeb.Note> notes = new ArrayList<>();
        if (!equalsIgnoreCaseTrimmed(a.account.getEmail(), b.account.getEmail())) {
            notes.add(new DuplicateComparisonWeb.Note("DIFFERENT_EMAILS",
                    "Les deux comptes ont une adresse électronique différente."));
        }
        if (!a.roles.isEmpty() && !b.roles.isEmpty() && !a.roles.equals(b.roles)) {
            notes.add(new DuplicateComparisonWeb.Note("DIFFERENT_ROLES",
                    "Les rôles actifs diffèrent : " + a.roles + " vs " + b.roles + "."));
        }
        boolean oneArchived = a.account.getStatus() == AccountStatus.ARCHIVED
                ^ b.account.getStatus() == AccountStatus.ARCHIVED;
        if (oneArchived) {
            notes.add(new DuplicateComparisonWeb.Note("ONE_ARCHIVED",
                    "Un compte est archivé, l'autre non."));
        }
        if (a.hasLoginCredential() && b.hasLoginCredential()) {
            notes.add(new DuplicateComparisonWeb.Note("CREDENTIALS_ON_BOTH",
                    "Les deux comptes ont déjà servi à se connecter (mot de passe ou passkey)."));
        }
        if ((a.count("passkeys") > 0 || a.count("trustedDevices") > 0)
                && (b.count("passkeys") > 0 || b.count("trustedDevices") > 0)) {
            notes.add(new DuplicateComparisonWeb.Note("DEVICES_ON_BOTH",
                    "Passkeys ou appareils de confiance présents sur les deux comptes."));
        }
        boolean historyOnBoth = (a.count("attendanceRecords") > 0 || a.count("enrollments") > 0)
                && (b.count("attendanceRecords") > 0 || b.count("enrollments") > 0);
        if (historyOnBoth) {
            notes.add(new DuplicateComparisonWeb.Note("PEDAGOGICAL_HISTORY_ON_BOTH",
                    "Chaque compte porte un historique pédagogique (inscriptions ou présences)."));
        }
        return notes;
    }

    private List<String> consequences(Snapshot a, Snapshot b) {
        // Le compte « secondaire » présumé est celui qui porte le moins
        // d'historique — c'est purement indicatif, aucune fusion n'a lieu.
        Snapshot lighter = a.totalDependencies() <= b.totalDependencies() ? a : b;
        Snapshot heavier = lighter == a ? b : a;
        List<String> lines = new ArrayList<>();
        long moved = lighter.totalDependencies();
        if (moved == 0) {
            lines.add("Le compte " + lighter.account.getPublicId()
                    + " ne porte aucune donnée dépendante : un rattachement au compte "
                    + heavier.account.getPublicId() + " serait sans reprise d'historique.");
        } else {
            lines.add("Une fusion devrait rattacher " + moved
                    + " ligne(s) dépendante(s) du compte " + lighter.account.getPublicId()
                    + " vers le compte " + heavier.account.getPublicId() + ".");
        }
        if (a.hasLoginCredential() && b.hasLoginCredential()) {
            lines.add("Un seul jeu d'identifiants de connexion pourrait subsister : "
                    + "l'autre devrait être révoqué explicitement.");
        }
        lines.add("Aucune fusion n'est réalisée par ce parcours : la décision et "
                + "l'exécution restent manuelles (docs/02 §9.5).");
        return lines;
    }

    private DuplicateComparisonWeb.Assessment assess(Snapshot a, Snapshot b,
                                                     List<DuplicateComparisonWeb.Note> conflicts,
                                                     List<DuplicateComparisonWeb.Note> warnings,
                                                     List<String> reasons) {
        if (!conflicts.isEmpty()) {
            conflicts.forEach(note -> reasons.add(note.detail()));
            return DuplicateComparisonWeb.Assessment.NOT_MERGEABLE;
        }
        boolean identityMatch = matchingFields(a, b).contains("normalizedName")
                || matchingFields(a, b).contains("phone");
        boolean historyConcentrated = a.totalDependencies() == 0 || b.totalDependencies() == 0;
        // Une adresse différente est <em>attendue</em> pour tout doublon
        // (RG-001 interdit l'identique) : elle ne bloque pas à elle seule.
        boolean materialWarning = warnings.stream()
                .anyMatch(note -> !"DIFFERENT_EMAILS".equals(note.code()));
        if (identityMatch && !materialWarning && historyConcentrated) {
            reasons.add("Identité concordante, aucun conflit, historique porté par un seul compte ; "
                    + "seule l'adresse électronique diffère (attendu pour un doublon).");
            return DuplicateComparisonWeb.Assessment.POTENTIALLY_SAFE;
        }
        if (!warnings.isEmpty()) {
            warnings.forEach(note -> reasons.add(note.detail()));
        }
        if (!historyConcentrated) {
            reasons.add("Les deux comptes portent des données dépendantes : "
                    + "un rattachement automatique n'est pas sûr.");
        }
        if (reasons.isEmpty()) {
            reasons.add("Aucun conflit bloquant, mais la concordance d'identité n'est pas "
                    + "assez forte pour conclure sans revue.");
        }
        return DuplicateComparisonWeb.Assessment.MANUAL_REVIEW_REQUIRED;
    }

    // ------------------------------------------------------------------
    // Normalisation (alignée sur BulkUserService.findDuplicates)
    // ------------------------------------------------------------------

    private static void mergeCounts(Map<String, Long> target, Map<String, Long> source) {
        if (source == null) {
            return;
        }
        source.forEach((key, value) ->
                target.merge(key, value == null ? 0L : value, Long::sum));
    }

    private static boolean equalsIgnoreCaseTrimmed(String left, String right) {
        return nullSafe(left).equalsIgnoreCase(nullSafe(right));
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizedName(UserAccount account) {
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
        return digits.length() >= 9 ? digits : null;
    }
}
