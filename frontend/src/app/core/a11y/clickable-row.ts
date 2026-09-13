import { Directive, ElementRef, HostBinding, HostListener, Input, inject } from '@angular/core';

/**
 * Éléments qui gèrent déjà leur propre activation : un clic ou une touche
 * qui en provient (ou d'un de leurs descendants) ne doit jamais déclencher
 * la navigation de ligne, sous peine de double navigation ou de casser
 * leur propre comportement (cocher une case, ouvrir un menu…).
 */
const INTERACTIVE_SELECTOR =
  'a, button, input, select, textarea, mat-checkbox, ' +
  '[role="button"], [role="link"], [role="checkbox"], [role="menuitem"], ' +
  '[role="menuitemcheckbox"], [role="switch"], [role="tab"]';

/**
 * Ligne de tableau cliquable (Lot 2) — mécanisme partagé minimal.
 *
 * <p>La ligne délègue au <strong>lien réel</strong> déjà présent dans une
 * de ses cellules (« Consulter », « Ouvrir »…) : ce lien reste l'unique
 * source de vérité de la destination et l'élément focusable de référence
 * pour un lecteur d'écran. La directive n'ajoute qu'une zone de clic /
 * d'activation clavier étendue à toute la ligne — jamais une destination
 * dupliquée.
 *
 * <p>Un clic ou une touche (Entrée / Espace) provenant d'un élément
 * interactif de la ligne (lien, bouton, case à cocher, champ de saisie…)
 * n'est jamais intercepté : l'élément agit seul, sans double navigation.
 *
 * <p>Réservée aux tableaux où une preuve d'usage réel a été établie (Lot
 * 2) : ne pas l'appliquer ailleurs sans preuve équivalente.
 *
 * Usage :
 * ```html
 * <tr mat-row *matRowDef="let row; columns: cols" appClickableRow></tr>
 * ```
 */
@Directive({
  selector: '[appClickableRow]',
  standalone: true,
})
export class ClickableRow {
  private readonly host = inject(ElementRef<HTMLElement>);

  /**
   * Désactive la navigation de ligne sans retirer la directive — utile
   * pour un mode sélection en masse, où le clic doit rester réservé à la
   * case à cocher (Lot 2 : « ne pas perturber le mode bulk »).
   */
  @Input('appClickableRowDisabled') disabled = false;

  @HostBinding('class.esic-clickable-row')
  get clickableClass(): boolean {
    return !this.disabled;
  }

  @HostBinding('attr.tabindex')
  get tabIndex(): string | null {
    return this.disabled ? null : '0';
  }

  @HostListener('click', ['$event'])
  onClick(event: MouseEvent): void {
    if (this.disabled || this.originatesFromInteractiveElement(event)) {
      return;
    }
    this.activate();
  }

  @HostListener('keydown.enter', ['$event'])
  onEnter(event: Event): void {
    this.onKeydown(event);
  }

  @HostListener('keydown.space', ['$event'])
  onSpace(event: Event): void {
    this.onKeydown(event);
  }

  private onKeydown(event: Event): void {
    if (this.disabled || this.originatesFromInteractiveElement(event)) {
      return;
    }
    // Espace ferait défiler la page sur un élément non interactif par
    // défaut : on l'empêche puisque la ligne vient de s'activer.
    event.preventDefault();
    this.activate();
  }

  private originatesFromInteractiveElement(event: Event): boolean {
    const target = event.target as HTMLElement | null;
    return !!target?.closest(INTERACTIVE_SELECTOR);
  }

  private activate(): void {
    const link = this.host.nativeElement.querySelector('a[href]') as HTMLAnchorElement | null;
    link?.click();
  }
}
