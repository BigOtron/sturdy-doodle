package dev.shermuhammad.ioc.beans;

public class Car {
    private final Engine engine;

    public Car(Engine engine) {
        this.engine = engine;
        System.out.println("Car created");
    }
}
