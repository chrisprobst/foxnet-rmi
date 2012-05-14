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
package com.foxnet.rmi.kernel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.SocketChannelConfig;

import com.foxnet.rmi.Connection;
import com.foxnet.rmi.Constants;
import com.foxnet.rmi.Invocation;
import com.foxnet.rmi.Invoker;
import com.foxnet.rmi.binding.LocalBinding;
import com.foxnet.rmi.binding.LocalBinding.OrderedExecutionQueue;
import com.foxnet.rmi.binding.registry.DynamicRegistry;
import com.foxnet.rmi.binding.registry.StaticRegistry;
import com.foxnet.rmi.binding.AsyncVoid;
import com.foxnet.rmi.binding.RemoteBinding;
import com.foxnet.rmi.binding.RemoteObject;
import com.foxnet.rmi.binding.StaticBinding;
import com.foxnet.rmi.transport.network.ConnectionManager;
import com.foxnet.rmi.util.ChannelMemoryLimiter;
import com.foxnet.rmi.util.ChannelMemoryLimiter.ChannelMemory;
import com.foxnet.rmi.util.Future;
import com.foxnet.rmi.util.FutureCallback;
import com.foxnet.rmi.util.Request;
import com.foxnet.rmi.util.RequestManager;
import com.foxnet.rmi.util.WrapperInputStream;
import com.foxnet.rmi.util.WrapperOutputStream;

/**
 * @author Christopher Probst
 */
public final class Dispatcher extends SimpleChannelHandler  {


	private Invocation invokeAsynchronously(Invoker invoker, String methodName,
			Object... args) {
		// Get method
		Method method = invoker.getMethods().get(methodName);

		// If method name is wrong...
		if (method == null) {
			// Create and return failed invocation
			Invocation invocation = new Invocation(invoker, methodName, args);
			invocation.failed(new IllegalArgumentException("Method with "
					+ "name \"" + methodName + "\" does " + "not exist."));
			return invocation;
		}

		/*
		 * At first replace all objects which implement the Remote interface
		 * with instances of RemoteObject so the receiver can easily create
		 * appropriate proxy classes.
		 */
		dynamicRegistry.replaceRemoteObjects(args);

		// Annotation
		AsyncVoid av;

		// Get the remote binding
		RemoteBinding remoteBinding = invoker.getRemoteBinding();

		// Check for async void method
		if (method.getReturnType() == void.class
				&& method.getExceptionTypes().length == 0
				&& ((av = method.getAnnotation(AsyncVoid.class)) != null && av
						.value())) {

			/*
			 * Now send the request asynchronously to the remote side.
			 */
			try {
				invokeVoidMethod(remoteBinding.isDynamic(),
						remoteBinding.getId(),
						remoteBinding.getMethodId(method), args);
			} catch (Exception e) {
				// Create and return failed invocation
				Invocation invocation = new Invocation(invoker, methodName,
						args);
				invocation.failed(e);
				return invocation;
			}

			// Return a new completed invocation
			Invocation invocation = new Invocation(invoker, methodName, args);
			invocation.completed(null);
			return invocation;
		} else {
			// Create a default invocation
			final Invocation invocation = new Invocation(invoker, methodName,
					args);

			/*
			 * Now send the request synchronously to the remote side.
			 */
			Request request;
			try {
				request = invokeMethod(remoteBinding.isDynamic(),
						remoteBinding.getId(),
						remoteBinding.getMethodId(method), args);
			} catch (Exception e) {
				invocation.failed(e);
				return invocation;
			}

			// Wait for response asynchronously
			request.add(new FutureCallback() {

				@Override
				public void completed(final Future future) throws Exception {
					// Execute the notification in an executor
					getMethodInvocator().execute(new Runnable() {

						@Override
						public void run() {
							if (future.isSuccessful()) {
								invocation.completed(future.getAttachment());
							} else {
								invocation.failed(future.getCause());
							}
						}
					});
				}
			});

			return invocation;
		}
	}

	private Object invokeSynchronously(RemoteBinding remoteBinding,
			Method method, Object[] args) throws Throwable {

		/*
		 * At first replace all objects which implement the Remote interface
		 * with instances of RemoteObject so the receiver can easily create
		 * appropriate proxy classes.
		 */
		dynamicRegistry.replaceRemoteObjects(args);

		// Annotation
		AsyncVoid av;

		// Check for async void method
		if (method.getReturnType() == void.class
				&& method.getExceptionTypes().length == 0
				&& ((av = method.getAnnotation(AsyncVoid.class)) != null && av
						.value())) {

			/*
			 * Now send the request asynchronously to the remote side.
			 */
			invokeVoidMethod(remoteBinding.isDynamic(), remoteBinding.getId(),
					remoteBinding.getMethodId(method), args);

			return null;
		} else {
			/*
			 * Now send the request synchronously to the remote side.
			 */
			Request request = invokeMethod(remoteBinding.isDynamic(),
					remoteBinding.getId(), remoteBinding.getMethodId(method),
					args);

			/*
			 * Now await that the request completes.
			 */
			if (request.await(INVOCATION_TIMEOUT, INVOCATION_TIME_UNIT)) {

				/*
				 * Replace reference and/or return response
				 */
				return replaceReference(request.getAttachment());
			} else {

				/*
				 * Throw the exception which was caught on the remote side.
				 */
				throw request.getCause();
			}
		}
	}


	private Object createProxy(RemoteObject remoteObject, boolean dynamic) {
		return Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(),
				remoteObject.getInterfaces(), new ProxyHandler(remoteObject,
						dynamic));
	}


}
