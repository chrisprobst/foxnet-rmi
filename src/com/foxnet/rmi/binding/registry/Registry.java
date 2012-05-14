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
package com.foxnet.rmi.binding.registry;

import java.io.Serializable;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import com.foxnet.rmi.binding.LocalBinding;
import com.foxnet.rmi.binding.LocalObject;
import com.foxnet.rmi.binding.Remote;
import com.foxnet.rmi.util.Future;

/**
 * This class represents a simple abstract registry of bindings.
 * 
 * @author Christopher Probst
 */
public abstract class Registry<B extends LocalBinding> implements Iterable<B>,
		Serializable {

	// Used to log infos, warnings or messages
	protected static final Logger STATIC_LOGGER = Logger
			.getLogger(Registry.class.getName());

	public static Object replaceLocalObject(StaticRegistry staticRegistry,
			DynamicRegistry dynamicRegistry, Object localObject) {

		// Not for us...
		if (!(localObject instanceof LocalObject)) {
			return localObject;
		}

		// Convert
		LocalObject tmp = (LocalObject) localObject;

		// Lookup the target
		LocalBinding target;
		if (tmp.isDynamic()) {
			target = dynamicRegistry.get(tmp.getId());
		} else {
			target = staticRegistry.get(tmp.getId());
		}

		// Check the target
		if (target == null) {
			throw new IllegalArgumentException("The local argument object "
					+ "is not part of the given registries");
		} else {
			// Otherwise replace with real target
			return target.getTarget();
		}
	}

	/**
	 * Tries to find the given binding and creates and invoke a runnable
	 * invocation directly. The arguments will be checked to be remote objects
	 * and converted to proxies using the given {@link ProxyManager} if
	 * necessary. The result will be stored in the given future. If you do not
	 * specify a future, this method will check the the method you want to
	 * invoke to be a void-method. If it is NOT a void-method an
	 * {@link IllegalStateException} will be thrown. If the method returns a
	 * {@link Remote} object the given dynamic registry will be used to generate
	 * a dynamic remote object.
	 * 
	 * @param dynamic
	 *            The dynamic flag (where to search the binding).
	 * @param bindingId
	 *            The binding id.
	 * @param proxyManager
	 *            The proxy manager.
	 * @param staticRegistry
	 *            The static registry which is used to lookup local objects
	 *            (arguments).
	 * @param dynamicRegistry
	 *            The dynamic registry which is used to find the binding and to
	 *            replace {@link Remote} return-values.
	 * @param future
	 *            The future object which will be notified asynchronously.
	 * @param methodId
	 *            The method id of the method you want to invoke.
	 * @param args
	 *            The arguments of the invocation.
	 * @return a runnable object.
	 * @throws IllegalStateException
	 *             If the future is null and the method returns a non-void
	 *             value.
	 */
	public static void invoke(boolean dynamic, long bindingId,
			ProxyManager proxyManager, StaticRegistry staticRegistry,
			DynamicRegistry dynamicRegistry, Future future, int methodId,
			Object... args) throws IllegalStateException {
		invoke(dynamic, bindingId, null, proxyManager, staticRegistry,
				dynamicRegistry, future, methodId, args);
	}

	/**
	 * Tries to find the given binding and creates and invoke a runnable
	 * invocation. The arguments will be checked to be remote objects and
	 * converted to proxies using the given {@link ProxyManager} if necessary.
	 * The result will be stored in the given future. If you do not specify a
	 * future, this method will check the the method you want to invoke to be a
	 * void-method. If it is NOT a void-method an {@link IllegalStateException}
	 * will be thrown. If the method returns a {@link Remote} object the given
	 * dynamic registry will be used to generate a dynamic remote object.
	 * 
	 * @param dynamic
	 *            The dynamic flag (where to search the binding).
	 * @param bindingId
	 *            The binding id.
	 * @param executor
	 *            The executor of the invocation.
	 * @param proxyManager
	 *            The proxy manager.
	 * @param staticRegistry
	 *            The static registry which is used to lookup local objects
	 *            (arguments).
	 * @param dynamicRegistry
	 *            The dynamic registry which is used to find the binding and to
	 *            replace {@link Remote} return-values.
	 * @param future
	 *            The future object which will be notified asynchronously.
	 * @param methodId
	 *            The method id of the method you want to invoke.
	 * @param args
	 *            The arguments of the invocation.
	 * @return a runnable object.
	 * @throws IllegalStateException
	 *             If the future is null and the method returns a non-void
	 *             value.
	 */
	public static void invoke(boolean dynamic, long bindingId,
			Executor executor, ProxyManager proxyManager,
			StaticRegistry staticRegistry, DynamicRegistry dynamicRegistry,
			Future future, int methodId, Object... args)
			throws IllegalStateException {

		if (staticRegistry == null) {
			throw new NullPointerException("staticRegistry");
		} else if (dynamicRegistry == null) {
			throw new NullPointerException("dynamicRegistry");
		}

		// Helper var
		LocalBinding binding;
		if (dynamic) {
			binding = dynamicRegistry.get(bindingId);
		} else {
			binding = staticRegistry.get(bindingId);
		}

		// Check the binding
		if (binding == null) {
			// Create message
			String msg = "Requested binding with id (" + bindingId
					+ ") does not exist";

			// Log
			STATIC_LOGGER.warning(msg);

			// Fail if future exists...
			if (future != null) {

				// Fail
				future.fail(new IllegalArgumentException(msg));
			}
		} else {
			// Transfer the request to the binding
			binding.invoke(executor, proxyManager, staticRegistry,
					dynamicRegistry, future, methodId, args);
		}
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	// Used to create indeces
	private long nextId = 0;

	// Used to log infos, warnings or messages
	protected final Logger logger = Logger.getLogger(getClass().getName());

	protected boolean notifyTargetBoundTo(Remote target) {
		if (target instanceof RegistryListener) {
			try {
				((RegistryListener) target).boundTo(this);
			} catch (Exception e) {
				logger.warning("Failed to notify the boundTo() "
						+ "method. Reason: " + e.getMessage());
			}
			return true;
		}
		return false;
	}

	protected boolean notifyTargetUnboundFrom(Remote target) {
		if (target instanceof RegistryListener) {
			try {
				((RegistryListener) target).unboundFrom(this);
			} catch (Exception e) {
				logger.warning("Failed to notify the unboundFrom() "
						+ "method. Reason: " + e.getMessage());
			}
			return true;
		}
		return false;
	}

	protected long getNextId() {
		return nextId++;
	}

	protected abstract Map<Long, B> getIndexMap();

	public abstract B unbind(long id);

	public abstract void unbindAll();

	public synchronized B get(long id) {
		return getIndexMap().get(id);
	}

	public synchronized Set<B> getBindings() {
		return new AbstractSet<B>() {

			// Copy to array list
			private final List<B> bindings = new ArrayList<B>(getIndexMap()
					.values());

			@Override
			public Iterator<B> iterator() {
				return bindings.iterator();
			}

			@Override
			public int size() {
				return bindings.size();
			}
		};
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<B> iterator() {
		return getBindings().iterator();
	}

	public synchronized int size() {
		return getIndexMap().size();
	}
}
