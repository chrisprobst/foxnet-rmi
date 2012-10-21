package com.foxnet.rmi.transport.network.handler.invocation;

import java.io.Serializable;

/**
 * 
 * @author Christopher Probst
 * 
 */
public class InvocationMessage implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final boolean dynamic;
	private final long bindingId;
	private final int methodId;
	private final Object[] arguments;

	public InvocationMessage(boolean dynamic, long bindingId, int methodId,
			Object... arguments) {
		this.dynamic = dynamic;
		this.bindingId = bindingId;
		this.methodId = methodId;
		this.arguments = arguments;
	}

	public boolean isDynamic() {
		return dynamic;
	}

	public long bindingId() {
		return bindingId;
	}

	public int methodId() {
		return methodId;
	}

	public Object[] arguments() {
		return arguments;
	}
}
