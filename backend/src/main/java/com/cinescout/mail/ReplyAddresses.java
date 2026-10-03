package com.cinescout.mail;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Each outreach draft's own reply address, {@code scout+<token>@<reply domain>}, and the token back out of an
 * address. Responses are built without Spring ({@code OutreachDraftResponse.from}), so the addresses in use are
 * also kept here, set when the application starts; until then (in plain unit tests) there are none.
 */
public final class ReplyAddresses {

    private static volatile ReplyAddresses current = new ReplyAddresses(null, "scout");

    private final String domain;
    private final String localPart;
    private final Pattern address;

    public ReplyAddresses(String domain, String localPart) {
        this.domain = domain == null || domain.isBlank() ? null : domain.strip().toLowerCase(Locale.ROOT);
        this.localPart = localPart == null || localPart.isBlank() ? "scout" : localPart.strip().toLowerCase(Locale.ROOT);
        this.address = this.domain == null ? null
                : Pattern.compile("(?i)^" + Pattern.quote(this.localPart) + "\\+([0-9a-f]{16,64})@" + Pattern.quote(this.domain) + "$");
    }

    public static ReplyAddresses current() {
        return current;
    }

    static void install(ReplyAddresses addresses) {
        current = addresses;
    }

    /** The draft's reply address, or null while reply addresses are off. */
    public String address(String token) {
        return domain == null ? null : localPart + "+" + token + "@" + domain;
    }

    /** The token in an address like {@code scout+<token>@<domain>} (any case), if it is one of ours. */
    public Optional<String> tokenOf(String emailAddress) {
        if (address == null || emailAddress == null) {
            return Optional.empty();
        }
        Matcher matcher = address.matcher(emailAddress.strip());
        return matcher.matches() ? Optional.of(matcher.group(1).toLowerCase(Locale.ROOT)) : Optional.empty();
    }
}
