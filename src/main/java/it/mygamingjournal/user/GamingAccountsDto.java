package it.mygamingjournal.user;

import jakarta.validation.constraints.Size;

public record GamingAccountsDto(
        @Size(max = 100) String steam,
        @Size(max = 100) String psn,
        @Size(max = 100) String xbox,
        @Size(max = 100) String nintendo,
        @Size(max = 100) String epic,
        @Size(max = 100) String riot,
        @Size(max = 100) String discord) {

    static GamingAccountsDto from(GamingAccounts accounts) {
        return new GamingAccountsDto(accounts.getSteam(), accounts.getPsn(), accounts.getXbox(),
                accounts.getNintendo(), accounts.getEpic(), accounts.getRiot(), accounts.getDiscord());
    }

    GamingAccounts toEmbeddable() {
        return new GamingAccounts(clean(steam), clean(psn), clean(xbox), clean(nintendo), clean(epic),
                clean(riot), clean(discord));
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
