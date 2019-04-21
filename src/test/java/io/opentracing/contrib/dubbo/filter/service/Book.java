package io.opentracing.contrib.dubbo.filter.service;

import java.io.Serializable;

public class Book implements Serializable {

    private String title;
    private String id;

    public Book() {

    }

    public Book(String id) {
        this.id = id;
    }

    public Book(String title, String id) {
        this.title = title;
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setId(String i) {
        id = i;
    }

    public String getId() {
        return id;
    }
}
