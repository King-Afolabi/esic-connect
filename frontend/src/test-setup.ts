/**
 * Amorces globales des tests unitaires (exécutées après le TestBed
 * Angular, avant les fichiers de spécification).
 *
 * jsdom n'implémente pas `matchMedia`, dont dépend le
 * `BreakpointObserver` du CDK utilisé par la coquille applicative.
 * On fournit une implémentation minimale et inerte.
 */
if (typeof window !== 'undefined' && typeof window.matchMedia !== 'function') {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string): MediaQueryList =>
      ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => undefined,
        removeListener: () => undefined,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
        dispatchEvent: () => false,
      }) as unknown as MediaQueryList,
  });
}

/**
 * jsdom n'implémente pas non plus `Element.scrollIntoView` (utilisé par la
 * coquille applicative pour garder l'entrée de menu active visible, et par
 * plusieurs composants Angular Material). Stub inerte, même esprit que
 * `matchMedia` ci-dessus.
 */
if (typeof Element !== 'undefined' && typeof Element.prototype.scrollIntoView !== 'function') {
  Element.prototype.scrollIntoView = () => undefined;
}
