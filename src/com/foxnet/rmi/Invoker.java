package com.foxnet.rmi;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import com.foxnet.rmi.binding.RemoteBinding;
import com.foxnet.rmi.binding.registry.DynamicRegistry;
import com.foxnet.rmi.binding.registry.StaticRegistry;

public abstract class Invoker implements InvokerFactory, InvocationHandler {

	public static Invoker getInvokerOf(Object proxy) {
		// Check to be a proxy class
		if (proxy == null || !Proxy.isProxyClass(proxy.getClass())) {
			return null;
		} else {
			// Try to get the handler
			InvocationHandler handler = Proxy.getInvocationHandler(proxy);

			// Conditional return...
			return handler instanceof Invoker ? (Invoker) handler : null;
		}
	}

	private final InvokerFactory invokerFactory;
	private final RemoteBinding remoteBinding;
	private volatile long proxyInvocationTimeout;
	private volatile Object lazyProxy;

	protected Invoker(InvokerFactory invokerFactory, RemoteBinding remoteBinding) {
		if (invokerFactory == null) {
			throw new NullPointerException("invokerFactory");
		} else if (remoteBinding == null) {
			throw new NullPointerException("remoteBinding");
		}
		this.invokerFactory = invokerFactory;
		this.remoteBinding = remoteBinding;
	}

	public long getProxyInvocationTimeout() {
		return proxyInvocationTimeout;
	}

	public void setProxyInvocationTimeout(long proxyInvocationTimeout) {
		this.proxyInvocationTimeout = proxyInvocationTimeout;
	}

	public InvokerFactory getInvokerFactory() {
		return invokerFactory;
	}

	public RemoteBinding getRemoteBinding() {
		return remoteBinding;
	}

	@Override
	public Invoker invoker(RemoteBinding remoteBinding) {
		return invokerFactory.invoker(remoteBinding);
	}

	@Override
	public StaticRegistry getStaticRegistry() {
		return invokerFactory.getStaticRegistry();
	}

	@Override
	public DynamicRegistry getDynamicRegistry() {
		return invokerFactory.getDynamicRegistry();
	}

	@Override
	public Object lookupProxy(String target) throws IOException {
		return invokerFactory.lookupProxy(target);
	}

	@Override
	public Invoker lookupInvoker(String target) throws IOException {
		return invokerFactory.lookupInvoker(target);
	}

	public abstract Invocation invoke(int methodId, Object... args);

	@Override
	public Object invoke(Object proxy, Method method, Object[] args)
			throws Throwable {

		// Invoke the method remotely
		Invocation invocation = invoke(method, args);

		// Simply synchronize and return or throw
		if (invocation.synchronize(proxyInvocationTimeout)) {
			return invocation.getAttachment();
		} else {
			throw invocation.getCause();
		}
	}

	public Invocation invoke(Method method, Object... args) {
		return invoke(remoteBinding.getMethodIds().get(method), args);
	}

	public Invocation invoke(String method, Object... args) {
		return invoke(remoteBinding.getNameIds().get(method), args);
	}

	public Object getProxy() {
		Object tmpProxy = lazyProxy;
		if (tmpProxy == null) {
			synchronized (this) {
				tmpProxy = lazyProxy;
				if (tmpProxy == null) {
					lazyProxy = tmpProxy = Proxy.newProxyInstance(Thread
							.currentThread().getContextClassLoader(),
							remoteBinding.getInterfaces(), this);
				}
			}
		}

		return tmpProxy;
	}
}
