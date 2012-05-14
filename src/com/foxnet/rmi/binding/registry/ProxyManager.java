package com.foxnet.rmi.binding.registry;


public interface ProxyManager {

	Object replaceRemoteObject(Object remoteObject);

	Object replaceProxy(Object proxy);
}
