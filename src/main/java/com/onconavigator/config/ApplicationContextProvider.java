package com.onconavigator.config;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Static accessor for the Spring ApplicationContext.
 *
 * <p>JPA {@link jakarta.persistence.AttributeConverter} instances are not managed by
 * Spring — they are instantiated by Hibernate. This provider allows converters like
 * {@link com.onconavigator.security.EncryptionConverter} to access Spring-managed beans
 * (e.g., the {@code SecretKey} bean) without constructor injection.
 *
 * <p>This pattern is intentionally narrow: only {@code EncryptionConverter} and similar
 * JPA-lifecycle classes should use it. All other beans must use standard Spring injection.
 */
@Component
public class ApplicationContextProvider implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext ctx) {
        context = ctx;
    }

    /**
     * Returns a Spring-managed bean of the given type.
     *
     * @param beanClass the class of the bean to look up
     * @param <T>       the bean type
     * @return the bean instance
     * @throws IllegalStateException if the application context has not been initialized
     */
    public static <T> T getBean(Class<T> beanClass) {
        if (context == null) {
            throw new IllegalStateException(
                    "ApplicationContext has not been initialized. "
                    + "Cannot look up bean: " + beanClass.getName());
        }
        return context.getBean(beanClass);
    }
}
