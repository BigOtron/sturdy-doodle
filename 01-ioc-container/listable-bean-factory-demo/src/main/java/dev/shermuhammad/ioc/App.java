package dev.shermuhammad.ioc;

import dev.shermuhammad.ioc.annotations.MyCustomAnnotation;
import dev.shermuhammad.ioc.beans.BeanWithCustomAnnotation;
import dev.shermuhammad.ioc.beans.EnglishGreeter;
import dev.shermuhammad.ioc.beans.GermanGreeter;
import dev.shermuhammad.ioc.beans.Greeter;
import dev.shermuhammad.ioc.beans.RussianGreeter;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import java.util.Arrays;

public class App {
    public static void main(String[] args) {
        var bf = new DefaultListableBeanFactory();
        bf.registerBeanDefinition("englishGreeter", new RootBeanDefinition(EnglishGreeter.class));
        bf.registerBeanDefinition("germanGreeter", new RootBeanDefinition(GermanGreeter.class));
        bf.registerBeanDefinition("russianGreeter", new RootBeanDefinition(RussianGreeter.class));

        bf.registerSingleton("spanishGreeter", (Greeter) () -> "Hola");
        System.out.println("bean definition count: " + bf.getBeanDefinitionCount());
        System.out.println("bean definition names: " + Arrays.toString(bf.getBeanDefinitionNames()));
        System.out.println("hasDef(spanishGreeter) = " + bf.containsBeanDefinition("spanishGreeter"));
        System.out.println("containsBean(spanishGreeter) = " + bf.containsBean("spanishGreeter"));
        System.out.println("namesForType = " + Arrays.toString(bf.getBeanNamesForType(Greeter.class)));
        System.out.println("singletonsSoFar = " + Arrays.toString(bf.getSingletonNames()));
        System.out.println("beansOfType = " + bf.getBeansOfType(Greeter.class).keySet());
        System.out.println("singletonsNow   = " + Arrays.toString(bf.getSingletonNames()));

        bf.registerBeanDefinition("beanWithCustomAnnotation", new RootBeanDefinition(BeanWithCustomAnnotation.class));
        System.out.println("bean names annotated with custom annotation: " +
                Arrays.toString(bf.getBeanNamesForAnnotation(MyCustomAnnotation.class)));
        System.out.println("singletonsSoFar = " + Arrays.toString(bf.getSingletonNames()));
        System.out.println("bean annotated with custom annotation: " +
                bf.getBeansWithAnnotation(MyCustomAnnotation.class).keySet());
        System.out.println("singletonsSoFar = " + Arrays.toString(bf.getSingletonNames()));
    }
}
