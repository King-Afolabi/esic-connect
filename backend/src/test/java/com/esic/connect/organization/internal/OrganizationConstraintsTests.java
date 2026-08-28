package com.esic.connect.organization.internal;

import com.esic.connect.shared.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contraintes SQL du référentiel organisationnel (docs/04 §9, §46.2) :
 * unicités, FK RESTRICT et unicité de la plage réseau active.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class OrganizationConstraintsTests {

    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private SiteNetworkRangeRepository rangeRepository;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void siteCodeMustBeUnique() {
        String code = uniqueCode();
        siteRepository.saveAndFlush(new Site(code, "Campus A", "Europe/Paris"));

        assertThrows(DataIntegrityViolationException.class,
                () -> siteRepository.saveAndFlush(new Site(code, "Campus B", "Europe/Paris")));
    }

    @Test
    void sitePublicIdMustBeUnique() {
        Site first = siteRepository.saveAndFlush(new Site(uniqueCode(), "Campus", "Europe/Paris"));
        Site second = new Site(uniqueCode(), "Campus", "Europe/Paris");
        ReflectionTestUtils.setField(second, "publicId", first.getPublicId());

        assertThrows(DataIntegrityViolationException.class,
                () -> siteRepository.saveAndFlush(second));
    }

    @Test
    void buildingCodeMustBeUniquePerSiteButFreeAcrossSites() {
        Site siteA = siteRepository.saveAndFlush(new Site(uniqueCode(), "A", "Europe/Paris"));
        Site siteB = siteRepository.saveAndFlush(new Site(uniqueCode(), "B", "Europe/Paris"));
        buildingRepository.saveAndFlush(new Building(siteA, "B1", "Bâtiment 1"));

        // Même code, autre site : accepté.
        buildingRepository.saveAndFlush(new Building(siteB, "B1", "Bâtiment 1"));
        // Même code, même site : rejeté.
        assertThrows(DataIntegrityViolationException.class,
                () -> buildingRepository.saveAndFlush(new Building(siteA, "B1", "Bâtiment 1 bis")));
    }

    @Test
    void roomCodeMustBeUniquePerSite() {
        Site site = siteRepository.saveAndFlush(new Site(uniqueCode(), "A", "Europe/Paris"));
        roomRepository.saveAndFlush(new Room(site, null, "R1", "Salle 1", 20, null, null));

        assertThrows(DataIntegrityViolationException.class,
                () -> roomRepository.saveAndFlush(new Room(site, null, "R1", "Salle 1 bis", 10, null, null)));
    }

    @Test
    void deletingSiteReferencedByBuildingIsRejected() {
        Site site = siteRepository.saveAndFlush(new Site(uniqueCode(), "A", "Europe/Paris"));
        buildingRepository.saveAndFlush(new Building(site, "B1", "Bâtiment 1"));
        Long siteId = site.getId();
        entityManager.clear();

        assertThrows(DataIntegrityViolationException.class, () -> {
            siteRepository.deleteById(siteId);
            siteRepository.flush();
        });
    }

    @Test
    void deletingBuildingReferencedByRoomIsRejected() {
        Site site = siteRepository.saveAndFlush(new Site(uniqueCode(), "A", "Europe/Paris"));
        Building building = buildingRepository.saveAndFlush(new Building(site, "B1", "Bâtiment 1"));
        roomRepository.saveAndFlush(new Room(site, building, "R1", "Salle 1", 20, null, null));
        Long buildingId = building.getId();
        entityManager.clear();

        assertThrows(DataIntegrityViolationException.class, () -> {
            buildingRepository.deleteById(buildingId);
            buildingRepository.flush();
        });
    }

    @Test
    void onlyOneActiveNetworkRangePerSiteAndCidr() {
        Site site = siteRepository.saveAndFlush(new Site(uniqueCode(), "A", "Europe/Paris"));
        rangeRepository.saveAndFlush(new SiteNetworkRange(site, "10.0.0.0/8", "LAN", Instant.now()));

        assertThrows(DataIntegrityViolationException.class,
                () -> rangeRepository.saveAndFlush(new SiteNetworkRange(site, "10.0.0.0/8", "LAN bis", Instant.now())));
    }

    @Test
    void deactivatedRangeFreesTheActiveSlot() {
        Site site = siteRepository.saveAndFlush(new Site(uniqueCode(), "A", "Europe/Paris"));
        SiteNetworkRange first = rangeRepository.saveAndFlush(
                new SiteNetworkRange(site, "10.0.0.0/8", "LAN", Instant.now()));
        first.deactivate(Instant.now());
        rangeRepository.saveAndFlush(first);

        SiteNetworkRange second = rangeRepository.saveAndFlush(
                new SiteNetworkRange(site, "10.0.0.0/8", "LAN v2", Instant.now()));
        assertThat(second.getId()).isNotNull();
        assertThat(second.isActive()).isTrue();
    }

    private static String uniqueCode() {
        // <= 50 caractères ("ST-" + 36 pour l'UUID).
        return "ST-" + UUID.randomUUID();
    }
}
