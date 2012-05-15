package com.foxnet.rmi;

import java.io.IOException;

import com.foxnet.rmi.binding.RemoteBinding;
import com.foxnet.rmi.binding.registry.DynamicRegistry;
import com.foxnet.rmi.binding.registry.StaticRegistry;

public interface InvokerFactory {

	StaticRegistry getStaticRegistry();

	DynamicRegistry getDynamicRegistry();

	Invoker invoker(RemoteBinding remoteBinding);

	Invoker lookupInvoker(String target) throws IOException;

	Object lookupProxy(String target) throws IOException;
}
