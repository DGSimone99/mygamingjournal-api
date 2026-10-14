package it.mygamingjournal.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GamingAccounts {

    @Column(table = "user_gaming_accounts", length = 100)
    private String steam;

    @Column(table = "user_gaming_accounts", length = 100)
    private String psn;

    @Column(table = "user_gaming_accounts", length = 100)
    private String xbox;

    @Column(table = "user_gaming_accounts", length = 100)
    private String nintendo;

    @Column(table = "user_gaming_accounts", length = 100)
    private String epic;

    @Column(table = "user_gaming_accounts", length = 100)
    private String riot;

    @Column(table = "user_gaming_accounts", length = 100)
    private String discord;
}
