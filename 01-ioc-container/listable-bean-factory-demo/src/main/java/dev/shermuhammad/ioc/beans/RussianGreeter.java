package dev.shermuhammad.ioc.beans;

public class RussianGreeter implements Greeter{
    @Override
    public String greet() {
        return "Privet";
    }
}
