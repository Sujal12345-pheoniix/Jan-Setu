package com.jansetu.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI janSetuOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("JanSetu API")
                        .version("v1")
                        .description("""
                                JanSetu service delivery and grievance management API.

                                **Portfolio simulation only.** JanSetu is not an official government portal, \
                                has no real government integrations and must only be used with synthetic data.

                                Authentication: obtain an access token from `POST /api/v1/auth/login` and send it as \
                                `Authorization: Bearer <token>`. The refresh token is an HttpOnly cookie; refresh and \
                                logout calls must also send the header `X-JanSetu-Client: web`.

                                Errors use RFC 9457 Problem Details with `errorCode`, `requestId` and optional `fieldErrors`.
                                """))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
