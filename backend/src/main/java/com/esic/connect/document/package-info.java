/**
 * Module « document » — production des documents de restitution
 * (docs/02 §22.4 ; EF-REP-004, EF-REP-005, EF-REP-006).
 *
 * <p>Ce module ne connaît <strong>aucun métier</strong> : il reçoit un
 * {@link com.esic.connect.document.TabularDocument} — un titre, une
 * période, des faits, un en-tête et des lignes déjà rendues en texte —
 * et le restitue en CSV, en classeur {@code .xlsx} ou en PDF. Il ne lit
 * aucune table, n'applique aucune autorisation et ne décide jamais
 * <em>quoi</em> montrer : c'est l'appelant qui a déjà filtré selon son
 * périmètre.
 *
 * <p>Deux garanties transverses justifient de centraliser ce travail
 * plutôt que de le recopier dans chaque module producteur :
 * <ul>
 *   <li>la <strong>neutralisation de l'injection de formule</strong>
 *       (AC-032) doit s'appliquer au CSV <em>comme</em> au classeur
 *       Excel — une cellule ouverte dans un tableur exécute la formule
 *       quel que soit le format d'origine ;</li>
 *   <li>l'<strong>identité d'un document officiel</strong> (AC-033) —
 *       identifiant vérifiable, émetteur, auteur, date, mention de
 *       document électronique — doit être présente sur toute page PDF,
 *       et pas seulement sur celles où l'auteur y a pensé.</li>
 * </ul>
 *
 * <p>Aucune donnée personnelle n'est ajoutée ici : ce module écrit
 * exactement ce qu'on lui donne.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Documents")
package com.esic.connect.document;
