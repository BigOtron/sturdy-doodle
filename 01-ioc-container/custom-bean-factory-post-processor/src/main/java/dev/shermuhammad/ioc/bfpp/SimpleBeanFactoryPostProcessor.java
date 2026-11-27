package dev.shermuhammad.ioc.bfpp;

import dev.shermuhammad.ioc.annotations.SimpleNameAnnotation;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

public class SimpleBeanFactoryPostProcessor implements BeanFactoryPostProcessor {
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        String[] targetBeanNames = beanFactory.getBeanNamesForAnnotation(SimpleNameAnnotation.class);
        for (String beanName : targetBeanNames) {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            MutablePropertyValues propertyValues = beanDefinition.getPropertyValues();
            for (PropertyValue propertyValue : propertyValues.getPropertyValueList()) {
                propertyValue.getClass().
            }
        }
    }
}
