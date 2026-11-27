package dev.shermuhammad.ioc.beans;

public class Greeter {
    private String text;

    public Greeter(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
