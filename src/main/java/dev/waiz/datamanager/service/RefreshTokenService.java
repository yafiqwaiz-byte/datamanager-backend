package dev.waiz.datamanager.service;

import dev.waiz.datamanager.model.refreshtoken;
import dev.waiz.datamanager.model.account;
import dev.waiz.datamanager.repository.RefreshTokenRepository;
import dev.waiz.datamanager.repository.accountrepository;
import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {


    private static final long REFRESH_TOKEN_EXPIRY_MS = 7L*24*60*60*1000;


    private final RefreshTokenRepository refreshTokenRepository;
    private final accountrepository accountRepository;


     /**
     * Creates and persists a new refresh token for the given account.
     * Any previously issued tokens remain valid until explicitly revoked
     * (single-device) or you can call revokeAll first (single-session).
     */

    @Transactional
    public refreshtoken createRefreshToken(account account){
        refreshtoken token = new refreshtoken();
        token.setToken(generateSecureToken());
        token.setAccount(account);
        token.setExpiryDate(Instant.now().plusMillis(REFRESH_TOKEN_EXPIRY_MS));
        token.setRevoked(false);
        return refreshTokenRepository.save(token);
    }

    /**
     * Looks up a refresh token string, validates it, then performs rotation:
     *   1. Marks the old token as revoked
     *   2. Issues a brand-new refresh token
     * Returns the new RefreshToken, or empty if invalid/expired/revoked.
     *
     * If the token being presented was ALREADY revoked (i.e. it was already
     * rotated once before), that's a strong signal of token theft/replay —
     * a legitimate client only ever holds the latest token. In that case we
     * revoke every token for the account AND invalidate any still-live
     * access tokens via revokeAllTokensForAccount().
     */

    @Transactional
    public Optional<refreshtoken> rotateRefreshToken(String tokenString){
        Optional<refreshtoken> opt = refreshTokenRepository.findByToken(tokenString);

        if (opt.isEmpty()) return Optional.empty();

        refreshtoken existing = opt.get();

        if (existing.isRevoked() || existing.isExpired()){
            if (existing.isRevoked()) {
                revokeAllTokensForAccount(existing.getAccount());
            }
            return Optional.empty();
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        refreshtoken newToken = createRefreshToken(existing.getAccount());
        return Optional.of(newToken);
    }

    /**
     * Revoke all refresh tokens for an account (logout / password change /
     * reuse-detected theft), and stamp tokensValidAfter so any access token
     * issued before this instant is rejected by JwtAuthFilter on its next use —
     * closing the gap where a still-live JWT would otherwise remain valid
     * until its natural expiry even after the session is revoked.
     */
    @Transactional
    public void revokeAllTokensForAccount(account account){
        refreshTokenRepository.revokeAllByAccount(account);
        account.setTokensValidAfter(Instant.now());
        accountRepository.save(account);
    }

     /** Find and return account from a valid (non-expired, non-revoked) token string */
    @Transactional(readOnly = true)
    public Optional<account> getAccountFromToken(String tokenString) {
        return refreshTokenRepository.findByToken(tokenString)
            .filter(t -> !t.isRevoked() && !t.isExpired())
            .map(r -> r.getAccount());
    }

    /** Scheduled cleanup — runs every 24 hours */
    @Scheduled(fixedRate = 24*60*60*1000)
    @Transactional
    public void purgeExpiredToken(){
        refreshTokenRepository.deleteAllExpired();
    }

    private String generateSecureToken(){
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}