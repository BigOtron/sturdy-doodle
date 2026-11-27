package dev.shermuhammad.ioc.beans;

import dev.shermuhammad.ioc.annotations.SimpleNameAnnotation;

public class MySimpleBean {
    @SimpleNameAnnotation("Bob")
    private String name;

    public MySimpleBean(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "MySimpleBean{" +
                "name='" + name + '\'' +
                '}';
    }
}
