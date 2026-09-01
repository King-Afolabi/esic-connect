import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { RoleContextService } from '../../../core/auth/role-context.service';
import { Role } from '../../../core/models/role';
import { NotificationService } from '../../../core/notifications/notification.service';
import { OrganizationApiService } from '../organization-api.service';
import { toOrganizationError } from '../organization-errors';
import {
  BuildingResponse,
  RoomResponse,
  SiteNetworkRangeResponse,
  SiteResponse,
  formatIsoDate,
  organizationStatusLabel,
} from '../organization.models';

type DetailState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'not-found' }
  | { kind: 'forbidden' }
  | { kind: 'ready'; site: SiteResponse };

type ChildState<T> =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; items: T[] };

/** Action de mutation du site en cours de confirmation. */
type PendingSiteAction = { kind: 'archive' } | { kind: 'restore' };

const ARCHIVE_REASON_MAX = 500;
/** `SiteController.WRITE_ROLES` — visibilité des actions d'écriture (site / bâtiment / salle). */
const WRITE_ROLES: readonly Role[] = ['ADMIN', 'SUPER_ADMIN'];
/** `SiteNetworkRangeController` — tout est réservé à `SUPER_ADMIN`, lecture comprise. */
const NETWORK_ROLES: readonly Role[] = ['SUPER_ADMIN'];
const CHILD_PAGE_SIZE = 100;

/**
 * Fiche d'un **site** et gestion des éléments qui en dépendent : archivage
 * / restauration du site, bâtiments, salles, plages réseau autorisées.
 *
 * Les sous-listes (bâtiments, salles, plages) sont chargées à `size=100`
 * sans pagination : un site compte peu d'éléments. Chaque mutation
 * recharge uniquement la sous-liste concernée.
 *
 * La visibilité des actions suit `RoleContextService.effectiveRoles`
 * (contexte de rôle actif — il peut restreindre l'affichage, jamais
 * l'élargir) : `ADMIN` / `SUPER_ADMIN` pour le site, les bâtiments et les
 * salles ; `SUPER_ADMIN` seul pour les plages réseau (et même leur
 * lecture). Rien de cela n'est une garantie : Spring Security reste
 * l'autorité et chaque appel traite les réponses `ORG_*` réelles
 * (`ORG_DUPLICATE_CODE`, `ORG_HAS_ACTIVE_CHILDREN`, `ORG_ENTITY_ARCHIVED`,
 * `ORG_BUILDING_SITE_MISMATCH`…), un `403` restant rendu « accès refusé ».
 */
@Component({
  selector: 'app-site-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
  ],
  templateUrl: './site-detail.html',
  styleUrl: './site-detail.scss',
})
export class SiteDetail {
  private readonly api = inject(OrganizationApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly roleContext = inject(RoleContextService);
  private readonly notifications = inject(NotificationService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  private readonly publicId = this.route.snapshot.paramMap.get('publicId') ?? '';

  protected readonly statusLabel = organizationStatusLabel;
  protected readonly formatDate = formatIsoDate;
  protected readonly reasonMaxLength = ARCHIVE_REASON_MAX;

  protected readonly state = signal<DetailState>({ kind: 'loading' });
  protected readonly buildings = signal<ChildState<BuildingResponse>>({ kind: 'loading' });
  protected readonly rooms = signal<ChildState<RoomResponse>>({ kind: 'loading' });
  protected readonly ranges = signal<ChildState<SiteNetworkRangeResponse>>({ kind: 'loading' });

  protected readonly pendingSite = signal<PendingSiteAction | null>(null);
  protected readonly submitting = signal(false);
  protected readonly actionError = signal<string | null>(null);

  protected readonly buildingColumns = ['code', 'name', 'status', 'actions'];
  protected readonly roomColumns = ['code', 'name', 'building', 'capacity', 'status', 'actions'];
  protected readonly rangeColumns = ['cidr', 'label', 'active', 'actions'];

  private readonly effectiveRoles = this.roleContext.effectiveRoles;
  protected readonly canWrite = computed(() =>
    this.effectiveRoles().some((r) => WRITE_ROLES.includes(r)),
  );
  protected readonly canManageNetwork = computed(() =>
    this.effectiveRoles().some((r) => NETWORK_ROLES.includes(r)),
  );

  protected readonly site = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.site : null;
  });
  protected readonly errorMessage = computed(() => {
    const current = this.state();
    return current.kind === 'error' ? current.message : null;
  });
  protected readonly isArchived = computed(() => this.site()?.status === 'ARCHIVED');

