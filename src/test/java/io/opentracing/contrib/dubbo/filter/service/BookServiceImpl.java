package io.opentracing.contrib.dubbo.filter.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

import io.opentracing.ActiveSpan;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

public class BookServiceImpl implements BookService {

    private final Tracer tracer;

    public BookServiceImpl() {
        tracer = GlobalTracer.get();
    }

    @Override
    public Collection<Book> getBooks(Book book) {
        try (ActiveSpan span = tracer.buildSpan("Get Books").startActive()) {
            return Arrays.asList(
                    new Book("book A", UUID.randomUUID().toString()),
                    new Book("book B", UUID.randomUUID().toString())
            );
        }
    }

    @Override
    public Collection<Book> getBooksWithTimeout(long timeout) {
        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return Arrays.asList(
                new Book("book A", UUID.randomUUID().toString()),
                new Book("book B", UUID.randomUUID().toString())
        );
    }

    @Override
    public Collection<Book> getBooksWithException(Book book) {
        throw new UnsupportedOperationException();
    }
}
