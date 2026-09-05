package com.esic.connect.document.internal;

/**
 * Neutralisation de l'injection de formule (AC-032, docs/02 §22.4).
 *
 * <p>Une cellule qui commence par {@code =}, {@code +}, {@code -},
 * {@code @}, une tabulation ou un retour chariot est interprétée comme
 * une <strong>formule</strong> par les tableurs. Un rapport d'assiduité
 * contenant un nom déposé comme {@code =cmd|'…'!A1} devient alors une
 * exécution de commande chez la personne qui l'ouvre.
 *
 * <p>La neutralisation s'applique au CSV <em>et</em> au classeur
 * {@code .xlsx} : le vecteur n'est pas le format de fichier, c'est le
 * tableur qui l'ouvre. Écrire la cellule comme texte ne suffit pas —
 * Excel ré-interprète une chaîne commençant par {@code =} lors d'un
 * copier-coller ou d'une conversion.
 */
final class CellSafety {

    private CellSafety() {
    }

    static String neutralize(String raw) {
        String value = raw == null ? "" : raw;
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }
}
