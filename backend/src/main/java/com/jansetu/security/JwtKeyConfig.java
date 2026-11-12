package com.jansetu.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

/**
 * RS256 signing keys for access tokens. Keys come from {@code JWT_PRIVATE_KEY}/{@code JWT_PUBLIC_KEY}. Only when
 * {@code allow-ephemeral-keys} is enabled (local and test profiles) is a throw-away key pair generated instead.
 */
@Configuration
public class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);
    static final String TOKEN_USE_CLAIM = "token_use";
    static final String TOKEN_USE_ACCESS = "access";

    @Bean
    RSAKey jwtSigningKey(SecurityProperties properties) throws com.nimbusds.jose.JOSEException {
        SecurityProperties.Jwt jwt = properties.jwt();
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;
        if (StringUtils.hasText(jwt.privateKey()) && StringUtils.hasText(jwt.publicKey())) {
            privateKey = parsePrivateKey(jwt.privateKey());
            publicKey = parsePublicKey(jwt.publicKey());
        } else if (jwt.allowEphemeralKeys()) {
            log.warn("JWT keys not configured - generating an ephemeral RSA key pair. Tokens will not survive a restart. "
                    + "This is acceptable only for local development and tests.");
            KeyPair pair = generate();
            privateKey = (RSAPrivateKey) pair.getPrivate();
            publicKey = (RSAPublicKey) pair.getPublic();
        } else {
            throw new IllegalStateException("JWT_PRIVATE_KEY and JWT_PUBLIC_KEY must be configured for this environment.");
        }
        return new RSAKey.Builder(publicKey).privateKey(privateKey).keyIDFromThumbprint().build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey jwtSigningKey, SecurityProperties properties) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer())); // exp, nbf, iss
        validators.add(claimValidator("aud", jwt -> jwt.getAudience() != null
                && jwt.getAudience().contains(properties.jwt().audience())));
        validators.add(claimValidator(TOKEN_USE_CLAIM, jwt -> TOKEN_USE_ACCESS.equals(jwt.getClaimAsString(TOKEN_USE_CLAIM))));
        validators.add(claimValidator("sub", jwt -> StringUtils.hasText(jwt.getSubject())));
        validators.add(claimValidator("exp", jwt -> jwt.getExpiresAt() != null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> claimValidator(String claim, java.util.function.Predicate<Jwt> test) {
        OAuth2Error error = new OAuth2Error("invalid_token", "The " + claim + " claim is invalid", null);
        return jwt -> test.test(jwt) ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(error);
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static RSAPrivateKey parsePrivateKey(String pem) {
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decodePem(pem)));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("JWT_PRIVATE_KEY is not a valid PKCS#8 RSA private key", e);
        }
    }

    static RSAPublicKey parsePublicKey(String pem) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decodePem(pem)));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("JWT_PUBLIC_KEY is not a valid X.509 RSA public key", e);
        }
    }

    private static byte[] decodePem(String pem) {
        String base64 = pem.replace("\\n", "\n")
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
