package dev.shermuhammad.ioc.bpp;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

public class SimpleBeanPostProcessor implements BeanPostProcessor {
    @Override
    public @Nullable Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        System.out.println("  BPP after-init: " + beanName + " (" + bean.getClass().getSimpleName() + ")");
        return bean;
    }
}
