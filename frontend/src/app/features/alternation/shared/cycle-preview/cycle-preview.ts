import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import {
  CanonicalPatternConfiguration,
  WEEKDAY_SHORT_LABELS,
  Weekday,
  alternationContextLabel,
} from '../../alternation.models';
import { CyclePreviewWeek, buildCyclePreview } from '../../pattern-config';

/**
 * Prévisualisation **accessible** d'un modèle de rythme : une grille
 * semaine × jour qui *représente* la configuration canonique stockée.
 *
 * Ce n'est pas une résolution de contexte : elle n'utilise ni ancre de
 * cycle, ni date, ni modulo. Le contexte réel d'une date donnée provient
 * uniquement des endpoints de contexte du back-end
 * (`/classes/{id}/context`, `/enrollments/{id}/context`).
 *
 * Accessibilité : rendu sous forme de `<table>` avec `<caption>` et
 * en-têtes de ligne/colonne ; l'information n'est jamais portée par la
 * seule couleur (chaque cellule porte un libellé texte).
 */
@Component({
  selector: 'app-cycle-preview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cycle-preview.html',
  styleUrl: './cycle-preview.scss',
})
export class CyclePreview {
  readonly config = input.required<CanonicalPatternConfiguration>();

  protected readonly contextLabel = alternationContextLabel;
  protected readonly dayHeaders: readonly { day: Weekday; label: string }[] = (
    Object.keys(WEEKDAY_SHORT_LABELS) as Weekday[]
  ).map((day) => ({ day, label: WEEKDAY_SHORT_LABELS[day] }));

  protected readonly weeks = computed<CyclePreviewWeek[]>(() => buildCyclePreview(this.config()));
}
