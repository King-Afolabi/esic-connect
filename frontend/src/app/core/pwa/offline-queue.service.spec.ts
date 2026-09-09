import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { OfflineQueueService } from './offline-queue.service';

const VALIDATE_URL = '/api/v1/attendance/validate';

/**
 * Laisse la boucle de rejeu franchir ses `await` : une seule bascule de
 * micro-tâche ne suffit pas, `firstValueFrom` en enchaîne plusieurs.
 */
async function settle(): Promise<void> {
  for (let i = 0; i < 5; i++) {
    await Promise.resolve();
  }
}

function setup(): { queue: OfflineQueueService; http: HttpTestingController } {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting()],
  });
  return {
    queue: TestBed.inject(OfflineQueueService),
    http: TestBed.inject(HttpTestingController),
  };
}

/**
 * File d'actions différées (EF-PWA-003 ; RG-063, AC-031).
 *
 * <p>La propriété centrale vérifiée ici : une action mise en file est
 * <strong>en attente</strong>, jamais réussie. Elle ne devient acquise
 * qu'après une réponse favorable du serveur, et un refus est aussi
 * visible qu'une acceptation.
 */
describe('OfflineQueueService', () => {
  it('met une action en attente sans jamais la présenter comme réussie', () => {
    const { queue } = setup();

    const action = queue.enqueue('Émargement', VALIDATE_URL, { shortCode: 'ABC123' });

    expect(action.status).toBe('PENDING');
    expect(queue.pendingCount()).toBe(1);
    expect(queue.actions().map((a) => a.status)).toEqual(['PENDING']);
  });

  it('confirme l’action lorsque le serveur l’accepte', async () => {
    const { queue, http } = setup();
    queue.enqueue('Émargement', VALIDATE_URL, { shortCode: 'ABC123' });

    const replay = queue.replay();
    http.expectOne(`/api${VALIDATE_URL}`).flush('{}');
    await replay;

    expect(queue.actions()[0].status).toBe('CONFIRMED');
    expect(queue.pendingCount()).toBe(0);
    http.verify();
  });

  it('traite un 409 comme un succès : la présence est déjà enregistrée', async () => {
    const { queue, http } = setup();
    queue.enqueue('Émargement', VALIDATE_URL, { shortCode: 'ABC123' });

    const replay = queue.replay();
    http
      .expectOne(`/api${VALIDATE_URL}`)
      .flush({ code: 'ATT_ALREADY_RECORDED' }, { status: 409, statusText: 'Conflict' });
    await replay;

    // Le résultat voulu est atteint : signaler un échec inquiéterait
    // l'apprenant pour une présence pourtant enregistrée.
    expect(queue.actions()[0].status).toBe('CONFIRMED');
  });

  it('marque l’action refusée, avec le motif du serveur, sans la retenter', async () => {
    const { queue, http } = setup();
    queue.enqueue('Émargement', VALIDATE_URL, { shortCode: 'PERIME' });

    const replay = queue.replay();
    http.expectOne(`/api${VALIDATE_URL}`).flush(
      { code: 'ATT_TOKEN_EXPIRED', message: "Le code d'émargement a expiré." },
      { status: 410, statusText: 'Gone' },
    );
    await replay;

    expect(queue.actions()[0].status).toBe('REJECTED');
    expect(queue.actions()[0].reason).toContain('expiré');

    // Un refus est définitif : le rejeu suivant n'émet aucune requête.
    await queue.replay();
    http.verify();
  });

  it('laisse l’action en attente sur une panne réseau', async () => {
    const { queue, http } = setup();
    queue.enqueue('Émargement', VALIDATE_URL, { shortCode: 'ABC123' });

    const replay = queue.replay();
    http.expectOne(`/api${VALIDATE_URL}`).error(new ProgressEvent('error'), { status: 0 });
    await replay;

    expect(queue.actions()[0].status).toBe('PENDING');
    expect(queue.pendingCount()).toBe(1);
  });

  it('laisse l’action en attente sur une erreur serveur', async () => {
    const { queue, http } = setup();
    queue.enqueue('Émargement', VALIDATE_URL, { shortCode: 'ABC123' });

    const replay = queue.replay();
    http
      .expectOne(`/api${VALIDATE_URL}`)
      .flush({}, { status: 503, statusText: 'Service Unavailable' });
    await replay;

    expect(queue.actions()[0].status).toBe('PENDING');
  });

  it('rejoue les actions dans l’ordre où elles ont été faites, une à la fois', async () => {
    const { queue, http } = setup();
    queue.enqueue('Premier', VALIDATE_URL, { shortCode: 'AAA' });
    queue.enqueue('Second', VALIDATE_URL, { shortCode: 'BBB' });

    const replay = queue.replay();

    // Séquentiel et non parallèle : le second ne part qu'après la réponse
    // du premier. Le vérifier compte — rejouer en parallèle ferait perdre
    // l'ordre dans lequel l'utilisateur a agi.
    const first = http.expectOne(`/api${VALIDATE_URL}`);
    expect(first.request.body).toEqual({ shortCode: 'AAA' });
    first.flush('{}');
    await settle();

    const second = http.expectOne(`/api${VALIDATE_URL}`);
    expect(second.request.body).toEqual({ shortCode: 'BBB' });
    second.flush('{}');
    await replay;

    expect(queue.actions().map((a) => a.status)).toEqual(['CONFIRMED', 'CONFIRMED']);
    http.verify();
  });

  it('vide la file à la déconnexion', () => {
    const { queue } = setup();
    queue.enqueue('Émargement', VALIDATE_URL, { shortCode: 'ABC123' });

    queue.clear();

    expect(queue.actions()).toEqual([]);
  });

  it('ne conserve aucun jeton d’émargement dans le stockage du navigateur (RG-093)', () => {
    const { queue } = setup();
    queue.enqueue('Émargement', VALIDATE_URL, { shortCode: 'SECRET-CODE' });

    // La file vit en mémoire : le code court est un jeton, et RG-093
    // interdit d'en placer un dans `localStorage`.
    const dump = JSON.stringify({ ...localStorage });
    expect(dump).not.toContain('SECRET-CODE');
    expect(dump).not.toContain('esic.offline');
  });
});
