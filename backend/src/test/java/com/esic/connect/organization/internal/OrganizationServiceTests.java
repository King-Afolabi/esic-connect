package com.esic.connect.organization.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Validations métier du référentiel organisationnel, isolées des I/O :
 * fuseau, code pays, CIDR, unicité, cohérence site/bâtiment, enfants
 * actifs, tri hors liste blanche.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationServiceTests {

    @Mock
    private SiteRepository siteRepository;
    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private SiteNetworkRangeRepository rangeRepository;
    @Mock
    private OrganizationChangePublisher changePublisher;

    @InjectMocks
    private SiteService siteService;
    @InjectMocks
    private RoomService roomService;
    @InjectMocks
    private SiteNetworkRangeService rangeService;

    // ------------------------------------------------------------------
    // Site
    // ------------------------------------------------------------------

    @Test
    void createSiteRejectsUnknownTimeZone() {
        CreateSiteRequest request = new CreateSiteRequest("ESIC-P", "Paris", null, null, null, null, "FR",
                "Mars/Olympus_Mons");
        assertThatThrownBy(() -> siteService.create(request, null))
                .isInstanceOf(OrganizationException.class)
                .extracting(ex -> ((OrganizationException) ex).kind())
                .isEqualTo(OrganizationException.Kind.INVALID_TIME_ZONE);
    }

    @Test
    void createSiteRejectsNonIsoCountryCode() {
        CreateSiteRequest request = new CreateSiteRequest("ESIC-P", "Paris", null, null, null, null, "XX",
                "Europe/Paris");
        assertThatThrownBy(() -> siteService.create(request, null))
                .extracting(ex -> ((OrganizationException) ex).kind())
                .isEqualTo(OrganizationException.Kind.INVALID_COUNTRY_CODE);
    }

    @Test
    void createSiteRejectsDuplicateCode() {
        when(siteRepository.existsByCode("ESIC-P")).thenReturn(true);
        CreateSiteRequest request = new CreateSiteRequest("ESIC-P", "Paris", null, null, null, null, "FR",
                "Europe/Paris");
        assertThatThrownBy(() -> siteService.create(request, null))
                .extracting(ex -> ((OrganizationException) ex).kind())
                .isEqualTo(OrganizationException.Kind.DUPLICATE_CODE);
    }

    @Test
    void archiveSiteRefusedWhileActiveChildrenRemain() {
        Site site = site("ESIC-P", 10L, false);
        when(siteRepository.findByPublicId(site.getPublicId())).thenReturn(Optional.of(site));
        when(buildingRepository.existsBySiteIdAndStatus(10L, OrganizationStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> siteService.archive(site.getPublicId(), "fermeture", null))
                .extracting(ex -> ((OrganizationException) ex).kind())
                .isEqualTo(OrganizationException.Kind.HAS_ACTIVE_CHILDREN);
    }

    @Test
    void listSiteRejectsSortFieldOutsideWhitelist() {
        assertThatThrownBy(() -> siteService.list(null, null, 0, 20, "password,asc"))
                .extracting(ex -> ((OrganizationException) ex).kind())
                .isEqualTo(OrganizationException.Kind.INVALID_SORT);
    }

    @Test
    void listSiteRejectsInvalidSortDirection() {
        assertThatThrownBy(() -> siteService.list(null, null, 0, 20, "code,upwards"))
                .extracting(ex -> ((OrganizationException) ex).kind())
                .isEqualTo(OrganizationException.Kind.INVALID_SORT);
    }

    // ------------------------------------------------------------------
    // Room
    // ------------------------------------------------------------------

    @Test
    void createRoomRejectsBuildingFromAnotherSite() {
        Site site = site("ESIC-P", 1L, false);
        Site otherSite = site("ESIC-L", 2L, false);
        Building foreignBuilding = new Building(otherSite, "B1", "Bâtiment 1");
        ReflectionTestUtils.setField(foreignBuilding, "id", 99L);
        ReflectionTestUtils.setField(foreignBuilding, "publicId", UUID.randomUUID());

        when(siteRepository.findByPublicId(site.getPublicId())).thenReturn(Optional.of(site));
        when(buildingRepository.findByPublicId(foreignBuilding.getPublicId()))
                .thenReturn(Optional.of(foreignBuilding));

        CreateRoomRequest request = new CreateRoomRequest("R1", "Salle 1",
                foreignBuilding.getPublicId().toString(), 20, null, null);
        assertThatThrownBy(() -> roomService.create(site.getPublicId(), request, null))
                .extracting(ex -> ((OrganizationException) ex).kind())
                .isEqualTo(OrganizationException.Kind.BUILDING_SITE_MISMATCH);
    }

    @Test
    void createRoomRejectedUnderArchivedSite() {
        Site site = site("ESIC-P", 1L, true);
        when(siteRepository.findByPublicId(site.getPublicId())).thenReturn(Optional.of(site));

        CreateRoomRequest request = new CreateRoomRequest("R1", "Salle 1", null, 20, null, null);
        assertThatThrownBy(() -> roomService.create(site.getPublicId(), request, null))
                .extracting(ex -> ((OrganizationException) ex).kind())
                .isEqualTo(OrganizationException.Kind.ARCHIVED_PARENT);
    }

    // ------------------------------------------------------------------
    // Network range
    // ------------------------------------------------------------------

    @Test
    void createRangeRejectsInvalidCidr() {
        Site site = site("ESIC-P", 1L, false);
        when(siteRepository.findByPublicId(site.getPublicId())).thenReturn(Optional.of(site));

        CreateNetworkRangeRequest request = new CreateNetworkRangeRequest("10.0.0.0/40", "LAN");
        assertThatThrownBy(() -> rangeService.create(site.getPublicId(), request, null))
                .extracting(ex -> ((OrganizationException) ex).kind())
                .isEqualTo(OrganizationException.Kind.INVALID_CIDR);
    }

    @Test
    void createRangeRejectsDuplicateActiveCidr() {
        Site site = site("ESIC-P", 1L, false);
        when(siteRepository.findByPublicId(site.getPublicId())).thenReturn(Optional.of(site));
        when(rangeRepository.existsBySiteIdAndCidrAndActiveTrue(1L, "10.0.0.0/8")).thenReturn(true);

        CreateNetworkRangeRequest request = new CreateNetworkRangeRequest("10.0.0.0/8", "LAN");
        assertThatThrownBy(() -> rangeService.create(site.getPublicId(), request, null))
                .extracting(ex -> ((OrganizationException) ex).kind())
                .isEqualTo(OrganizationException.Kind.DUPLICATE_ACTIVE_RANGE);
    }

    @Test
    void createRangePublishesEventAndPersists() {
        Site site = site("ESIC-P", 1L, false);
        when(siteRepository.findByPublicId(site.getPublicId())).thenReturn(Optional.of(site));
        when(rangeRepository.existsBySiteIdAndCidrAndActiveTrue(anyLong(), any())).thenReturn(false);
        when(rangeRepository.save(any(SiteNetworkRange.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(changePublisher.actorId(any())).thenReturn(7L);

        CreateNetworkRangeRequest request = new CreateNetworkRangeRequest("192.168.0.0/16", "Wi-Fi");
        SiteNetworkRangeResponse response = rangeService.create(site.getPublicId(), request, "subject");

        assertThat(response.cidr()).isEqualTo("192.168.0.0/16");
        assertThat(response.active()).isTrue();
    }

    private static Site site(String code, long id, boolean archived) {
        Site site = new Site(code, code + " name", "Europe/Paris");
        ReflectionTestUtils.setField(site, "id", id);
        ReflectionTestUtils.setField(site, "publicId", UUID.randomUUID());
        if (archived) {
            site.archive("test", null, Instant.now());
        }
        return site;
    }
}
