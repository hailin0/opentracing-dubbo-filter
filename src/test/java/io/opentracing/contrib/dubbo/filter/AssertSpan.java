package io.opentracing.contrib.dubbo.filter;

import io.opentracing.mock.MockSpan;
import io.opentracing.tag.Tags;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AssertSpan {

    public static void assertChildOfParent(MockSpan child, MockSpan.MockContext parent) {
        assertNotEquals(0, child.parentId());
        assertEquals(parent.spanId(), child.parentId());
    }

    public static void assertChildOfParent(MockSpan child, MockSpan parent) {
        assertNotEquals(0, child.parentId());
        assertEquals(parent.context().spanId(), child.parentId());
    }

    public static void assertNotParent(MockSpan child) {
        assertEquals(0, child.parentId());
    }

    public static void assertServerSpanTag(MockSpan serverSpan) {
        assertEquals(TracingHandler.COMPONENT, serverSpan.tags().get(Tags.COMPONENT.getKey()));
        assertEquals(Tags.SPAN_KIND_SERVER, serverSpan.tags().get(Tags.SPAN_KIND.getKey()));
    }

    public static void assertClientSpanTag(MockSpan clientSpan) {
        assertEquals(TracingHandler.COMPONENT, clientSpan.tags().get(Tags.COMPONENT.getKey()));
        assertEquals(Tags.SPAN_KIND_CLIENT, clientSpan.tags().get(Tags.SPAN_KIND.getKey()));
    }

    public static void assertSpanError(MockSpan span) {
        assertEquals(true, span.tags().get(Tags.ERROR.getKey()));
    }

    public static void assertHasArguments(MockSpan span) {
        assertTrue(span.tags().size() > 1);
        assertNotNull(span.tags().get(TracingHandler.METHOD_ARGUMENTS));
    }
}
