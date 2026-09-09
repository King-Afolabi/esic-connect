import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  NgZone,
  OnDestroy,
  ViewChild,
  inject,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { NavigationStart, Router } from '@angular/router';
import { filter } from 'rxjs';

import jsQR from 'jsqr';

/** Cause d'échec normalisée, sans détail d'infrastructure. */
export type QrScannerErrorKind =
  | 'unsupported' // navigateur sans accès caméra
  | 'permission-denied' // l'utilisateur a refusé la caméra
  | 'no-camera' // aucune caméra disponible
  | 'camera-busy' // caméra déjà utilisée par une autre application
  | 'unknown';

type ScanState = 'starting' | 'scanning' | 'decoded' | 'error';

/** Sous-ensemble typé de l'API `BarcodeDetector` (absente des `lib` DOM). */
interface BarcodeDetectorLike {
  detect(source: CanvasImageSource): Promise<{ rawValue: string }[]>;
}
interface BarcodeDetectorCtor {
  new (options?: { formats?: string[] }): BarcodeDetectorLike;
  getSupportedFormats?(): Promise<string[]>;
}

/**
 * Scanner de QR code réutilisable pour l'émargement.
 *
 * Responsabilités et garde-fous :
 * - la caméra n'est demandée qu'après **action utilisateur** — ce
 *   composant n'est rendu qu'après un clic « Scanner » ;
 * - caméra **arrière** privilégiée (`facingMode: environment`, puis
 *   sélection explicite du périphérique arrière si plusieurs caméras) ;
 * - les pistes caméra sont libérées à la **destruction**, à la
 *   **fermeture**, à un **changement de route** et **dès qu'un QR est
 *   lu** — jamais de caméra active hors de cet écran ;
 * - un seul QR est traité : `handledOnce` verrouille dès la première
 *   lecture et coupe la caméra ;
 * - décodage : `BarcodeDetector` natif s'il existe (accélération), sinon
 *   `jsQR` sur les trames vidéo — jamais `BarcodeDetector` comme unique
 *   voie (absent d'iOS Safari) ;
 * - **ne décide jamais** de la validité d'une présence : il émet la
 *   chaîne opaque brute et laisse l'appelant appeler l'API et afficher le
 *   résultat du serveur ;
 * - ne conserve rien (`localStorage` / `sessionStorage` interdits).
 *
 * La caméra exige un **contexte sécurisé** (HTTPS ou `localhost`).
 */
@Component({
  selector: 'app-qr-scanner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatIconModule],
  templateUrl: './qr-scanner.html',
  styleUrl: './qr-scanner.scss',
})
export class QrScanner implements AfterViewInit, OnDestroy {
  /** Chaîne opaque décodée (jeton dynamique ou URL de salle). */
  readonly scanned = output<string>();
  /** L'utilisateur ferme le scanner sans résultat. */
  readonly closed = output<void>();
  /** L'utilisateur préfère saisir un code court. */
  readonly fallbackRequested = output<void>();

  @ViewChild('video') private videoRef?: ElementRef<HTMLVideoElement>;
  @ViewChild('heading') private headingRef?: ElementRef<HTMLElement>;

  protected readonly state = signal<ScanState>('starting');
  protected readonly errorKind = signal<QrScannerErrorKind | null>(null);
  /** Message annoncé aux technologies d'assistance (`aria-live`). */
  protected readonly liveMessage = signal('');

  private readonly ngZone = inject(NgZone);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private stream: MediaStream | null = null;
  private rafId = 0;
  private timerId = 0;
  private detector: BarcodeDetectorLike | null = null;
  private readonly canvas =
    typeof document !== 'undefined' ? document.createElement('canvas') : null;
  private readonly ctx =
    this.canvas?.getContext('2d', { willReadFrequently: true }) ?? null;

  /** Verrou anti-traitement multiple : passe à `true` à la première lecture. */
  private handledOnce = false;
  /** Coupe la boucle de décodage. */
  private stopped = false;

