package com.jansetu.security;

import com.jansetu.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 401/403 responses in the standard problem format, with safe and consistent messages. */
@Component
public class ProblemSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ProblemResponseWriter writer;

    public ProblemSecurityHandlers(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        if (ex instanceof InvalidBearerTokenException || ex instanceof DisabledException
                || ex.getClass().getSimpleName().contains("OAuth2")) {
            response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
            writer.write(request, response, ErrorCode.INVALID_TOKEN, "The access token is invalid, expired or revoked.");
            return;
        }
        response.setHeader("WWW-Authenticate", "Bearer");
        writer.write(request, response, ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is required.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        writer.write(request, response, ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action.");
    }
}
