package dev.shermuhammad.ioc.config;

import dev.shermuhammad.ioc.beans.MyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NoUniqueBeanConfig {
    @Bean
    public MyBean getGenericBean() {
        return new MyBean();
    }

    @Bean
    public MyBean getSpecialBean() {
        return new MyBean();
    }
}
