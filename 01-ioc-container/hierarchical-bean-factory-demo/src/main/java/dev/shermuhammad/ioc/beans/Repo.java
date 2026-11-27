package dev.shermuhammad.ioc.beans;

public class Repo {
    private String owner;

    public Repo(String owner) {
        this.owner = owner;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
}
