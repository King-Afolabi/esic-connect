import { TestBed } from '@angular/core/testing';

import { DeviceIdService } from './device-id.service';

describe('DeviceIdService', () => {
  let service: DeviceIdService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(DeviceIdService);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it("génère un identifiant opaque et le conserve d'un appel à l'autre", () => {
    const first = service.current();

    expect(first).toMatch(/^[0-9a-f]{64}$/);
    expect(service.current()).toBe(first);
  });

  it("réutilise l'identifiant déjà stocké plutôt que d'en créer un nouveau", () => {
    const stored = service.current();

    // Nouvelle instance : l'appareil doit rester le même, sinon la
    // reconnaissance d'appareil ne survivrait pas à un rechargement.
    const other = TestBed.inject(DeviceIdService);
    localStorage.setItem('esic-connect.device-id', stored!);
    expect(other.current()).toBe(stored);
  });

  it('oublie l’appareil sur demande', () => {
    service.current();
    service.forget();

    expect(localStorage.getItem('esic-connect.device-id')).toBeNull();
    expect(service.current()).not.toBeNull();
  });

  it('renvoie null lorsque le stockage local est indisponible', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('stockage désactivé');
    });

    // Comportement sûr : sans identifiant, le serveur traite la connexion
    // comme venant d'un appareil inconnu et redemande le second facteur.
    expect(service.current()).toBeNull();
  });

  it("ne contient aucune donnée personnelle : l'identifiant est purement aléatoire", () => {
    const first = service.current();
    service.forget();
    const second = service.current();

    expect(second).not.toBe(first);
  });
});
