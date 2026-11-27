package dev.shermuhammad.ioc.beans;

import dev.shermuhammad.ioc.annotations.MyCustomAnnotation;

@MyCustomAnnotation
public class BeanWithCustomAnnotation {
    private final String someValue = "Hello, from bean with custom annotation";

    public String getSomeValue() {
        return someValue;
    }
}
