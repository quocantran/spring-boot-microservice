package com.moviebooking.common.auth;

import java.lang.annotation.*;

/** Marks endpoint requiring JWT authentication. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Authenticated {
}