  protected readonly facts = computed<{ label: string; value: string }[]>(() => {
    const s = this.site();
    if (!s) {
      return [];
    }
    const facts = [
      { label: 'Code', value: s.code },
      { label: 'Nom', value: s.name },
      { label: 'Fuseau horaire', value: s.timeZoneId },
      { label: 'Adresse', value: [s.addressLine1, s.addressLine2].filter(Boolean).join(', ') || '—' },
      {
        label: 'Ville',
        value: [s.postalCode, s.city].filter(Boolean).join(' ') || '—',
      },
      { label: 'Pays', value: s.countryCode || '—' },
      { label: 'Statut', value: organizationStatusLabel(s.status) },
      { label: 'Créé le', value: formatIsoDate(s.createdAt) },
    ];
    if (s.status === 'ARCHIVED' && s.archiveReason) {
      facts.push({ label: "Motif d'archivage", value: s.archiveReason });
    }
    return facts;
  });

  /** Bâtiments actifs — options du sélecteur de bâtiment de la salle. */
  protected readonly activeBuildings = computed<BuildingResponse[]>(() => {
    const current = this.buildings();
    return current.kind === 'ready' ? current.items.filter((b) => b.status === 'ACTIVE') : [];
  });

  protected readonly siteReasonForm = this.formBuilder.group({
    reason: this.formBuilder.control('', [
      Validators.required,
      Validators.maxLength(ARCHIVE_REASON_MAX),
    ]),
  });

  protected readonly buildingForm = this.formBuilder.group({
    code: this.formBuilder.control('', [
      Validators.required,
      Validators.maxLength(50),
      Validators.pattern(/^[A-Za-z0-9][A-Za-z0-9._-]{0,49}$/),
    ]),
    name: this.formBuilder.control('', [Validators.required, Validators.maxLength(150)]),
  });
  protected readonly buildingSubmitting = signal(false);
  protected readonly buildingFormError = signal<string | null>(null);

  protected readonly roomForm = this.formBuilder.group({
    code: this.formBuilder.control('', [
      Validators.required,
      Validators.maxLength(50),
      Validators.pattern(/^[A-Za-z0-9][A-Za-z0-9._-]{0,49}$/),
    ]),
    name: this.formBuilder.control('', [Validators.required, Validators.maxLength(150)]),
    buildingPublicId: this.formBuilder.control(''),
    capacity: this.formBuilder.control<number | null>(null, [Validators.min(1)]),
    floorLabel: this.formBuilder.control('', [Validators.maxLength(50)]),
  });
  protected readonly roomSubmitting = signal(false);
  protected readonly roomFormError = signal<string | null>(null);

  protected readonly rangeForm = this.formBuilder.group({
    cidr: this.formBuilder.control('', [Validators.required, Validators.maxLength(50)]),
    label: this.formBuilder.control('', [Validators.required, Validators.maxLength(100)]),
  });
  protected readonly rangeSubmitting = signal(false);
  protected readonly rangeFormError = signal<string | null>(null);

  constructor() {
    this.load();
  }

  protected retry(): void {
    this.load();
  }

  // --- Site lifecycle ---------------------------------------------------

  protected startSiteAction(kind: 'archive' | 'restore'): void {
    this.siteReasonForm.reset({ reason: '' });
    this.actionError.set(null);
    this.pendingSite.set({ kind });
  }

  protected cancelSiteAction(): void {
    this.pendingSite.set(null);
    this.actionError.set(null);
  }

  protected confirmSiteAction(): void {
    const action = this.pendingSite();
    const s = this.site();
    if (!action || !s || this.submitting()) {
      return;
    }
    if (action.kind === 'archive') {
      if (this.siteReasonForm.invalid) {
        this.siteReasonForm.markAllAsTouched();
        return;
      }
      this.runSite(
        this.api.archiveSite(s.publicId, {
          reason: this.siteReasonForm.getRawValue().reason.trim(),
        }),
        'Site archivé.',
      );
      return;
    }
    this.runSite(this.api.restoreSite(s.publicId), 'Site restauré.');
  }

