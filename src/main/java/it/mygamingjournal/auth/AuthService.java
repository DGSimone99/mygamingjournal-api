package it.mygamingjournal.auth;

import it.mygamingjournal.common.error.ConflictException;
import it.mygamingjournal.user.User;
import it.mygamingjournal.user.UserRepository;
import it.mygamingjournal.user.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("Username already taken");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email already registered");
        }
        User user = users.save(new User(username, email, passwordEncoder.encode(request.password()), clock.instant()));
        return new AuthResponse(jwtService.generateToken(user.getId()), UserSummary.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String identifier = request.usernameOrEmail().trim();
        // Usernames cannot contain '@', so the identifier type is unambiguous.
        Optional<User> user = identifier.contains("@")
                ? users.findByEmailIgnoreCase(identifier)
                : users.findByUsernameIgnoreCase(identifier);
        return user
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .map(found -> new AuthResponse(jwtService.generateToken(found.getId()), UserSummary.from(found)))
                .orElseThrow(InvalidCredentialsException::new);
    }
}
