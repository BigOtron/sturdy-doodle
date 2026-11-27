package dev.shermuhammad.ioc;

import dev.shermuhammad.ioc.beans.Greeter;
import dev.shermuhammad.ioc.beans.Repo;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import java.util.Arrays;

public class App {
    public static void main(String[] args) {
        DefaultListableBeanFactory parent = new DefaultListableBeanFactory();
        parent.registerBeanDefinition("greeter",
                new RootBeanDefinition(Greeter.class, () -> new Greeter("Hello, from PARENT greeter")));
        parent.registerBeanDefinition("repo",
                new RootBeanDefinition(Repo.class, () -> new Repo("PARENT")));

        DefaultListableBeanFactory child = new DefaultListableBeanFactory(parent);
        child.registerBeanDefinition("greeter",
                new RootBeanDefinition(Greeter.class, () -> new Greeter("Hello, from CHILD greeter")));

        HierarchicalBeanFactory h = child;
        System.out.println("parent of child = " + (h.getParentBeanFactory() == parent));
        System.out.println("child greeter    = " + child.getBean("greeter", Greeter.class).getText());
        System.out.println("child repo       = " + child.getBean("repo", Repo.class).getOwner());
        System.out.println("containsBean(repo)      = " + child.containsBean("repo"));
        System.out.println("containsLocalBean(repo) = " + child.containsLocalBean("repo"));
        System.out.println("parent greeter   = " + parent.getBean("greeter", Greeter.class).getText());

        System.out.println("child.getBeanNamesForType(Repo) = "
                + Arrays.toString(child.getBeanNamesForType(Repo.class)));
        System.out.println("including ancestors             = "
                + Arrays.toString(BeanFactoryUtils.beanNamesForTypeIncludingAncestors(child, Repo.class)));
        System.out.println("child.getBean(Repo.class)       = " + child.getBean(Repo.class).getOwner());
        System.out.println("same instance    = " + (child.getBean("repo") == parent.getBean("repo")));

        try {
            child.setParentBeanFactory(new DefaultListableBeanFactory());
        } catch (IllegalStateException e) {
            System.out.println("setParent again  -> " + e.getMessage().split(":")[0]);
        }
    }
}
