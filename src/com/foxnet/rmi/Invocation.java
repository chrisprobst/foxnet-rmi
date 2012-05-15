package com.foxnet.rmi;

import java.lang.reflect.Method;

import com.foxnet.rmi.util.Future;

public final class Invocation extends Future {

	private final Invoker invoker;
	private final int methodId;
	private final Object[] arguments;

	@Override
	protected Object modifyAttachment(Object attachment) {
		return RmiRoutines.remoteToLocal(invoker.getInvokerFactory(),
				attachment);
	}

	public Invocation(Invoker invoker, int methodId, Object... arguments) {
		if (invoker == null) {
			throw new NullPointerException("invoker");
		}
		this.methodId = methodId;
		this.arguments = arguments;
		this.invoker = invoker;
	}

	public Invoker getInvoker() {
		return invoker;
	}

	public Method getMethod() {
		return invoker.getRemoteBinding().getMethods().get(methodId);
	}

	public String getMethodName() {
		return getMethod().getName();
	}

	public int getMethodId() {
		return methodId;
	}

	public Object[] getArguments() {
		return arguments;
	}
}
