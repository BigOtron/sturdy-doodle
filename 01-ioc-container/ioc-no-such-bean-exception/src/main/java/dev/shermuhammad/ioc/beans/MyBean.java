package dev.shermuhammad.ioc.beans;

import java.util.List;

public class MyBean {
    private final List<String> dependencies = List.of("entityManager");


    public List<String> listDependencies() {
        return dependencies;
    }
}
