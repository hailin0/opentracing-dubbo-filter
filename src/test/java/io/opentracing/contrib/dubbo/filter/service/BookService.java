package io.opentracing.contrib.dubbo.filter.service;

import java.util.Collection;

public interface BookService {

    Collection<Book> getBooks(Book book);

    Collection<Book> getBooksWithTimeout(long timeout);

    Collection<Book> getBooksWithException(Book book);
}