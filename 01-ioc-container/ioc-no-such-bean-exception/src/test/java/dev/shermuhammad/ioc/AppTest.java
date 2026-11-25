package dev.shermuhammad.ioc;

import dev.shermuhammad.ioc.beans.MyBean;
import dev.shermuhammad.ioc.config.NoSuchBeanConfig;
import dev.shermuhammad.ioc.config.NoUniqueBeanConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class AppTest {
    @Test
    void noSuchBean() {
        try(var context = new AnnotationConfigApplicationContext(NoSuchBeanConfig.class)) {
            var exception = Assertions.assertThrowsExactly(NoSuchBeanDefinitionException.class,
                    () -> context.getBean(MyBean.class));
            Assertions.assertEquals(MyBean.class, exception.getBeanType());
        }
    }

    @Test
    void noUniqueBean() {
        try(var context = new AnnotationConfigApplicationContext(NoUniqueBeanConfig.class)) {
            var exception = Assertions.assertThrowsExactly(NoUniqueBeanDefinitionException.class,
                    () -> context.getBean(MyBean.class));
            Assertions.assertEquals(2, exception.getNumberOfBeansFound());
        }
    }
}
