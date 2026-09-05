package com.esic.connect.notification.internal;

import com.esic.connect.identity.CurrentUserResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Préférences de notification de l'appelant (EF-NOTIF-006 ; docs/02
 * §21.6).
 *
 * <p><strong>Deux règles ne se négocient pas.</strong> Le canal
 * {@code IN_APP} ne se désactive pas : le centre de notifications est la
 * trace de ce qui a été adressé à la personne (§21.5). Et la catégorie
 * {@code SECURITY} ne se désactive pas non plus : « les notifications
 * critiques de sécurité restent obligatoires » (§21.6). Les deux sont
 * refusées ici <em>et</em> par contrainte SQL — un service peut être
 * contourné par un futur appel mal contrôlé, une contrainte non.
 */
@Service
class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;
    private final CurrentUserResolver currentUserResolver;
    private final Clock clock;

    NotificationPreferenceService(NotificationPreferenceRepository repository,
                                  CurrentUserResolver currentUserResolver, Clock clock) {
        this.repository = repository;
        this.currentUserResolver = currentUserResolver;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    NotificationResponses.PreferenceList list(String callerSubject) {
        long userId = requireCaller(callerSubject);
        return new NotificationResponses.PreferenceList(view(effective(userId)));
    }

    @Transactional
    NotificationResponses.PreferenceList update(String callerSubject, String rawCategory,
                                                String rawChannel, Boolean enabled) {
        long userId = requireCaller(callerSubject);
        if (enabled == null) {
            throw new NotificationException(NotificationException.Kind.INVALID_PREFERENCE);
        }
        NotificationCategory category = parseCategory(rawCategory);
        NotificationChannel channel = parseChannel(rawChannel);
        if (!category.optional()) {
            throw new NotificationException(NotificationException.Kind.PREFERENCE_LOCKED);
        }
        if (channel == NotificationChannel.IN_APP) {
            throw new NotificationException(NotificationException.Kind.PREFERENCE_LOCKED);
        }
        var now = clock.instant();
        repository.findByUserIdAndCategoryAndChannel(userId, category, channel)
                .ifPresentOrElse(
                        existing -> existing.setEnabled(enabled, now),
                        () -> repository.save(
                                new NotificationPreference(userId, category, channel, enabled, now)));
        return new NotificationResponses.PreferenceList(view(effective(userId)));
    }

    /**
     * État effectif de tous les canaux réglables : le défaut (activé),
     * corrigé par les exceptions enregistrées.
     */
    @Transactional(readOnly = true)
    Map<NotificationCategory, Map<NotificationChannel, Boolean>> effective(long userId) {
        Map<NotificationCategory, Map<NotificationChannel, Boolean>> result =
                new EnumMap<>(NotificationCategory.class);
        for (NotificationCategory category : NotificationCategory.values()) {
            Map<NotificationChannel, Boolean> channels = new EnumMap<>(NotificationChannel.class);
            channels.put(NotificationChannel.IN_APP, true);
            channels.put(NotificationChannel.EMAIL, true);
            channels.put(NotificationChannel.PUSH, true);
            result.put(category, channels);
        }
        for (NotificationPreference preference : repository.findByUserId(userId)) {
            Map<NotificationChannel, Boolean> channels = result.get(preference.getCategory());
            if (channels != null && preference.getChannel() != NotificationChannel.IN_APP
                    && preference.getCategory().optional()) {
                channels.put(preference.getChannel(), preference.isEnabled());
            }
        }
        return result;
    }

    /** Un canal est-il retenu pour ce destinataire et cette catégorie ? */
    @Transactional(readOnly = true)
    boolean accepts(long userId, NotificationCategory category, NotificationChannel channel) {
        if (channel == NotificationChannel.IN_APP || !category.optional()) {
            return true;
        }
        return repository.findByUserIdAndCategoryAndChannel(userId, category, channel)
                .map(NotificationPreference::isEnabled)
                .orElse(true);
    }

    private static List<NotificationResponses.PreferenceView> view(
            Map<NotificationCategory, Map<NotificationChannel, Boolean>> effective) {
        List<NotificationResponses.PreferenceView> views = new ArrayList<>();
        effective.forEach((category, channels) -> channels.forEach((channel, enabled) ->
                views.add(new NotificationResponses.PreferenceView(
                        category.name(), channel.name(), enabled,
                        // Verrouillé : l'écran affiche la case cochée et
                        // désactivée, plutôt que de laisser croire à un
                        // choix qui serait refusé au clic.
                        !category.optional() || channel == NotificationChannel.IN_APP))));
        return List.copyOf(views);
    }

    private long requireCaller(String callerSubject) {
        return currentUserResolver.resolveInternalId(callerSubject)
                .orElseThrow(() -> new NotificationException(NotificationException.Kind.UNAUTHENTICATED));
    }

    private static NotificationCategory parseCategory(String raw) {
        try {
            return NotificationCategory.valueOf(String.valueOf(raw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException unknown) {
            throw new NotificationException(NotificationException.Kind.INVALID_PREFERENCE);
        }
    }

    private static NotificationChannel parseChannel(String raw) {
        try {
            return NotificationChannel.valueOf(String.valueOf(raw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException unknown) {
            throw new NotificationException(NotificationException.Kind.INVALID_PREFERENCE);
        }
    }
}