  constructor() {
    // Un changement de route ne doit jamais laisser la caméra allumée.
    this.router.events
      .pipe(
        filter((event): event is NavigationStart => event instanceof NavigationStart),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.stopCamera());
  }

  ngAfterViewInit(): void {
    this.headingRef?.nativeElement.focus?.();
    void this.start();
  }

  ngOnDestroy(): void {
    this.stopCamera();
  }

  protected retry(): void {
    if (this.state() === 'scanning' || this.state() === 'starting') {
      return;
    }
    this.handledOnce = false;
    this.stopped = false;
    this.errorKind.set(null);
    this.state.set('starting');
    void this.start();
  }

  protected close(): void {
    this.stopCamera();
    this.closed.emit();
  }

  protected useShortCode(): void {
    this.stopCamera();
    this.fallbackRequested.emit();
  }

  protected errorText(): string {
    switch (this.errorKind()) {
      case 'permission-denied':
        return "L'accès à la caméra a été refusé. Autorisez-le dans votre navigateur, ou saisissez un code court.";
      case 'no-camera':
        return 'Aucune caméra détectée sur cet appareil. Saisissez un code court.';
      case 'camera-busy':
        return 'La caméra est déjà utilisée par une autre application. Fermez-la, puis réessayez.';
      case 'unsupported':
        return "Ce navigateur ne permet pas l'accès à la caméra (un contexte HTTPS est requis). Saisissez un code court.";
      default:
        return "La caméra n'a pas pu démarrer. Réessayez, ou saisissez un code court.";
    }
  }

  // --- Cycle de vie caméra ---------------------------------------------

  private async start(): Promise<void> {
    this.stopped = false;
    const media = typeof navigator !== 'undefined' ? navigator.mediaDevices : undefined;
    if (!media?.getUserMedia || !this.canvas || !this.ctx) {
      this.fail('unsupported');
      return;
    }

    let stream: MediaStream;
    try {
      stream = await media.getUserMedia({
        video: { facingMode: { ideal: 'environment' } },
        audio: false,
      });
    } catch (error) {
      this.fail(classifyGetUserMediaError(error));
      return;
    }

    // Sélection explicite d'une caméra arrière si plusieurs caméras
    // existent et que la contrainte `facingMode` n'a pas suffi.
    stream = await this.preferRearCamera(media, stream);

    if (this.stopped) {
      stopTracks(stream);
      return;
    }
    this.stream = stream;

    const video = this.videoRef?.nativeElement;
    if (!video) {
      this.fail('unknown');
      return;
    }
    video.srcObject = stream;
    video.setAttribute('playsinline', 'true');
    video.muted = true;
    try {
      await video.play();
    } catch {
      // Lecture différée par le navigateur : la boucle attend `readyState`.
    }

    await this.initDetector();

    this.state.set('scanning');
    this.liveMessage.set('Caméra activée. Placez le QR code dans le cadre.');
    this.scheduleTick();
  }

  private async preferRearCamera(
    media: MediaDevices,
    current: MediaStream,
  ): Promise<MediaStream> {
    if (!media.enumerateDevices) {
      return current;
    }
    try {
      const devices = await media.enumerateDevices();
      const cameras = devices.filter((d) => d.kind === 'videoinput');
      if (cameras.length < 2) {
        return current;
      }
      const rear = cameras.find((d) => /back|rear|arrière|environment/i.test(d.label));
      const activeId = current.getVideoTracks()[0]?.getSettings?.().deviceId;
      if (!rear || !rear.deviceId || rear.deviceId === activeId) {
        return current;
      }
      const better = await media.getUserMedia({
        video: { deviceId: { exact: rear.deviceId } },
        audio: false,
      });
      stopTracks(current);
      return better;
    } catch {
      return current;
    }
  }

