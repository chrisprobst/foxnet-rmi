/*
 * Copyright (C) 2011 Christopher Probst
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of the 'FoxNet RMI' nor the names of its 
 *   contributors may be used to endorse or promote products derived
 *   from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR 
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.foxnet.rmi.test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.jboss.netty.channel.Channel;

import com.foxnet.rmi.binding.LocalInterface;
import com.foxnet.rmi.binding.OrderedExecution;
import com.foxnet.rmi.binding.Remote;
import com.foxnet.rmi.binding.StaticBinding;
import com.foxnet.rmi.binding.registry.Registry;
import com.foxnet.rmi.binding.registry.RegistryListener;
import com.foxnet.rmi.transport.network.ConnectionManager;
import com.foxnet.rmi.transport.network.handler.invocation.InvocationMessage;
import com.foxnet.rmi.util.Future;
import com.foxnet.rmi.util.FutureCallback;
import com.foxnet.rmi.util.Request;

/**
 * @author Christopher Probst
 */
public class TestApp {

	public static interface Service {

		@OrderedExecution
		String get(int index);
	}

	public static class Test implements Service, Remote, RegistryListener {
		@Override
		public void boundTo(Registry<?> registry) throws Exception {
			System.out.println("bound to: " + registry);
		}

		@Override
		public void unboundFrom(Registry<?> registry) throws Exception {
			System.out.println("unbound from: " + registry);
		}

		@Override
		public String get(int index) {

			try {
				Thread.sleep((int) (Math.random() * 200));
			} catch (Exception e) {
				// TODO: handle exception
			}

			return "first message";
		}

	}

	public static void main(String[] args) throws Exception {

		ConnectionManager servers = new ConnectionManager(true);
		servers.openServer(1337);

		servers.getStaticRegistry().bind("test", new Test());

		// Open connection
		ConnectionManager clients = new ConnectionManager(false);
		Channel connection = clients.openClient("kr0e-pc", 1337)
				.awaitUninterruptibly().getChannel();

		for (Class<?> c : servers.getStaticRegistry().get("test")
				.getInterfaces()) {
			System.out.println(c);
		}

		for (int i = 0; i < 100; i++) {
			final int j = i;
			Request req = new Request(new InvocationMessage(false, 0, 0, j));
			req.add(new FutureCallback() {

				@Override
				public void completed(Future future) throws Exception {

					System.out.println(j + ". " + future.getAttachment());
				}
			});

			connection.write(req);
		}

	}
};
