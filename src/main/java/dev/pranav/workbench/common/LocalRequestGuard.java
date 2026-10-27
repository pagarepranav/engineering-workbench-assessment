package dev.pranav.workbench.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.UUID;

/** Loopback binding plus Origin/Host checks protect the local single-user control plane. */
@Component
public class LocalRequestGuard extends OncePerRequestFilter {
    private static final Set<String> HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        res.setHeader("X-Request-Id", UUID.randomUUID().toString());
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("Referrer-Policy", "no-referrer");
        res.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
        if (!HOSTS.contains(req.getServerName())) { res.sendError(403); return; }
        boolean mutation = !Set.of("GET", "HEAD", "OPTIONS").contains(req.getMethod());
        if (mutation) {
            String origin = req.getHeader("Origin");
            if (origin != null) {
                try {
                    URI uri = URI.create(origin);
                    int port = uri.getPort() == -1 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort();
                    if (!uri.getHost().equals(req.getServerName()) || port != req.getServerPort() || !uri.getScheme().equals(req.getScheme())) { res.sendError(403); return; }
                } catch (RuntimeException ex) { res.sendError(403); return; }
            }
            if (!"application/json".equalsIgnoreCase(req.getContentType() == null ? "" : req.getContentType().split(";")[0])) { res.sendError(415); return; }
            if (req.getContentLengthLong() > 1_000_000) { res.sendError(413); return; }
        }
        chain.doFilter(req, res);
    }
}
