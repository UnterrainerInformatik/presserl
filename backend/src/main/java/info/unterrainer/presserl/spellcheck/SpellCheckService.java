package info.unterrainer.presserl.spellcheck;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import info.unterrainer.presserl.newspaper.EffectiveSettings;
import info.unterrainer.presserl.newspaper.NewspaperSettings;
import info.unterrainer.presserl.newspaper.SpellCheckHelp;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Checks a text with LanguageTool and keeps only spelling, casing, grammar, punctuation and
 * typography findings, so children are not flooded with style advice. The newspaper's
 * {@code spell-check.help} decides whether a finding carries its message and replacements. The checked
 * text is never logged; an outage is logged once until the next successful check.
 */
@ApplicationScoped
public class SpellCheckService {

    static final int MAX_REPLACEMENTS = 5;

    static final Set<String> ISSUE_TYPES = Set.of("misspelling", "grammar", "typographical", "uncategorized");
    static final Set<String> CATEGORIES = Set.of("TYPOS", "CASING", "GRAMMAR", "PUNCTUATION", "CONFUSED_WORDS",
            "COMPOUNDING");

    private static final Logger LOG = Logger.getLogger(SpellCheckService.class);

    @Inject
    SpellCheckConfig config;

    @Inject
    @RestClient
    LanguageToolClient client;

    @Inject
    NewspaperSettings settings;

    private final AtomicBoolean down = new AtomicBoolean();

    /**
     * @throws SpellCheckUnavailableException (as a failed {@link Uni}) when the check is switched off
     *         or LanguageTool fails or does not answer in time
     */
    public Uni<List<SpellMatchDto>> check(String text) {
        if (!config.enabled()) {
            return Uni.createFrom().failure(new SpellCheckUnavailableException(null));
        }
        if (text.isBlank()) {
            return Uni.createFrom().item(List.of());
        }
        // read per check, not cached, so a changed level applies to the next check; a failed read fails
        // the request instead of falling back to more help
        return settings.effective()
                .map(EffectiveSettings::spellCheckHelp)
                .flatMap(help -> languageTool(text)
                        .map(matches -> matches.stream().map(match -> shaped(match, help)).toList()));
    }

    private Uni<List<SpellMatchDto>> languageTool(String text) {
        return client.check(text, config.language(), "default")
                .map(SpellCheckService::matches)
                .invoke(() -> {
                    if (down.compareAndSet(true, false)) {
                        LOG.info("LanguageTool answers again");
                    }
                })
                .onFailure().transform(e -> {
                    if (down.compareAndSet(false, true)) {
                        LOG.warnf("LanguageTool at %s is unavailable, spell checks answer 503: %s", config.url(),
                                e.toString());
                    }
                    return new SpellCheckUnavailableException(e);
                });
    }

    /**
     * The finding as the help level allows: {@code messages} drops the replacements, {@code marks} also
     * the message.
     */
    static SpellMatchDto shaped(SpellMatchDto match, SpellCheckHelp help) {
        return switch (help) {
            case SUGGESTIONS -> match;
            case MESSAGES -> new SpellMatchDto(match.offset(), match.length(), match.message(), List.of());
            case MARKS -> new SpellMatchDto(match.offset(), match.length(), "", List.of());
        };
    }

    static List<SpellMatchDto> matches(LanguageToolResponse response) {
        if (response == null || response.matches() == null) {
            return List.of();
        }
        return response.matches().stream()
                .filter(SpellCheckService::reported)
                .sorted(Comparator.comparingInt(LanguageToolResponse.Match::offset))
                .map(match -> new SpellMatchDto(match.offset(), match.length(), match.message(),
                        replacements(match)))
                .toList();
    }

    static boolean reported(LanguageToolResponse.Match match) {
        LanguageToolResponse.Rule rule = match.rule();
        if (rule == null) {
            return false;
        }
        if (rule.issueType() != null && ISSUE_TYPES.contains(rule.issueType())) {
            return true;
        }
        return rule.category() != null && rule.category().id() != null && CATEGORIES.contains(rule.category().id());
    }

    private static List<String> replacements(LanguageToolResponse.Match match) {
        if (match.replacements() == null) {
            return List.of();
        }
        return match.replacements().stream()
                .map(LanguageToolResponse.Replacement::value)
                .filter(value -> value != null)
                .limit(MAX_REPLACEMENTS)
                .toList();
    }
}
