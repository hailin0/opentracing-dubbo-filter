package io.opentracing.contrib.dubbo.filter.registry;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.ConcurrentHashSet;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.common.utils.UrlUtils;
import com.alibaba.dubbo.registry.NotifyListener;
import com.alibaba.dubbo.registry.RegistryService;
import com.alibaba.dubbo.registry.support.AbstractRegistry;
import com.alibaba.dubbo.rpc.RpcContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Reference: dubbo-registry-default/test/java: org.apache.dubbo.registry.dubbo.SimpleRegistryService
 */
public class SimpleRegistryService extends AbstractRegistry {
    private static final Logger logger = LoggerFactory.getLogger(SimpleRegistryService.class);
    private final ConcurrentMap<String, Set<URL>> remoteRegistered = new ConcurrentHashMap();
    private final ConcurrentMap<String, ConcurrentMap<URL, Set<NotifyListener>>> remoteSubscribed = new ConcurrentHashMap();

    public SimpleRegistryService() {
        super(new URL("dubbo", NetUtils.getLocalHost(), 0, RegistryService.class.getName(), new String[]{"file", "N/A"}));
    }

    public boolean isAvailable() {
        return true;
    }

    public List<URL> lookup(URL url) {
        List<URL> urls = new ArrayList();
        Iterator var3 = this.getRegistered().iterator();

        while(var3.hasNext()) {
            URL u = (URL)var3.next();
            if (UrlUtils.isMatch(url, u)) {
                urls.add(u);
            }
        }

        return urls;
    }

    public void register(URL url) {
        String client = RpcContext.getContext().getRemoteAddressString();
        Set<URL> urls = (Set)this.remoteRegistered.get(client);
        if (urls == null) {
            this.remoteRegistered.putIfAbsent(client, new ConcurrentHashSet());
            urls = (Set)this.remoteRegistered.get(client);
        }

        urls.add(url);
        super.register(url);
        this.registered(url);
    }

    public void unregister(URL url) {
        String client = RpcContext.getContext().getRemoteAddressString();
        Set<URL> urls = (Set)this.remoteRegistered.get(client);
        if (urls != null && urls.size() > 0) {
            urls.remove(url);
        }

        super.unregister(url);
        this.unregistered(url);
    }

    public void subscribe(URL url, NotifyListener listener) {
        if (this.getUrl().getPort() == 0) {
            URL registryUrl = RpcContext.getContext().getUrl();
            if (registryUrl != null && registryUrl.getPort() > 0 && RegistryService.class.getName().equals(registryUrl.getPath())) {
                super.setUrl(registryUrl);
                super.register(registryUrl);
            }
        }

        String client = RpcContext.getContext().getRemoteAddressString();
        ConcurrentMap<URL, Set<NotifyListener>> clientListeners = (ConcurrentMap)this.remoteSubscribed.get(client);
        if (clientListeners == null) {
            this.remoteSubscribed.putIfAbsent(client, new ConcurrentHashMap());
            clientListeners = (ConcurrentMap)this.remoteSubscribed.get(client);
        }

        Set<NotifyListener> listeners = (Set)clientListeners.get(url);
        if (listeners == null) {
            clientListeners.putIfAbsent(url, new ConcurrentHashSet());
            listeners = (Set)clientListeners.get(url);
        }

        listeners.add(listener);
        super.subscribe(url, listener);
        this.subscribed(url, listener);
    }

    public void unsubscribe(URL url, NotifyListener listener) {
        if (!"*".equals(url.getServiceInterface()) && url.getParameter("register", true)) {
            this.unregister(url);
        }

        String client = RpcContext.getContext().getRemoteAddressString();
        Map<URL, Set<NotifyListener>> clientListeners = (Map)this.remoteSubscribed.get(client);
        if (clientListeners != null && clientListeners.size() > 0) {
            Set<NotifyListener> listeners = (Set)clientListeners.get(url);
            if (listeners != null && listeners.size() > 0) {
                listeners.remove(listener);
            }
        }

    }

    protected void registered(URL url) {
        Iterator var2 = this.getSubscribed().entrySet().iterator();

        while(true) {
            Entry entry;
            URL key;
            do {
                if (!var2.hasNext()) {
                    return;
                }

                entry = (Entry)var2.next();
                key = (URL)entry.getKey();
            } while(!UrlUtils.isMatch(key, url));

            List<URL> list = this.lookup(key);
            Iterator var6 = ((Set)entry.getValue()).iterator();

            while(var6.hasNext()) {
                NotifyListener listener = (NotifyListener)var6.next();
                listener.notify(list);
            }
        }
    }

    protected void unregistered(URL url) {
        Iterator var2 = this.getSubscribed().entrySet().iterator();

        while(true) {
            Entry entry;
            URL key;
            do {
                if (!var2.hasNext()) {
                    return;
                }

                entry = (Entry)var2.next();
                key = (URL)entry.getKey();
            } while(!UrlUtils.isMatch(key, url));

            List<URL> list = this.lookup(key);
            Iterator var6 = ((Set)entry.getValue()).iterator();

            while(var6.hasNext()) {
                NotifyListener listener = (NotifyListener)var6.next();
                listener.notify(list);
            }
        }
    }

    protected void subscribed(final URL url, final NotifyListener listener) {
        if ("*".equals(url.getServiceInterface())) {
            (new Thread(new Runnable() {
                public void run() {
                    Map<String, List<URL>> map = new HashMap();
                    Iterator var2 = SimpleRegistryService.this.getRegistered().iterator();

                    while(var2.hasNext()) {
                        URL u = (URL)var2.next();
                        if (UrlUtils.isMatch(url, u)) {
                            String service = u.getServiceInterface();
                            List<URL> listx = (List)map.get(service);
                            if (listx == null) {
                                listx = new ArrayList();
                                map.put(service, listx);
                            }

                            ((List)listx).add(u);
                        }
                    }

                    var2 = map.values().iterator();

                    while(var2.hasNext()) {
                        List list = (List)var2.next();

                        try {
                            listener.notify(list);
                        } catch (Throwable var6) {
                            SimpleRegistryService.logger.warn("Discard to notify " + url.getServiceKey() + " to listener " + listener);
                        }
                    }

                }
            }, "DubboMonitorNotifier")).start();
        } else {
            List list = this.lookup(url);

            try {
                listener.notify(list);
            } catch (Throwable var5) {
                logger.warn("Discard to notify " + url.getServiceKey() + " to listener " + listener);
            }
        }

    }

    public void disconnect() {
        String client = RpcContext.getContext().getRemoteAddressString();
        if (logger.isInfoEnabled()) {
            logger.info("Disconnected " + client);
        }

        Set<URL> urls = (Set)this.remoteRegistered.get(client);
        if (urls != null && urls.size() > 0) {
            Iterator var3 = urls.iterator();

            while(var3.hasNext()) {
                URL url = (URL)var3.next();
                this.unregister(url);
            }
        }

        Map<URL, Set<NotifyListener>> listeners = (Map)this.remoteSubscribed.get(client);
        if (listeners != null && listeners.size() > 0) {
            Iterator var10 = listeners.entrySet().iterator();

            while(var10.hasNext()) {
                Entry<URL, Set<NotifyListener>> entry = (Entry)var10.next();
                URL url = (URL)entry.getKey();
                Iterator var7 = ((Set)entry.getValue()).iterator();

                while(var7.hasNext()) {
                    NotifyListener listener = (NotifyListener)var7.next();
                    this.unsubscribe(url, listener);
                }
            }
        }

    }
}
