package com.moviebooking.common.auth;

import com.moviebooking.common.exception.CustomExceptions.ForbiddenException;
import com.moviebooking.common.exception.CustomExceptions.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
public class RolesInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // Check @Authenticated annotation
        boolean requiresAuth = handlerMethod.hasMethodAnnotation(Authenticated.class)
                || handlerMethod.getBeanType().isAnnotationPresent(Authenticated.class);

        // Check @Roles annotation
        Roles rolesAnnotation = handlerMethod.getMethodAnnotation(Roles.class);
        if (rolesAnnotation == null) {
            rolesAnnotation = handlerMethod.getBeanType().getAnnotation(Roles.class);
        }

        boolean requiresRoles = rolesAnnotation != null && rolesAnnotation.value().length > 0;

        // If neither @Authenticated nor @Roles, allow through
        if (!requiresAuth && !requiresRoles) {
            return true;
        }

        // Both annotations require a valid user
        JwtPayload user = (JwtPayload) request.getAttribute(JwtAuthFilter.USER_ATTRIBUTE);
        if (user == null) {
            throw new UnauthorizedException("Thiếu token xác thực");
        }

        // If only @Authenticated (no @Roles), authentication is sufficient
        if (!requiresRoles) {
            return true;
        }

        // Check role authorization
        if (user.getRole() == null) {
            throw new ForbiddenException("Không có quyền truy cập");
        }

        boolean hasRole = Arrays.asList(rolesAnnotation.value()).contains(user.getRole());
        if (!hasRole) {
            throw new ForbiddenException(String.format(
                    "Yêu cầu quyền: %s. Quyền hiện tại: %s",
                    String.join(", ", rolesAnnotation.value()),
                    user.getRole()
            ));
        }

        return true;
    }
}
