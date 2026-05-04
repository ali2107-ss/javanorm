package ru.normacontrol.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class ForceHttpsFilter extends OncePerRequestFilter {

    @Value("${app.security.force-https:false}")
    private boolean forceHttps;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!forceHttps || isHttps(request) || isHealthCheck(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String query = request.getQueryString();
        String redirectUrl = "https://" + request.getServerName()
                + request.getRequestURI()
                + (query == null ? "" : "?" + query);
        response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
        response.setHeader("Location", redirectUrl);
    }

    private boolean isHttps(HttpServletRequest request) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return request.isSecure() || "https".equalsIgnoreCase(forwardedProto);
    }

    private boolean isHealthCheck(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }
}
