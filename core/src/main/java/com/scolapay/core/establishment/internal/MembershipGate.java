package com.scolapay.core.establishment.internal;

import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.shared.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Barrière de la surface /establishments/{id}/** : aucune route de cette surface ne peut
 * oublier la vérification d'adhésion. Elle passe avant le décodage et la validation du
 * corps, si bien qu'un non-membre obtient toujours le même 404, quelle que soit sa requête.
 * Les services vérifient ensuite la permission précise de chaque opération.
 */
@Component
class MembershipGate implements HandlerInterceptor {

    private final EstablishmentAccess access;

    MembershipGate(EstablishmentAccess access) {
        this.access = access;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        access.requireMember(establishmentIdOf(request));
        return true;
    }

    private static UUID establishmentIdOf(HttpServletRequest request) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        Object value = variables instanceof Map<?, ?> map ? map.get("establishmentId") : null;
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException invalid) {
            throw ApiException.notFound("ESTABLISHMENT_NOT_FOUND", "Établissement introuvable.");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Registration implements WebMvcConfigurer {

        private final MembershipGate gate;

        Registration(MembershipGate gate) {
            this.gate = gate;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(gate).addPathPatterns("/api/v2/establishments/**");
        }
    }
}
