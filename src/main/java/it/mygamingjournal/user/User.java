package it.mygamingjournal.user;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@SecondaryTable(name = "user_gaming_accounts", pkJoinColumns = @PrimaryKeyJoinColumn(name = "user_id"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String username;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    private String bio;

    @Column(nullable = false)
    private int experience;

    @Column(nullable = false)
    private int level;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20)
    private Set<Role> roles = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_languages", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "language_code", length = 10)
    @OrderBy
    private Set<String> languages = new HashSet<>();

    @Embedded
    private GamingAccounts gamingAccounts = new GamingAccounts();

    public User(String username, String email, String passwordHash, Instant createdAt) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = username;
        this.createdAt = createdAt;
        this.roles.add(Role.USER);
    }

    public GamingAccounts getGamingAccounts() {
        // Hibernate loads an all-null embeddable as null.
        if (gamingAccounts == null) {
            gamingAccounts = new GamingAccounts();
        }
        return gamingAccounts;
    }
}
