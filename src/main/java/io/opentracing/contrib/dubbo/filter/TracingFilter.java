package io.opentracing.contrib.dubbo.filter;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;

import java.util.Map;

@Activate(group = {Constants.PROVIDER, Constants.CONSUMER})
public class TracingFilter implements Filter {

    protected TracingHandler tracingHandler = new TracingHandler();

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        TracingHandler.Span span = tracingHandler.newSpan(isConsumer(),
                getContextCarrier(),
                getRemoteAddress(),
                invoker.getUrl(),
                invocation.getMethodName(),
                invocation.getParameterTypes());
        try {
            Result result = invoker.invoke(invocation);
            if (result.hasException()) {
                span.error(result.getException(), invocation.getArguments());
            }
            return result;
        } catch (Throwable e) {
            span.error(e, invocation.getArguments());
            throw e;
        } finally {
            span.close();
        }
    }

    private boolean isConsumer() {
        return RpcContext.getContext().isConsumerSide();
    }

    private Map<String, String> getContextCarrier() {
        return RpcContext.getContext().getAttachments();
    }

    private String getRemoteAddress() {
        return RpcContext.getContext().getRemoteAddressString();
    }

}
