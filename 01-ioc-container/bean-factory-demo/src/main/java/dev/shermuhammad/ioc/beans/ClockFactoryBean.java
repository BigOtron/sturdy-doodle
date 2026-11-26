package dev.shermuhammad.ioc.beans;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.FactoryBean;

import java.time.Clock;

public class ClockFactoryBean implements FactoryBean<Clock> {
    @Override
    public @Nullable Clock getObject() throws Exception {
        return Clock.systemUTC();
    }

    @Override
    public @Nullable Class<?> getObjectType() {
        return Clock.class;
    }
}
