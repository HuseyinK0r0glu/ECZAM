package com.eczam.shared.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title       = "ECZAM API",
        version     = "1.0",
        description = """
            Smart medication management API.

            **Authentication**
            Most endpoints require an `Authorization: Bearer <token>` header.
            Obtain an access token from `POST /auth/register` or `POST /auth/login`.
            Access tokens expire in 2 hours. Use `POST /auth/refresh` with your refresh token
            to get a new pair without re-entering credentials.

            **Rate limits**
            Auth endpoints (register, login, Google, password-reset): 10 requests/minute per IP.
            Sensitive operations (change-password, resend-verification, change-email): 5 requests/15 minutes.
            AI chat: 20 requests/minute.

            **Response envelope**
            Every response has the shape `{ data, meta, error }`.
            Successful responses populate `data`; failures populate `error` with a machine-readable `code`.

            **Validation errors**
            Invalid inputs return `422` with `error.fields` containing per-field messages.
            """,
        contact = @Contact(name = "ECZAM Team", email = "admin@eczam.app")
    ),
    servers = {
        @Server(url = "/api/v1", description = "Default — context path prefix"),
        @Server(url = "http://localhost:8080/api/v1", description = "Local dev")
    },
    security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
    name        = "bearerAuth",
    type        = SecuritySchemeType.HTTP,
    scheme      = "bearer",
    bearerFormat = "JWT",
    description = "Paste the access token from POST /auth/login or POST /auth/register"
)
public class OpenApiConfig {
}
