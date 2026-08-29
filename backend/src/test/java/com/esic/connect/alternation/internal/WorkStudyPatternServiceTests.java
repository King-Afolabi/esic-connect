package com.esic.connect.alternation.internal;

import com.esic.connect.alternation.AlternationChangeAction;
import com.esic.connect.alternation.AlternationResourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validations métier des modèles de rythme, isolées des I/O : code
 * dupliqué, code / type figés, configuration invalide propagée, archivage
 * et restauration.
 */
@ExtendWith(MockitoExtension.class)
class WorkStudyPatternServiceTests {

    @Mock
    private WorkStudyPatternRepository patternRepository;
    @Mock
    private AlternationChangePublisher changePublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AlternationConfigParser configParser = new AlternationConfigParser(new ObjectMapper());

    private WorkStudyPatternService service() {
        return new WorkStudyPatternService(patternRepository, configParser, changePublisher, objectMapper);
    }

    private WorkStudyPatternRequests.Create create(String type, String configJson) throws Exception {
        return new WorkStudyPatternRequests.Create("RYT-1", "Rythme", "desc", type, null,
                objectMapper.readTree(configJson));
    }

    @Test
    void createRejectsDuplicateCode() throws Exception {
        when(patternRepository.existsByCode("RYT-1")).thenReturn(true);
        assertThatThrownBy(() -> service().create(create("CUSTOM", "{\"cycleLengthWeeks\":2,"
                + "\"schoolWeeks\":[1],\"companyWeeks\":[2]}"), null))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.DUPLICATE_CODE);
        verify(patternRepository, never()).save(any());
    }

    @Test
    void createRejectsInvalidType() throws Exception {
        assertThatThrownBy(() -> service().create(create("WEEKLY", "{}"), null))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.INVALID_PATTERN_TYPE);
    }

    @Test
    void createPropagatesInvalidConfiguration() throws Exception {
        when(patternRepository.existsByCode("RYT-1")).thenReturn(false);
        assertThatThrownBy(() -> service().create(
                create("THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY", "{\"schoolDays\":[\"MONDAY\"],\"foo\":1}"), null))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.INVALID_CONFIGURATION);
        verify(patternRepository, never()).save(any());
    }

    @Test
    void createStoresCanonicalConfigurationAndPublishesEvent() throws Exception {
        when(patternRepository.existsByCode("RYT-1")).thenReturn(false);
        when(patternRepository.save(any(WorkStudyPattern.class))).thenAnswer(inv -> {
            WorkStudyPattern p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "publicId", UUID.randomUUID());
            return p;
        });
        when(changePublisher.actorId("sub")).thenReturn(9L);

        WorkStudyPatternResponse response = service().create(create("THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY",
                "{\"schoolDays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\"],"
                        + "\"companyDays\":[\"THURSDAY\",\"FRIDAY\"]}"), "sub");

        assertThat(response.type()).isEqualTo(WorkStudyPatternType.THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY);
        assertThat(response.cycleLengthWeeks()).isEqualTo(1);
        assertThat(response.configuration().get("schoolDays")).hasSize(3);
        verify(changePublisher).publish(eq(AlternationResourceType.WORK_STUDY_PATTERN), any(),
                eq(AlternationChangeAction.CREATED), eq(9L), any());
    }

    @Test
    void updateKeepsCodeAndTypeAndRevalidatesConfiguration() throws Exception {
        WorkStudyPattern existing = pattern();
        when(patternRepository.findByPublicId(existing.getPublicId())).thenReturn(Optional.of(existing));

        WorkStudyPatternRequests.Update request = new WorkStudyPatternRequests.Update("Nouveau nom", "d", 4,
                objectMapper.readTree("{\"schoolWeeks\":[1],\"companyWeeks\":[2,3,4]}"));
        WorkStudyPatternResponse response = service().update(existing.getPublicId(), request, "sub");

        assertThat(response.code()).isEqualTo(existing.getCode());
        assertThat(response.type()).isEqualTo(WorkStudyPatternType.ONE_WEEK_SCHOOL_OUT_OF_FOUR);
        assertThat(response.name()).isEqualTo("Nouveau nom");
    }

    @Test
    void updateRejectedWhenArchived() throws Exception {
        WorkStudyPattern archived = pattern();
        archived.archive("stop", null, java.time.Instant.now());
        when(patternRepository.findByPublicId(archived.getPublicId())).thenReturn(Optional.of(archived));
        WorkStudyPatternRequests.Update request = new WorkStudyPatternRequests.Update("n", null, 4,
                objectMapper.readTree("{\"schoolWeeks\":[1],\"companyWeeks\":[2,3,4]}"));
        assertThatThrownBy(() -> service().update(archived.getPublicId(), request, "sub"))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.INVALID_STATE);
    }

    @Test
    void archiveThenRestoreTogglesStatusAndAudits() {
        WorkStudyPattern pattern = pattern();
        when(patternRepository.findByPublicId(pattern.getPublicId())).thenReturn(Optional.of(pattern));

        service().archive(pattern.getPublicId(), "obsolète", "sub");
        assertThat(pattern.getStatus()).isEqualTo(WorkStudyPatternStatus.ARCHIVED);

        service().restore(pattern.getPublicId(), "sub");
        assertThat(pattern.getStatus()).isEqualTo(WorkStudyPatternStatus.ACTIVE);

        ArgumentCaptor<AlternationChangeAction> actions = ArgumentCaptor.forClass(AlternationChangeAction.class);
        verify(changePublisher, org.mockito.Mockito.atLeast(2)).publish(any(), any(), actions.capture(),
                any(), any());
        assertThat(actions.getAllValues()).contains(AlternationChangeAction.ARCHIVED,
                AlternationChangeAction.RESTORED);
    }

    @Test
    void restoreRejectedWhenNotArchived() {
        WorkStudyPattern pattern = pattern();
        when(patternRepository.findByPublicId(pattern.getPublicId())).thenReturn(Optional.of(pattern));
        assertThatThrownBy(() -> service().restore(pattern.getPublicId(), "sub"))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.INVALID_STATE);
    }

    @Test
    void getRejectsUnknownPattern() {
        UUID id = UUID.randomUUID();
        when(patternRepository.findByPublicId(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().get(id))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.PATTERN_NOT_FOUND);
    }

    @Test
    void listRejectsSortOutsideWhitelist() {
        assertThatThrownBy(() -> service().list(null, null, null, 0, 20, "configurationJson,asc"))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.INVALID_SORT);
    }

    private static WorkStudyPattern pattern() {
        WorkStudyPattern pattern = new WorkStudyPattern("RYT-1", "Rythme", null,
                WorkStudyPatternType.ONE_WEEK_SCHOOL_OUT_OF_FOUR, 4,
                "{\"cycleLengthWeeks\":4,\"schoolWeeks\":[1],\"companyWeeks\":[2,3,4],"
                        + "\"schoolDays\":[\"MONDAY\"],\"companyDays\":[]}");
        ReflectionTestUtils.setField(pattern, "publicId", UUID.randomUUID());
        return pattern;
    }
}