  private runSite(call: Observable<void>, message: string): void {
    this.submitting.set(true);
    this.actionError.set(null);
    call.subscribe({
      next: () => {
        this.submitting.set(false);
        this.pendingSite.set(null);
        this.notifications.info(message);
        this.load();
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.actionError.set(toOrganizationError(error).message);
      },
    });
  }

  // --- Buildings ------------------------------------------------------

  protected submitBuilding(): void {
    const s = this.site();
    if (!s || this.buildingForm.invalid || this.buildingSubmitting()) {
      this.buildingForm.markAllAsTouched();
      return;
    }
    this.buildingSubmitting.set(true);
    this.buildingFormError.set(null);
    const raw = this.buildingForm.getRawValue();
    this.api
      .createBuilding(s.publicId, { code: raw.code.trim(), name: raw.name.trim() })
      .subscribe({
        next: () => {
          this.buildingSubmitting.set(false);
          this.buildingForm.reset({ code: '', name: '' });
          this.notifications.info('Bâtiment créé.');
          this.loadBuildings();
        },
        error: (error: unknown) => {
          this.buildingSubmitting.set(false);
          const view = toOrganizationError(error);
          if (view.field === 'code') {
            this.buildingForm.controls.code.setErrors({ server: view.message });
          }
          this.buildingFormError.set(view.message);
        },
      });
  }

  protected archiveBuilding(building: BuildingResponse): void {
    const reason = window.prompt("Motif d'archivage du bâtiment :", '')?.trim();
    if (!reason) {
      return;
    }
    this.api.archiveBuilding(building.publicId, { reason }).subscribe({
      next: () => {
        this.notifications.info('Bâtiment archivé.');
        this.loadBuildings();
      },
      error: (error: unknown) => this.notifications.error(toOrganizationError(error).message),
    });
  }

  protected restoreBuilding(building: BuildingResponse): void {
    this.api.restoreBuilding(building.publicId).subscribe({
      next: () => {
        this.notifications.info('Bâtiment restauré.');
        this.loadBuildings();
      },
      error: (error: unknown) => this.notifications.error(toOrganizationError(error).message),
    });
  }

  // --- Rooms --------------------------------------------------------

  protected submitRoom(): void {
    const s = this.site();
    if (!s || this.roomForm.invalid || this.roomSubmitting()) {
      this.roomForm.markAllAsTouched();
      return;
    }
    this.roomSubmitting.set(true);
    this.roomFormError.set(null);
    const raw = this.roomForm.getRawValue();
    this.api
      .createRoom(s.publicId, {
        code: raw.code.trim(),
        name: raw.name.trim(),
        buildingPublicId: raw.buildingPublicId || null,
        capacity: raw.capacity ?? null,
        floorLabel: raw.floorLabel.trim() || null,
      })
      .subscribe({
        next: () => {
          this.roomSubmitting.set(false);
          this.roomForm.reset({
            code: '',
            name: '',
            buildingPublicId: '',
            capacity: null,
            floorLabel: '',
          });
          this.notifications.info('Salle créée.');
          this.loadRooms();
        },
        error: (error: unknown) => {
          this.roomSubmitting.set(false);
          const view = toOrganizationError(error);
          if (view.field === 'code') {
            this.roomForm.controls.code.setErrors({ server: view.message });
          } else if (view.field === 'buildingPublicId') {
            this.roomForm.controls.buildingPublicId.setErrors({ server: view.message });
          }
          this.roomFormError.set(view.message);
        },
      });
  }

  protected archiveRoom(room: RoomResponse): void {
    const reason = window.prompt("Motif d'archivage de la salle :", '')?.trim();
    if (!reason) {
      return;
    }
    this.api.archiveRoom(room.publicId, { reason }).subscribe({
      next: () => {
        this.notifications.info('Salle archivée.');
        this.loadRooms();
      },
      error: (error: unknown) => this.notifications.error(toOrganizationError(error).message),
    });
  }

