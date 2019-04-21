package io.opentracing.contrib.dubbo.filter;

import com.alibaba.dubbo.rpc.RpcContext;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import io.opentracing.ActiveSpan;
import io.opentracing.contrib.dubbo.filter.service.Book;
import io.opentracing.contrib.dubbo.filter.service.BookService;
import io.opentracing.contrib.dubbo.filter.service.UserService;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;
import io.opentracing.util.ThreadLocalActiveSpanSource;

import static io.opentracing.contrib.dubbo.filter.AssertSpan.assertChildOfParent;
import static io.opentracing.contrib.dubbo.filter.AssertSpan.assertClientSpanTag;
import static io.opentracing.contrib.dubbo.filter.AssertSpan.assertHasArguments;
import static io.opentracing.contrib.dubbo.filter.AssertSpan.assertNotParent;
import static io.opentracing.contrib.dubbo.filter.AssertSpan.assertServerSpanTag;
import static io.opentracing.contrib.dubbo.filter.AssertSpan.assertSpanError;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class TracingTest {

    private static MockTracer mockTracer;
    private static ClassPathXmlApplicationContext registry;
    private static ClassPathXmlApplicationContext provider;
    private static ClassPathXmlApplicationContext consumer;
    private static BookService bookService;
    private static BookService bookServiceWithTimeout;
    private static BookService bookServiceWithAsync;
    private static UserService userService;
    private static final String SERVICE_CLASS_NAME = BookService.class.getName() + ".";
    private static final Book QUERY_CONDITION = new Book("book");

    @BeforeClass
    public static void startServer() throws InterruptedException {
        mockTracer = new MockTracer(new ThreadLocalActiveSpanSource(), MockTracer.Propagator.TEXT_MAP);
        GlobalTracer.register(mockTracer);


        registry = new ClassPathXmlApplicationContext("dubbo-registry.xml");
        registry.start();
        provider = new ClassPathXmlApplicationContext("dubbo-provider.xml");
        provider.start();
        consumer = new ClassPathXmlApplicationContext("dubbo-consumer.xml");
        consumer.start();

        bookService = consumer.getBean("bookService", BookService.class);
        bookServiceWithTimeout = consumer.getBean("bookServiceWithTimeout", BookService.class);
        bookServiceWithAsync = consumer.getBean("bookServiceWithAsync", BookService.class);
        userService = consumer.getBean(UserService.class);
    }

    @AfterClass
    public static void closeServer() {
        consumer.close();
        provider.close();
        registry.close();
    }

    @Before
    public void setUp() {
        mockTracer.reset();
    }

    @Test
    public void testThatNewChildSpanIsCreatedWhenParent() {
        assertThat(bookService.getBooks(QUERY_CONDITION).size(), equalTo(2));

        List<MockSpan> allSpans = mockTracer.finishedSpans();
        assertThat(allSpans.size(), equalTo(3));
        assertThat(allSpans.get(0).operationName(), equalTo("Get Books"));
        assertThat(allSpans.get(1).operationName(), equalTo(SERVICE_CLASS_NAME + "getBooks(Book)"));
        assertServerSpanTag(allSpans.get(1));
        assertThat(allSpans.get(2).operationName(), equalTo(SERVICE_CLASS_NAME + "getBooks(Book)"));
        assertClientSpanTag(allSpans.get(2));
        assertChildOfParent(allSpans.get(0), allSpans.get(1));
        assertChildOfParent(allSpans.get(1), allSpans.get(2));
        assertThat(mockTracer.activeSpan(), is(nullValue()));
    }

    @Test
    public void testThatNewChildSpanIsNotClosedWhenActive() {

        try (ActiveSpan scope = mockTracer.buildSpan("parent span").startActive()) {
            assertThat(bookService.getBooks(QUERY_CONDITION).size(), equalTo(2));
            assertThat(mockTracer.activeSpan(), not(nullValue()));

            List<MockSpan> allSpans = mockTracer.finishedSpans();
            assertThat(allSpans.size(), equalTo(3));
            assertThat(allSpans.get(0).operationName(), equalTo("Get Books"));
            assertThat(allSpans.get(1).operationName(), equalTo(SERVICE_CLASS_NAME + "getBooks(Book)"));
            assertServerSpanTag(allSpans.get(1));
            assertThat(allSpans.get(2).operationName(), equalTo(SERVICE_CLASS_NAME + "getBooks(Book)"));
            assertClientSpanTag(allSpans.get(2));

            assertChildOfParent(allSpans.get(0), allSpans.get(1));
            assertChildOfParent(allSpans.get(1), allSpans.get(2));
        }

        List<MockSpan> allSpans = mockTracer.finishedSpans();
        assertThat(allSpans.size(), equalTo(4));
        assertThat(allSpans.get(3).operationName(), equalTo("parent span"));
        assertNotParent(allSpans.get(3));

        assertThat(mockTracer.activeSpan(), is(nullValue()));
    }

    @Test
    public void testThatNewChildSpanIsCreatedWhenParentIsProviderInCaseOfFault() {
        try {
            bookService.getBooksWithException(QUERY_CONDITION);
            fail("Expected Exception to be raised");
        } catch (final Exception ex) {
            /* expected exception */
        }

        final List<MockSpan> allSpans = mockTracer.finishedSpans();
        assertThat(allSpans.size(), equalTo(2));
        assertThat(allSpans.get(0).operationName(), equalTo(SERVICE_CLASS_NAME + "getBooksWithException(Book)"));
        assertSpanError(allSpans.get(0));
        assertServerSpanTag(allSpans.get(0));
        assertHasArguments(allSpans.get(0));
        assertThat(allSpans.get(1).operationName(), equalTo(SERVICE_CLASS_NAME + "getBooksWithException(Book)"));
        assertSpanError(allSpans.get(1));
        assertClientSpanTag(allSpans.get(1));
        assertHasArguments(allSpans.get(1));

        assertChildOfParent(allSpans.get(0), allSpans.get(1));

        assertThat(mockTracer.activeSpan(), is(nullValue()));
    }

    @Test
    public void testThatNewChildSpanIsNotClosedWhenActiveOfAsyncClient() throws ExecutionException, InterruptedException {

        try (ActiveSpan scope = mockTracer.buildSpan("parent span").startActive()) {
            bookServiceWithAsync.getBooks(QUERY_CONDITION);
            Future<Collection<Book>> bookFuture = RpcContext.getContext().getFuture();
            assertThat(bookFuture.get().size(), equalTo(2));
            assertThat(mockTracer.activeSpan(), not(nullValue()));

            List<MockSpan> allSpans = mockTracer.finishedSpans();
            assertThat(allSpans.size(), equalTo(3));
            assertThat(allSpans.get(1).operationName(), equalTo("Get Books"));
            assertThat(allSpans.get(2).operationName(), equalTo(SERVICE_CLASS_NAME + "getBooks(Book)"));
            assertServerSpanTag(allSpans.get(2));

            assertThat(allSpans.get(0).operationName(), equalTo(SERVICE_CLASS_NAME + "getBooks(Book)"));
            assertClientSpanTag(allSpans.get(0));

            assertChildOfParent(allSpans.get(2), allSpans.get(0));
            assertChildOfParent(allSpans.get(1), allSpans.get(2));
        }

        List<MockSpan> allSpans = mockTracer.finishedSpans();
        assertThat(allSpans.size(), equalTo(4));
        assertThat(allSpans.get(3).operationName(), equalTo("parent span"));
        assertChildOfParent(allSpans.get(0), allSpans.get(3));
        assertNotParent(allSpans.get(3));
        assertThat(mockTracer.activeSpan(), is(nullValue()));
    }

    @Test
    public void testThatNewSpanIsCreatedInCaseOfConsumerTimeout() throws InterruptedException {
        try {
            bookServiceWithTimeout.getBooksWithTimeout(500);
            fail("Expected Exception to be raised");
        } catch (final Exception ex) {
            /* expected exception */
        }

        Thread.sleep(1000l);

        final List<MockSpan> allSpans = mockTracer.finishedSpans();
        assertThat(allSpans.size(), equalTo(8));
        assertThat(allSpans.get(0).operationName(), equalTo(SERVICE_CLASS_NAME + "getBooksWithTimeout(long)"));
        assertClientSpanTag(allSpans.get(0));
        assertSpanError(allSpans.get(0));
        assertThat(allSpans.get(1).operationName(), equalTo(SERVICE_CLASS_NAME + "getBooksWithTimeout(long)"));
        assertClientSpanTag(allSpans.get(0));
        assertSpanError(allSpans.get(0));
        assertThat(allSpans.get(2).operationName(), equalTo(SERVICE_CLASS_NAME + "getBooksWithTimeout(long)"));
        assertClientSpanTag(allSpans.get(0));
        assertSpanError(allSpans.get(0));
        assertThat(allSpans.get(3).operationName(), equalTo(SERVICE_CLASS_NAME + "getBooksWithTimeout(long)"));
        assertClientSpanTag(allSpans.get(0));
        assertSpanError(allSpans.get(0));

        assertThat(allSpans.get(4).operationName(), equalTo(SERVICE_CLASS_NAME + "getBooksWithTimeout(long)"));
        assertServerSpanTag(allSpans.get(4));
        assertThat(allSpans.get(5).operationName(), equalTo(SERVICE_CLASS_NAME + "getBooksWithTimeout(long)"));
        assertServerSpanTag(allSpans.get(5));
        assertThat(allSpans.get(6).operationName(), equalTo(SERVICE_CLASS_NAME + "getBooksWithTimeout(long)"));
        assertServerSpanTag(allSpans.get(6));
        assertThat(allSpans.get(7).operationName(), equalTo(SERVICE_CLASS_NAME + "getBooksWithTimeout(long)"));
        assertServerSpanTag(allSpans.get(7));

        assertChildOfParent(allSpans.get(4), allSpans.get(0));
        assertChildOfParent(allSpans.get(5), allSpans.get(0));
        assertChildOfParent(allSpans.get(6), allSpans.get(0));
        assertChildOfParent(allSpans.get(7), allSpans.get(0));

        assertThat(mockTracer.activeSpan(), is(nullValue()));
    }

    @Test
    public void testThatNewSpanIsCreatedInCaseOfServerError() {
        try {
            userService.delete("1");
            fail("Expected Exception to be raised");
        } catch (final Exception ex) {
            /* expected exception */
        }

        final List<MockSpan> allSpans = mockTracer.finishedSpans();
        assertThat(allSpans.size(), equalTo(0));
        assertThat(mockTracer.activeSpan(), is(nullValue()));
    }
}