  private async initDetector(): Promise<void> {
    const ctor = (globalThis as { BarcodeDetector?: BarcodeDetectorCtor }).BarcodeDetector;
    if (!ctor) {
      this.detector = null;
      return;
    }
    try {
      if (ctor.getSupportedFormats) {
        const formats = await ctor.getSupportedFormats();
        if (!formats.includes('qr_code')) {
          this.detector = null;
          return;
        }
      }
      this.detector = new ctor({ formats: ['qr_code'] });
    } catch {
      this.detector = null;
    }
  }

  private scheduleTick(): void {
    if (this.stopped) {
      return;
    }
    const raf = typeof requestAnimationFrame === 'function';
    this.ngZone.runOutsideAngular(() => {
      if (raf) {
        this.rafId = requestAnimationFrame(() => void this.tick());
      } else {
        this.timerId = setTimeout(() => void this.tick(), 120) as unknown as number;
      }
    });
  }

  /** Décode une trame ; à usage interne (exposé pour les tests). */
  protected async tick(): Promise<void> {
    if (this.stopped || this.handledOnce) {
      return;
    }
    const video = this.videoRef?.nativeElement;
    if (!video || video.readyState < 2 || !video.videoWidth) {
      this.scheduleTick();
      return;
    }
    const text = await this.decodeFrame(video);
    if (text && text.trim()) {
      this.handleResult(text.trim());
      return;
    }
    this.scheduleTick();
  }

  private async decodeFrame(video: HTMLVideoElement): Promise<string | null> {
    const width = video.videoWidth;
    const height = video.videoHeight;
    if (!this.canvas || !this.ctx || !width || !height) {
      return null;
    }
    this.canvas.width = width;
    this.canvas.height = height;
    this.ctx.drawImage(video, 0, 0, width, height);

    if (this.detector) {
      try {
        const found = await this.detector.detect(this.canvas);
        const value = found.find((b) => b.rawValue)?.rawValue;
        if (value) {
          return value;
        }
      } catch {
        // On retombe sur jsQR pour cette trame.
      }
    }
    try {
      const image = this.ctx.getImageData(0, 0, width, height);
      const result = jsQR(image.data, width, height, { inversionAttempts: 'dontInvert' });
      return result?.data ?? null;
    } catch {
      return null;
    }
  }

  private handleResult(text: string): void {
    if (this.handledOnce) {
      return;
    }
    this.handledOnce = true;
    this.stopCamera();
    this.ngZone.run(() => {
      this.state.set('decoded');
      this.liveMessage.set('Code détecté, vérification en cours…');
      this.scanned.emit(text);
    });
  }

  private fail(kind: QrScannerErrorKind): void {
    this.stopCamera();
    this.ngZone.run(() => {
      this.errorKind.set(kind);
      this.state.set('error');
      this.liveMessage.set(this.errorText());
    });
  }

  /** Coupe la boucle et libère toutes les pistes caméra. Idempotent. */
  private stopCamera(): void {
    this.stopped = true;
    if (this.rafId && typeof cancelAnimationFrame === 'function') {
      cancelAnimationFrame(this.rafId);
    }
    if (this.timerId) {
      clearTimeout(this.timerId);
    }
    this.rafId = 0;
    this.timerId = 0;
    if (this.stream) {
      stopTracks(this.stream);
      this.stream = null;
    }
    const video = this.videoRef?.nativeElement;
    if (video) {
      video.srcObject = null;
    }
  }
}

function stopTracks(stream: MediaStream): void {
  for (const track of stream.getTracks()) {
    try {
      track.stop();
    } catch {
      // piste déjà arrêtée
    }
  }
}

function classifyGetUserMediaError(error: unknown): QrScannerErrorKind {
  const name = (error as { name?: string })?.name ?? '';
  switch (name) {
    case 'NotAllowedError':
    case 'SecurityError':
      return 'permission-denied';
    case 'NotFoundError':
    case 'OverconstrainedError':
    case 'DevicesNotFoundError':
      return 'no-camera';
    case 'NotReadableError':
    case 'AbortError':
    case 'TrackStartError':
      return 'camera-busy';
    default:
      return 'unknown';
  }
}
