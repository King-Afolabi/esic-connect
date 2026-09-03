package com.esic.connect.shared.captcha;

/**
 * Protection anti-robot des formulaires exposés (EF-AUTH-011,
 * docs/02-cahier-des-charges.md §17.9).
 *
 * <p><strong>Port.</strong> Le produit fonctionne intégralement sans
 * fournisseur externe : un adaptateur local prend le relais quand aucune
 * clé n'est configurée (docs/02 §28.1). Le choix se fait par
 * configuration, jamais par modification de code.
 *
 * <p><strong>Règle absolue.</strong> La vérification est faite
 * <em>côté serveur</em>. Un widget affiché par le front sans validation
 * serveur n'est pas une protection : il suffit de ne pas l'exécuter.
 */
public interface CaptchaVerifier {

    /**
     * Vérifie un jeton produit par le widget.
     *
     * @param token        jeton transmis par le client ; peut être nul
     * @param clientOrigin origine réseau de l'appel, transmise au
     *                     fournisseur pour son propre contrôle. Utilisée
     *                     pendant la décision uniquement, jamais conservée
     *                     (docs/02 §16.7)
     * @return le verdict ; jamais {@code null}
     */
    CaptchaVerdict verify(String token, String clientOrigin);

    /**
     * Une protection réelle est-elle active ? Permet à l'interface de
     * n'afficher le widget que lorsqu'il sert à quelque chose, et aux
     * tests de savoir dans quel mode ils tournent.
     */
    boolean isEnforced();
}
