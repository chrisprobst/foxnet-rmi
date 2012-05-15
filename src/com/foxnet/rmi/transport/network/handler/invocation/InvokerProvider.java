package com.foxnet.rmi.transport.network.handler.invocation;

import org.jboss.netty.channel.Channel;

import com.foxnet.rmi.InvokerFactory;
import com.foxnet.rmi.InvokerService;

public class InvokerProvider implements InvokerService {
	@Override
	public InvokerFactory getFactory(Object... params) {
		return InvokerHandler.of((Channel) params[0]);
	}
}