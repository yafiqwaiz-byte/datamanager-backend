package dev.waiz.datamanager.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.waiz.datamanager.model.account;
import dev.waiz.datamanager.model.refreshtoken;


public interface RefreshTokenRepository extends JpaRepository<refreshtoken,UUID> {

    @Query("SELECT r FROM refreshtoken r JOIN FETCH r.account WHERE r.token = :token")
    Optional<refreshtoken> findByToken(@Param("token")String token);

    @Modifying
    @Query("""
            UPDATE refreshtoken r SET r.revoked 
            = true WHERE r.account =:account AND r.revoked = false
            """)
    void revokeAllByAccount(account account);


    @Modifying
    @Query("""
            DELETE FROM refreshtoken r WHERE 
            r.expiryDate < CURRENT_TIMESTAMP
            """)
    void deleteAllExpired();
}
