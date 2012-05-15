package com.foxnet.rmi.util;

import com.foxnet.rmi.InvokerService;
import com.foxnet.rmi.transport.network.handler.invocation.InvokerProvider;

public class InvokerServices {

	private static final InvokerService is = new InvokerProvider();

	public static InvokerService load() {
		return is;
	}
}
