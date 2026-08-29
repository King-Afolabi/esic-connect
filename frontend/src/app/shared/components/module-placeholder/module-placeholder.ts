import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

/**
 * Écran d'attente pour un module dont la route et les autorisations sont
 * déjà en place mais dont l'interface reste à livrer. Le contenu est
 * fourni par les `data` de la route (liées via `withComponentInputBinding`).
 */
@Component({
  selector: 'app-module-placeholder',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatCardModule, MatIconModule],
  templateUrl: './module-placeholder.html',
  styleUrl: './module-placeholder.scss',
})
export class ModulePlaceholder {
  readonly pageTitle = input('Module');
  readonly pageDescription = input('');
  readonly docReference = input('');
}
