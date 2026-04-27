package ru.normacontrol.infrastructure.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for automatic audit logging.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLogged {

    /**
     * Audit action code.
     *
     * @return action code
     */
    String action();

    /**
     * Resource type label.
     *
     * @return resource type
     */
    String resourceType() default "";
}
