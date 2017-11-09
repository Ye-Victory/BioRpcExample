package bio.rpc.netcom;

import bio.rpc.netcom.Rules.RpcRequest;
import bio.rpc.netcom.Rules.RpcResponse;
import bio.rpc.netcom.bio.client.BioClient;
import bio.rpc.netcom.client.IClient;
import bio.rpc.serialize.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

/**
 * Created by luyu on 2017/11/9.
 */
public class NetComClientProxy implements FactoryBean<Object>, InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(NetComClientProxy.class);
    // [tips01: save 30ms/100invoke. why why why??? with this logger, it can save lots of time.]

    // ---------------------- config ----------------------
    private String serverAddress;
    private Serializer serializer = Serializer.SerializeEnum.HESSIAN.serializer;
    private Class<?> iface;
    private long timeoutMillis = 5000;

    public NetComClientProxy(){	}
    public NetComClientProxy(String serverAddress, Serializer serializer, Class<?> iface, long timeoutMillis) {
        this.setServerAddress(serverAddress);
        this.serializer = serializer;
        this.setIface(iface);
        this.setTimeoutMillis(timeoutMillis);
        try {
            this.afterPropertiesSet();
        } catch (Exception e) {
            logger.error("", e);
        }
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }
    public void setSerializer(String serializer) {
        this.serializer = Serializer.SerializeEnum.match(serializer, Serializer.SerializeEnum.HESSIAN).serializer;
    }
    public void setIface(Class<?> iface) {
        this.iface = iface;
    }
    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    // ---------------------- init client, operate ----------------------
    IClient client = null;

    @Override
    public void afterPropertiesSet() throws Exception {
        client = new BioClient();
        client.init(serverAddress, serializer, timeoutMillis);
    }

    @Override
    public Object getObject() throws Exception {
        return Proxy.newProxyInstance(Thread.currentThread()
                        .getContextClassLoader(), new Class[] { iface },
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

                        // request
                        RpcRequest request = new RpcRequest();
                        request.setRequestId(UUID.randomUUID().toString());
                        request.setCreateMillisTime(System.currentTimeMillis());
                        request.setClassName(method.getDeclaringClass().getName());
                        request.setMethodName(method.getName());
                        request.setParameterTypes(method.getParameterTypes());
                        request.setParameters(args);

                        // send
                        RpcResponse response = client.send(request);

                        // valid response
                        if (response == null) {
                            logger.error(">>>>>>>>>>> xxl-rpc netty response not found.");
                            throw new Exception(">>>>>>>>>>> xxl-rpc netty response not found.");
                        }
                        if (response.isError()) {
                            throw response.getError();
                        } else {
                            return response.getResult();
                        }

                    }
                });
    }
    @Override
    public Class<?> getObjectType() {
        return iface;
    }
    @Override
    public boolean isSingleton() {
        return false;
    }

}
