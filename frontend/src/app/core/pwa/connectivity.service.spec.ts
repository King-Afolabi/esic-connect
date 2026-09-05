import { TestBed } from '@angular/core/testing';

import { ConnectivityService } from './connectivity.service';

function setup(): ConnectivityService {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({});
  return TestBed.inject(ConnectivityService);
}

/**
 * État de connexion (EF-PWA-002).
 *
 * <p>`navigator.onLine` ne dit pas si le serveur répond ; l'échec d'un
 * appel, si. Les deux sources doivent donc converger vers un seul état.
 */
describe('ConnectivityService', () => {
  it('se considère en ligne par défaut', () => {
    expect(setup().online()).toBe(true);
  });

  it('bascule hors ligne sur une panne réseau signalée par l’intercepteur', () => {
    const connectivity = setup();

    connectivity.reportNetworkFailure();

    // Une requête qui n'atteint pas le serveur vaut « hors ligne », même
    // si le système croit l'appareil connecté (portail captif, VPN mort).
    expect(connectivity.online()).toBe(false);
  });

  it('revient en ligne dès qu’une réponse serveur arrive', () => {
    const connectivity = setup();
    connectivity.reportNetworkFailure();

    connectivity.reportNetworkSuccess();

    expect(connectivity.online()).toBe(true);
  });

  it('suit les événements « online » et « offline » du navigateur', () => {
    const connectivity = setup();

    window.dispatchEvent(new Event('offline'));
    expect(connectivity.online()).toBe(false);

    window.dispatchEvent(new Event('online'));
    expect(connectivity.online()).toBe(true);
  });
});
