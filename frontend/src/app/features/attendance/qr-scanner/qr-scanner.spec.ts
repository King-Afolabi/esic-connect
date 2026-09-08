import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { QrScanner } from './qr-scanner';

/** jsQR est le décodeur logiciel de repli — mocké, jamais exécuté réellement ici. */
const jsqrMock = vi.fn<(...args: unknown[]) => { data: string } | null>(() => null);
vi.mock('jsqr', () => ({ default: (...args: unknown[]) => jsqrMock(...args) }));

interface Track {
  stop: ReturnType<typeof vi.fn>;
  getSettings: () => { deviceId: string };
}

function makeStream(deviceId = 'cam-front'): { stream: MediaStream; track: Track } {
  const track: Track = { stop: vi.fn(), getSettings: () => ({ deviceId }) };
  const stream = {
    getTracks: () => [track],
    getVideoTracks: () => [track],
  } as unknown as MediaStream;
  return { stream, track };
}

interface ScannerInternals {
  handleResult: (text: string) => void;
  tick: () => Promise<void>;
}

describe('QrScanner', () => {
  let getUserMedia: ReturnType<typeof vi.fn>;
  let enumerateDevices: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    jsqrMock.mockReset();
    jsqrMock.mockReturnValue(null);

    // rAF piloté par les tests : la boucle ne tourne pas toute seule.
    vi.spyOn(window, 'requestAnimationFrame').mockReturnValue(0 as unknown as number);
    vi.spyOn(window, 'cancelAnimationFrame').mockImplementation(() => undefined);

    // jsdom n'implémente ni la lecture vidéo ni `srcObject` ni le canvas 2D.
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined);
    Object.defineProperty(HTMLMediaElement.prototype, 'srcObject', {
      configurable: true,
      get: () => null,
      set: () => undefined,
    });
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      drawImage: vi.fn(),
      getImageData: () => ({ data: new Uint8ClampedArray(4) }),
    } as unknown as CanvasRenderingContext2D);

    getUserMedia = vi.fn().mockResolvedValue(makeStream().stream);
    enumerateDevices = vi.fn().mockResolvedValue([]);
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia, enumerateDevices },
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: undefined });
  });

  function setup() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture: ComponentFixture<QrScanner> = TestBed.createComponent(QrScanner);
    const scanned: string[] = [];
    const closed: number[] = [];
    const fallback: number[] = [];
    fixture.componentInstance.scanned.subscribe((v) => scanned.push(v));
    fixture.componentInstance.closed.subscribe(() => closed.push(1));
    fixture.componentInstance.fallbackRequested.subscribe(() => fallback.push(1));
    fixture.detectChanges(); // déclenche ngAfterViewInit → start()
    return {
      fixture,
      scanned,
      closed,
      fallback,
      internals: fixture.componentInstance as unknown as ScannerInternals,
      text: () => (fixture.nativeElement as HTMLElement).textContent ?? '',
      el: () => fixture.nativeElement as HTMLElement,
      button: (label: string) =>
        [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')].find((b) =>
          (b.textContent ?? '').includes(label),
        ) as HTMLButtonElement | undefined,
    };
  }

  const flush = async () => {
    for (let i = 0; i < 6; i++) {
      await new Promise((r) => setTimeout(r, 0));
    }
  };

  it('requests the rear camera once the view is initialised', async () => {
    const s = setup();
    await flush();
    expect(getUserMedia).toHaveBeenCalledTimes(1);
    const constraints = getUserMedia.mock.calls[0][0] as MediaStreamConstraints;
    expect((constraints.video as MediaTrackConstraints).facingMode).toEqual({ ideal: 'environment' });
    s.fixture.detectChanges();
    expect(s.text()).toContain('Caméra activée');
  });

  it('re-selects an explicit rear camera when several cameras exist', async () => {
    const first = makeStream('cam-front');
    const second = makeStream('cam-back');
    getUserMedia.mockResolvedValueOnce(first.stream).mockResolvedValueOnce(second.stream);
    enumerateDevices.mockResolvedValue([
      { kind: 'videoinput', deviceId: 'cam-front', label: 'Front Camera' },
      { kind: 'videoinput', deviceId: 'cam-back', label: 'Back Camera' },
    ]);
    setup();
    await flush();
    expect(getUserMedia).toHaveBeenCalledTimes(2);
    expect(getUserMedia.mock.calls[1][0]).toEqual({
      video: { deviceId: { exact: 'cam-back' } },
      audio: false,
    });
    expect(first.track.stop).toHaveBeenCalled(); // l'ancien flux est libéré
  });

  it('stops every camera track on destroy', async () => {
    const only = makeStream();
    getUserMedia.mockResolvedValue(only.stream);
    const s = setup();
    await flush();
    s.fixture.destroy();
    expect(only.track.stop).toHaveBeenCalled();
  });

  it('handles a decoded QR once: stops the camera, emits the raw value, no second emit', async () => {
    const only = makeStream();
    getUserMedia.mockResolvedValue(only.stream);
    const s = setup();
    await flush();
    s.internals.handleResult('TOKEN-abc');
    s.internals.handleResult('TOKEN-abc-again');
    expect(s.scanned).toEqual(['TOKEN-abc']);
    expect(only.track.stop).toHaveBeenCalled();
    s.fixture.detectChanges();
    expect(s.text()).toContain('vérification en cours');
  });

  it('shows an accessible permission-denied error and keeps the short-code fallback', async () => {
    getUserMedia.mockRejectedValue(Object.assign(new Error('no'), { name: 'NotAllowedError' }));
    const s = setup();
    await flush();
    s.fixture.detectChanges();
    expect(s.text()).toContain("L'accès à la caméra a été refusé");
    expect(s.el().querySelector('[role="alert"]')).not.toBeNull();
    expect(s.button('Saisir un code court')).toBeDefined();
    expect(s.button('Réessayer')).toBeDefined();
  });

  it('maps NotReadableError to "camera busy" and NotFoundError to "no camera"', async () => {
    getUserMedia.mockRejectedValueOnce(
      Object.assign(new Error(''), { name: 'NotReadableError' }),
    );
    const busy = setup();
    await flush();
    busy.fixture.detectChanges();
    expect(busy.text()).toContain('déjà utilisée par une autre application');

    getUserMedia.mockRejectedValueOnce(Object.assign(new Error(''), { name: 'NotFoundError' }));
    const none = setup();
    await flush();
    none.fixture.detectChanges();
    expect(none.text()).toContain('Aucune caméra détectée');
  });

  it('reports "unsupported" when the browser exposes no camera API', async () => {
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: undefined });
    const s = setup();
    await flush();
    s.fixture.detectChanges();
    expect(s.text()).toContain('contexte HTTPS');
  });

  it('emits fallbackRequested and stops the camera when the user picks manual entry', async () => {
    const only = makeStream();
    getUserMedia.mockResolvedValue(only.stream);
    const s = setup();
    await flush();
    s.button('Saisir un code court')!.click();
    expect(s.fallback).toEqual([1]);
    expect(only.track.stop).toHaveBeenCalled();
  });

  it('emits closed and stops the camera on Fermer', async () => {
    const only = makeStream();
    getUserMedia.mockResolvedValue(only.stream);
    const s = setup();
    await flush();
    s.button('Fermer')!.click();
    expect(s.closed).toEqual([1]);
    expect(only.track.stop).toHaveBeenCalled();
  });

  it('does not decode a frame before the video is ready', async () => {
    const s = setup();
    await flush();
    await s.internals.tick();
    expect(jsqrMock).not.toHaveBeenCalled(); // readyState 0 en jsdom
  });

  it('touches neither localStorage nor sessionStorage', async () => {
    const ls = vi.spyOn(Storage.prototype, 'setItem');
    setup();
    await flush();
    expect(ls).not.toHaveBeenCalled();
  });

  it('exposes an aria-live region for assistive tech', async () => {
    const s = setup();
    await flush();
    expect(s.el().querySelector('[aria-live="polite"]')).not.toBeNull();
    expect(s.el().querySelector('[role="region"]')).not.toBeNull();
  });
});
