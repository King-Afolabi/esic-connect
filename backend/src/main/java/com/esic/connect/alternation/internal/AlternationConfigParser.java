package com.esic.connect.alternation.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Composant métier <em>pur</em> (aucune I/O, aucun accès base) qui lit et
 * valide {@code configuration_json} d'un modèle de rythme, puis produit
 * une {@link PatternConfiguration} normalisée. Toute propriété inconnue,
 * tout jour inconnu, toute incohérence (semaines / jours / intersections)
 * lève {@link AlternationException.Kind#INVALID_CONFIGURATION} avec un
 * message non sensible : jamais d'acceptation silencieuse (section 4 du
 * lot).
 *
 * <p>Le contrat JSON par type — noms de propriétés alignés autant que
 * possible sur l'exemple de docs/04 §14.1, {@code cycleStartDate} exclu
 * (il est propre à l'affectation de classe) :
 * <ul>
 *   <li><b>THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY</b> :
 *       {@code {"schoolDays":[...],"companyDays":[...]}} — jours MON..FRI
 *       classifiés exactement une fois, aucun jour dans les deux ensembles,
 *       aucun nom inconnu ; {@code cycleLengthWeeks} absent ou égal à 1
 *       (normalisé à 1) ;</li>
 *   <li><b>ONE_WEEK_SCHOOL_OUT_OF_FOUR</b> :
 *       {@code {"schoolWeeks":[1],"companyWeeks":[2,3,4]}} —
 *       {@code cycleLengthWeeks} absent ou égal à 4 ; exactement une
 *       semaine école, les trois autres en entreprise ; {@code schoolDays}
 *       facultatif (défaut MON..FRI) ;</li>
 *   <li><b>TWO_WEEKS_SCHOOL_OUT_OF_FOUR</b> :
 *       {@code {"schoolWeeks":[1,2],"companyWeeks":[3,4]}} — exactement
 *       deux semaines école, les deux autres en entreprise ;</li>
 *   <li><b>CUSTOM</b> :
 *       {@code {"cycleLengthWeeks":N,"schoolWeeks":[...],"companyWeeks":[...],
 *       "schoolDays":[...],"companyDays":[...]}} — {@code cycleLengthWeeks}
 *       obligatoire et strictement positif (doit correspondre à la colonne
 *       s'il est aussi fourni là) ; {@code schoolWeeks} et
 *       {@code companyWeeks} sans intersection, chaque index dans le
 *       cycle, au moins une des deux listes non vide ; les semaines non
 *       classifiées produisent {@code UNKNOWN}. {@code schoolDays} défaut
 *       MON..FRI, {@code companyDays} défaut vide.</li>
 * </ul>
 */
@Component
class AlternationConfigParser {

    private static final Set<DayOfWeek> WEEKDAYS = EnumSet.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private final ObjectMapper objectMapper;

    AlternationConfigParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param type              type de rythme demandé
     * @param cycleLengthWeeks  valeur de la colonne {@code cycle_length_weeks}
     *                          fournie par la requête ({@code null} accepté
     *                          selon le type)
     * @param configurationJson corps JSON brut
     * @return la configuration normalisée
     * @throws AlternationException {@code INVALID_CONFIGURATION} si le JSON
     *                              est illisible, incomplet, incohérent ou
     *                              contient une propriété inconnue
     */
    ParsedConfiguration parse(WorkStudyPatternType type, Integer cycleLengthWeeks, String configurationJson) {
        JsonNode root = readObject(configurationJson);
        return switch (type) {
            case THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY -> parseThreeTwo(root, cycleLengthWeeks);
            case ONE_WEEK_SCHOOL_OUT_OF_FOUR -> parseWeeksOutOfFour(root, cycleLengthWeeks, 1);
            case TWO_WEEKS_SCHOOL_OUT_OF_FOUR -> parseWeeksOutOfFour(root, cycleLengthWeeks, 2);
            case CUSTOM -> parseCustom(root, cycleLengthWeeks);
        };
    }

    /**
     * Relit une {@link PatternConfiguration} depuis sa forme canonique
     * (les cinq clés produites par {@link #canonicalize}), sans
     * revalidation propre au type : la donnée a déjà été validée à
     * l'écriture et n'est plus fournie par un client. Sert la résolution
     * calendaire ({@code AlternationContextService}).
     */
    PatternConfiguration parseCanonical(String canonicalJson) {
        JsonNode root = readObject(canonicalJson);
        JsonNode cycleNode = root.get("cycleLengthWeeks");
        if (cycleNode == null || !cycleNode.canConvertToInt() || cycleNode.asInt() <= 0) {
            throw invalid("cycleLengthWeeks canonique invalide");
        }
        int cycle = cycleNode.asInt();
        return new PatternConfiguration(cycle,
                optionalWeeks(root, "schoolWeeks", cycle),
                optionalWeeks(root, "companyWeeks", cycle),
                root.has("schoolDays") ? requireDays(root, "schoolDays") : Set.of(),
                root.has("companyDays") ? requireDays(root, "companyDays") : Set.of());
    }

    /**
     * Réécrit la configuration normalisée en JSON canonique, pour un
     * stockage stable et une relecture déterministe.
     */
    String canonicalize(ParsedConfiguration parsed) {
        PatternConfiguration config = parsed.configuration();
        ObjectNode node = objectMapper.createObjectNode();
        node.put("cycleLengthWeeks", config.cycleLengthWeeks());
        node.set("schoolWeeks", intArray(new TreeSet<>(config.schoolWeeks())));
        node.set("companyWeeks", intArray(new TreeSet<>(config.companyWeeks())));
        node.set("schoolDays", dayArray(config.schoolDays()));
        node.set("companyDays", dayArray(config.companyDays()));
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new AlternationException(AlternationException.Kind.INVALID_CONFIGURATION,
                    "sérialisation impossible");
        }
    }

    // ------------------------------------------------------------------

    private ParsedConfiguration parseThreeTwo(JsonNode root, Integer cycleLengthWeeks) {
        rejectUnknownKeys(root, Set.of("schoolDays", "companyDays", "cycleLengthWeeks"));
        if (cycleLengthWeeks != null && cycleLengthWeeks != 1) {
            throw invalid("cycleLengthWeeks doit valoir 1 pour ce type");
        }
        if (root.has("cycleLengthWeeks") && root.get("cycleLengthWeeks").asInt(-1) != 1) {
            throw invalid("cycleLengthWeeks doit valoir 1 pour ce type");
        }
        Set<DayOfWeek> schoolDays = requireDays(root, "schoolDays");
        Set<DayOfWeek> companyDays = requireDays(root, "companyDays");
        if (!intersection(schoolDays, companyDays).isEmpty()) {
            throw invalid("un jour ne peut pas être à la fois école et entreprise");
        }
        Set<DayOfWeek> classified = new LinkedHashSet<>(schoolDays);
        classified.addAll(companyDays);
        if (!classified.containsAll(WEEKDAYS)) {
            throw invalid("tous les jours ouvrés (lundi à vendredi) doivent être classifiés");
        }
        if (!WEEKDAYS.containsAll(classified)) {
            throw invalid("seuls les jours du lundi au vendredi sont admis");
        }
        PatternConfiguration config = new PatternConfiguration(1, Set.of(1), Set.of(),
                Set.copyOf(schoolDays), Set.copyOf(companyDays));
        return new ParsedConfiguration(1, config);
    }

    private ParsedConfiguration parseWeeksOutOfFour(JsonNode root, Integer cycleLengthWeeks, int expectedSchoolWeeks) {
        rejectUnknownKeys(root, Set.of("schoolWeeks", "companyWeeks", "schoolDays", "cycleLengthWeeks"));
        if (cycleLengthWeeks != null && cycleLengthWeeks != 4) {
            throw invalid("cycleLengthWeeks doit valoir 4 pour ce type");
        }
        if (root.has("cycleLengthWeeks") && root.get("cycleLengthWeeks").asInt(-1) != 4) {
            throw invalid("cycleLengthWeeks doit valoir 4 pour ce type");
        }
        Set<Integer> schoolWeeks = requireWeeks(root, "schoolWeeks", 4);
        Set<Integer> companyWeeks = requireWeeks(root, "companyWeeks", 4);
        if (schoolWeeks.size() != expectedSchoolWeeks) {
            throw invalid("exactement " + expectedSchoolWeeks + " semaine(s) doivent être marquées école");
        }
        if (!intersection(schoolWeeks, companyWeeks).isEmpty()) {
            throw invalid("une semaine ne peut pas être à la fois école et entreprise");
        }
        Set<Integer> union = new TreeSet<>(schoolWeeks);
        union.addAll(companyWeeks);
        if (!union.equals(Set.of(1, 2, 3, 4))) {
            throw invalid("les quatre semaines du cycle doivent être classifiées");
        }
        Set<DayOfWeek> schoolDays = root.has("schoolDays") ? requireDays(root, "schoolDays") : WEEKDAYS;
        if (!WEEKDAYS.containsAll(schoolDays)) {
            throw invalid("seuls les jours du lundi au vendredi sont admis");
        }
        PatternConfiguration config = new PatternConfiguration(4, Set.copyOf(schoolWeeks),
                Set.copyOf(companyWeeks), Set.copyOf(schoolDays), Set.of());
        return new ParsedConfiguration(4, config);
    }

    private ParsedConfiguration parseCustom(JsonNode root, Integer cycleLengthWeeks) {
        rejectUnknownKeys(root, Set.of("cycleLengthWeeks", "schoolWeeks", "companyWeeks",
                "schoolDays", "companyDays"));
        int cycle = resolveCustomCycle(root, cycleLengthWeeks);
        Set<Integer> schoolWeeks = optionalWeeks(root, "schoolWeeks", cycle);
        Set<Integer> companyWeeks = optionalWeeks(root, "companyWeeks", cycle);
        if (schoolWeeks.isEmpty() && companyWeeks.isEmpty()) {
            throw invalid("au moins une période SCHOOL ou COMPANY doit être définie");
        }
        if (!intersection(schoolWeeks, companyWeeks).isEmpty()) {
            throw invalid("les listes schoolWeeks et companyWeeks ne peuvent pas se recouper");
        }
        Set<DayOfWeek> schoolDays = root.has("schoolDays") ? requireDays(root, "schoolDays") : WEEKDAYS;
        Set<DayOfWeek> companyDays = root.has("companyDays") ? requireDays(root, "companyDays") : Set.of();
        if (!WEEKDAYS.containsAll(schoolDays) || !WEEKDAYS.containsAll(companyDays)) {
            throw invalid("seuls les jours du lundi au vendredi sont admis");
        }
        if (!intersection(schoolDays, companyDays).isEmpty()) {
            throw invalid("un jour ne peut pas être à la fois école et entreprise");
        }
        PatternConfiguration config = new PatternConfiguration(cycle, Set.copyOf(schoolWeeks),
                Set.copyOf(companyWeeks), Set.copyOf(schoolDays), Set.copyOf(companyDays));
        return new ParsedConfiguration(cycle, config);
    }

    private int resolveCustomCycle(JsonNode root, Integer cycleLengthWeeks) {
        Integer fromJson = null;
        if (root.has("cycleLengthWeeks")) {
            JsonNode node = root.get("cycleLengthWeeks");
            if (!node.canConvertToInt()) {
                throw invalid("cycleLengthWeeks doit être un entier");
            }
            fromJson = node.asInt();
        }
        Integer cycle = cycleLengthWeeks != null ? cycleLengthWeeks : fromJson;
        if (cycle == null) {
            throw invalid("cycleLengthWeeks est obligatoire pour un rythme CUSTOM");
        }
        if (fromJson != null && cycleLengthWeeks != null && !fromJson.equals(cycleLengthWeeks)) {
            throw invalid("cycleLengthWeeks du corps et de la configuration divergent");
        }
        if (cycle <= 0) {
            throw invalid("cycleLengthWeeks doit être strictement positif");
        }
        return cycle;
    }

    // ------------------------------------------------------------------

    private JsonNode readObject(String json) {
        if (json == null || json.isBlank()) {
            throw invalid("configuration absente");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception notJson) {
            throw invalid("JSON illisible");
        }
        if (root == null || !root.isObject()) {
            throw invalid("un objet JSON est attendu");
        }
        return root;
    }

    private static void rejectUnknownKeys(JsonNode root, Set<String> allowed) {
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) {
                throw invalid("propriété inconnue : " + name);
            }
        }
    }

    private static Set<DayOfWeek> requireDays(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw invalid(field + " doit être une liste de jours non vide");
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (JsonNode element : node) {
            if (!element.isTextual()) {
                throw invalid(field + " ne doit contenir que des noms de jours");
            }
            DayOfWeek day;
            try {
                day = DayOfWeek.valueOf(element.asText().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                throw invalid("jour inconnu : " + element.asText());
            }
            if (!days.add(day)) {
                throw invalid(field + " contient un doublon : " + day);
            }
        }
        return days;
    }

    private static Set<Integer> requireWeeks(JsonNode root, String field, int cycle) {
        if (!root.has(field)) {
            throw invalid(field + " est obligatoire");
        }
        return optionalWeeks(root, field, cycle);
    }

    private static Set<Integer> optionalWeeks(JsonNode root, String field, int cycle) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return Set.of();
        }
        if (!node.isArray()) {
            throw invalid(field + " doit être une liste d'entiers");
        }
        Set<Integer> weeks = new TreeSet<>();
        for (JsonNode element : node) {
            if (!element.canConvertToInt()) {
                throw invalid(field + " ne doit contenir que des entiers");
            }
            int week = element.asInt();
            if (week < 1 || week > cycle) {
                throw invalid("index de semaine hors du cycle (1.." + cycle + ") : " + week);
            }
            if (!weeks.add(week)) {
                throw invalid(field + " contient un doublon : " + week);
            }
        }
        return weeks;
    }

    private static <T> Set<T> intersection(Set<T> a, Set<T> b) {
        Set<T> result = new LinkedHashSet<>(a);
        result.retainAll(b);
        return result;
    }

    private static AlternationException invalid(String detail) {
        return new AlternationException(AlternationException.Kind.INVALID_CONFIGURATION, detail);
    }

    private com.fasterxml.jackson.databind.node.ArrayNode intArray(Set<Integer> values) {
        com.fasterxml.jackson.databind.node.ArrayNode array = objectMapper.createArrayNode();
        values.forEach(array::add);
        return array;
    }

    private com.fasterxml.jackson.databind.node.ArrayNode dayArray(Set<DayOfWeek> values) {
        com.fasterxml.jackson.databind.node.ArrayNode array = objectMapper.createArrayNode();
        List<DayOfWeek> ordered = new ArrayList<>(values);
        ordered.sort(java.util.Comparator.naturalOrder());
        ordered.forEach(day -> array.add(day.name()));
        return array;
    }

    /**
     * Résultat du parsing : la longueur de cycle normalisée (à écrire dans
     * la colonne {@code cycle_length_weeks}) et la configuration validée.
     */
    record ParsedConfiguration(int normalizedCycleLengthWeeks, PatternConfiguration configuration) {
    }
}
