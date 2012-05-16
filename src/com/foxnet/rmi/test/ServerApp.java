package com.foxnet.rmi.test;

import java.io.IOException;

import org.jboss.netty.channel.Channel;

import com.foxnet.rmi.Invoker;
import com.foxnet.rmi.InvokerManager;
import com.foxnet.rmi.Remote;
import com.foxnet.rmi.transport.network.ConnectionManager;
import com.foxnet.rmi.util.Future;
import com.foxnet.rmi.util.InvokerServices;

public class ServerApp implements ServerInterface, Remote {

	@Override
	public void dieLiefertNichtsZurueck() {
		System.out.println("void  methode");
	}

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {

		
		// SERVER
		ConnectionManager cm = new ConnectionManager(true);

		cm.openServer(1337);
		cm.getStaticRegistry().bind("void-stuff", new ServerApp());

		
		
		
		// CLIENT
		ConnectionManager ccm = new ConnectionManager(false);

		Channel channel = ccm.openClient("localhost", 1337);

		
		// Lade the remote factory für diesen Kanel
		InvokerManager fac = InvokerServices.load().getFactory(channel);

		
		Invoker invoker = fac.lookupInvoker("void-stuff");
		
		
		Future future = invoker.invoke("dieLiefertNichtsZurueck");
	}
}