  protected restoreRoom(room: RoomResponse): void {
    this.api.restoreRoom(room.publicId).subscribe({
      next: () => {
        this.notifications.info('Salle restaurée.');
        this.loadRooms();
      },
      error: (error: unknown) => this.notifications.error(toOrganizationError(error).message),
    });
  }

  protected buildingName(publicId: string | null): string {
    if (!publicId) {
      return '—';
    }
    const current = this.buildings();
    const match =
      current.kind === 'ready' ? current.items.find((b) => b.publicId === publicId) : undefined;
    return match ? match.code : '—';
  }

  // --- Network ranges ------------------------------------------------

  protected submitRange(): void {
    const s = this.site();
    if (!s || this.rangeForm.invalid || this.rangeSubmitting()) {
      this.rangeForm.markAllAsTouched();
      return;
    }
    this.rangeSubmitting.set(true);
    this.rangeFormError.set(null);
    const raw = this.rangeForm.getRawValue();
    this.api
      .createNetworkRange(s.publicId, { cidr: raw.cidr.trim(), label: raw.label.trim() })
      .subscribe({
        next: () => {
          this.rangeSubmitting.set(false);
          this.rangeForm.reset({ cidr: '', label: '' });
          this.notifications.info('Plage réseau créée.');
          this.loadRanges();
        },
        error: (error: unknown) => {
          this.rangeSubmitting.set(false);
          const view = toOrganizationError(error);
          if (view.field === 'cidr') {
            this.rangeForm.controls.cidr.setErrors({ server: view.message });
          }
          this.rangeFormError.set(view.message);
        },
      });
  }

  protected toggleRange(range: SiteNetworkRangeResponse): void {
    const call = range.active
      ? this.api.deactivateNetworkRange(range.publicId)
      : this.api.activateNetworkRange(range.publicId);
    call.subscribe({
      next: () => {
        this.notifications.info(range.active ? 'Plage désactivée.' : 'Plage activée.');
        this.loadRanges();
      },
      error: (error: unknown) => this.notifications.error(toOrganizationError(error).message),
    });
  }

  // --- Loaders -----------------------------------------------------

  private load(): void {
    this.state.set({ kind: 'loading' });
    this.pendingSite.set(null);
    this.actionError.set(null);
    this.api.getSite(this.publicId).subscribe({
      next: (site) => {
        this.state.set({ kind: 'ready', site });
        this.loadBuildings();
        this.loadRooms();
        if (this.canManageNetwork()) {
          this.loadRanges();
        }
      },
      error: (error: unknown) => {
        const view = toOrganizationError(error);
        if (view.notFound) {
          this.state.set({ kind: 'not-found' });
          return;
        }
        if (view.forbidden) {
          this.state.set({ kind: 'forbidden' });
          return;
        }
        this.state.set({ kind: 'error', message: view.message });
      },
    });
  }

  protected loadBuildings(): void {
    this.buildings.set({ kind: 'loading' });
    this.api.listBuildings(this.publicId, { sort: 'code,asc', size: CHILD_PAGE_SIZE }).subscribe({
      next: (page) => this.buildings.set({ kind: 'ready', items: page.content }),
      error: (error: unknown) =>
        this.buildings.set({ kind: 'error', message: toOrganizationError(error).message }),
    });
  }

  protected loadRooms(): void {
    this.rooms.set({ kind: 'loading' });
    this.api.listRooms(this.publicId, { sort: 'code,asc', size: CHILD_PAGE_SIZE }).subscribe({
      next: (page) => this.rooms.set({ kind: 'ready', items: page.content }),
      error: (error: unknown) =>
        this.rooms.set({ kind: 'error', message: toOrganizationError(error).message }),
    });
  }

  protected loadRanges(): void {
    this.ranges.set({ kind: 'loading' });
    this.api.listNetworkRanges(this.publicId, { sort: 'createdAt,desc', size: CHILD_PAGE_SIZE }).subscribe({
      next: (page) => this.ranges.set({ kind: 'ready', items: page.content }),
      error: (error: unknown) =>
        this.ranges.set({ kind: 'error', message: toOrganizationError(error).message }),
    });
  }
}
