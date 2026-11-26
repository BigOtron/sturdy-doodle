package dev.shermuhammad.ioc;

import dev.shermuhammad.ioc.beans.Car;
import dev.shermuhammad.ioc.beans.ClockFactoryBean;
import dev.shermuhammad.ioc.beans.Engine;
import dev.shermuhammad.ioc.bpp.SimpleBeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

public class BeanFactoryDemo {
    public static void main(String[] args) {
        var bf = new DefaultListableBeanFactory();

        bf.registerBeanDefinition("engine",
                BeanDefinitionBuilder.genericBeanDefinition(Engine.class).getBeanDefinition());
        bf.registerBeanDefinition("car",
                BeanDefinitionBuilder.genericBeanDefinition(Car.class)
                        .addConstructorArgReference("engine").getBeanDefinition());
        bf.registerBeanDefinition("clock",
                BeanDefinitionBuilder.genericBeanDefinition(ClockFactoryBean.class).getBeanDefinition());
        bf.registerAlias("car", "vehicle");

        System.out.println("registered, nothing created yet. car singleton? " + bf.isSingleton("car"));

        bf.addBeanPostProcessor(new SimpleBeanPostProcessor());

        Car car = bf.getBean("vehicle", Car.class);
        System.out.println("same instance by type? " + (car == bf.getBean(Car.class)));
        System.out.println("clock  -> " + bf.getBean("clock").getClass().getSimpleName());
        System.out.println("&clock -> " + bf.getBean("&clock").getClass().getSimpleName());
    }
}