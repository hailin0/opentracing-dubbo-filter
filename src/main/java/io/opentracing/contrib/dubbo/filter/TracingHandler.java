package io.opentracing.contrib.dubbo.filter;

import com.alibaba.dubbo.common.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.opentracing.ActiveSpan;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class TracingHandler {
    private static final Logger log = LoggerFactory.getLogger(TracingHandler.class);

    static final String COMPONENT = "dubbo";
    static final String METHOD_ARGUMENTS = "arguments";
    static final String DUBBO_URL = "url";
    static final String PEER_ADDRESS = "peer.address";

    private final Tracer tracer;

    TracingHandler() {
        this(resolveTracer());
    }

    TracingHandler(Tracer tracer) {
        this.tracer = tracer;
    }

    Span newSpan(boolean isConsumer,
                 Map<String, String> contextCarrier,
                 String remoteAddress,
                 URL requestURL,
                 String method,
                 Class<?>[] parameterTypes) {
        return new Span(isConsumer, contextCarrier, remoteAddress, requestURL, method, parameterTypes);
    }

    class Span {

        private ActiveSpan activeSpan;

        Span(boolean isConsumer,
             Map<String, String> contextCarrier,
             String remoteAddress,
             URL requestURL,
             String method,
             Class<?>[] parameterTypes) {
            String operationName = generateOperationName(requestURL, method, parameterTypes);
            if (log.isDebugEnabled()) {
                log.debug("Tracing {} {}", isConsumer ? "Consumer" : "Provider", operationName);
            }

            if (isConsumer) {
                activeSpan = tracer.buildSpan(operationName)
                        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                        .startActive();
                tracer.inject(activeSpan.context(), Format.Builtin.TEXT_MAP,
                        new TextMapInjectAdapter(contextCarrier));
            } else {
                Tracer.SpanBuilder spanBuilder = tracer.buildSpan(operationName)
                        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
                SpanContext parent = tracer.extract(Format.Builtin.TEXT_MAP, new TextMapExtractAdapter(contextCarrier));
                if (parent != null) {
                    spanBuilder.asChildOf(parent);
                }
                activeSpan = spanBuilder.startActive();
            }

            Tags.COMPONENT.set(activeSpan, COMPONENT);
            activeSpan.setTag(PEER_ADDRESS, remoteAddress);
            activeSpan.setTag(DUBBO_URL, generateRequestURL(requestURL, method, parameterTypes));
        }

        void error(Throwable e, Object[] arguments) {
            Tags.ERROR.set(activeSpan, Boolean.TRUE);
            activeSpan.log(logsForException(e));

            String argumentsStr = argumentsToString(arguments);
            if (argumentsStr != null) {
                activeSpan.setTag(METHOD_ARGUMENTS, argumentsToString(arguments));
            }
        }

        void close() {
            activeSpan.close();
        }
    }

    private static Tracer resolveTracer() {
        Tracer tracer = TracerResolver.resolveTracer();
        if (tracer == null) {
            tracer = GlobalTracer.get();
        }
        return tracer;
    }

    /**
     * Format operation name. e.g. org.xxx.Test.test(String)
     *
     * @return operation name.
     */
    private static String generateOperationName(URL requestURL, String method, Class<?>[] parameterTypes) {
        StringBuilder operationName = new StringBuilder();
        operationName.append(requestURL.getPath());
        operationName.append("." + method + "(");
        if (parameterTypes != null) {
            for (Class<?> classes : parameterTypes) {
                operationName.append(classes.getSimpleName() + ",");
            }

            if (parameterTypes.length > 0) {
                operationName.delete(operationName.length() - 1, operationName.length());
            }
        }

        operationName.append(")");

        return operationName.toString();
    }

    /**
     * Format request url. e.g. dubbo://127.0.0.1:20880/com.xxx.Test.test(String).
     *
     * @return request url.
     */
    private static String generateRequestURL(URL url, String method, Class<?>[] parameterTypes) {
        StringBuilder requestURL = new StringBuilder();
        requestURL.append(url.getProtocol() + "://");
        requestURL.append(url.getHost());
        requestURL.append(":" + url.getPort() + "/");
        requestURL.append(generateOperationName(url, method, parameterTypes));
        return requestURL.toString();
    }

    private Map<String, String> logsForException(Throwable throwable) {
        Map<String, String> errorLog = new HashMap<>(3);
        errorLog.put("event", Tags.ERROR.getKey());

        String message = throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage();
        if (message != null) {
            errorLog.put("message", message);
        }
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        errorLog.put("stack", sw.toString());

        return errorLog;
    }

    /**
     * Format arguments. e.g. [arg1,arg2,...]
     */
    private String argumentsToString(Object[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return null;
        }

        ArrayList<Object> arr = new ArrayList<>();
        for (Object argument : arguments) {
            if (argument == null) {
                arr.add("null");
            }
            if (argument instanceof String) {
                String text = (String) argument;
                if (text.length() > 100) {
                    arr.add(text.substring(0, 97) + "...");
                } else {
                    arr.add(text);
                }
            } else if (argument instanceof Number) {
                arr.add(argument);
            } else if (argument instanceof java.util.Date) {
                arr.add(argument);
            } else if (argument instanceof Boolean) {
                arr.add(argument);
            } else {
                arr.add('<' + argument.getClass().getName() + '>');
            }
        }
        return arr.toString();
    }
}
