package com.zeta.user.config;

import com.zeta.user.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Bean di supporto per le espressioni @PreAuthorize.
 * Consente di verificare se l'utente autenticato è il proprietario della risorsa.
 */
@Service("userSecurity")
@RequiredArgsConstructor
public class UserSecurityService {

    private final UserRepository userRepository;

    /**
     * Verifica se il JWT dell'utente autenticato corrisponde all'utente con l'id dato.
     * Il confronto avviene tramite l'email claim del JWT vs email nel DB.
     *
     * @param userId         ID dell'utente risorsa
     * @param authentication Contesto di sicurezza Spring
     * @return true se l'utente autenticato è il proprietario della risorsa
     */
    public boolean isOwner(UUID userId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        String jwtEmail = jwt.getClaimAsString("email");
        if (jwtEmail == null) {
            return false;
        }
        return userRepository.findById(userId)
                .map(user -> jwtEmail.equalsIgnoreCase(user.getEmail()))
                .orElse(false);
    }
}
