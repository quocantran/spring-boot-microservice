package com.moviebooking.common.auth;

import java.lang.annotation.*;

/**
 * Đánh dấu endpoint yêu cầu xác thực JWT.
 * Tương đương @UseGuards(JwtAuthGuard) trong NestJS.
 * Khi annotation này được đặt trên method hoặc class, RolesInterceptor sẽ
 * throw UnauthorizedException nếu không có token hợp lệ.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Authenticated {
}
