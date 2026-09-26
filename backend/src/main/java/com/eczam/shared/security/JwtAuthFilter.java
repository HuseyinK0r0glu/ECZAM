package com.eczam.shared.security;

import com.eczam.users.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final UserRepository users;

    public JwtAuthFilter(JwtService jwt, UserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    // OncePerRequestFilter's default (true) skips this filter on the async dispatch
    // that finalizes a streaming (SseEmitter) response — e.g. ChatController's SSE
    // endpoint, whose completion runs on a background executor thread instead of the
    // original request thread. SecurityContextHolder is thread-local and nothing else
    // re-populates it for that dispatch, so Spring Security's authorization check
    // then runs against an empty context and overwrites the whole response with a 401
    // — even though the request was already correctly authenticated. Re-running this
    // filter re-derives the same authentication from the (still present) Authorization
    // header instead.
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    // Same reasoning as shouldNotFilterAsyncDispatch(), for the error-flavored async
    // dispatch a timed-out or abruptly-erroring streaming response can also trigger.
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req, @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                UUID userId = jwt.verify(header.substring(7), JwtService.TokenType.ACCESS);

                // Load role from DB to honour real-time role changes and soft-deletes.
                // Cached in SecurityContext per request — no repeated DB calls.
                var user = users.findById(userId).orElse(null);
                if (user != null && !user.isDeleted()) {
                    String role = user.getRole().authority();
                    var auth = new UsernamePasswordAuthenticationToken(
                            userId, null, AuthorityUtils.createAuthorityList(role));
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }
}
