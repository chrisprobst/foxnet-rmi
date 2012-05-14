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
package com.foxnet.rmi.binding;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import com.foxnet.rmi.binding.registry.DynamicRegistry;
import com.foxnet.rmi.binding.registry.ProxyManager;
import com.foxnet.rmi.binding.registry.Registry;
import com.foxnet.rmi.binding.registry.StaticRegistry;
import com.foxnet.rmi.util.Future;

/**
 * This represents an abstract local binding.
 * 
 * @author Christopher Probst
 */
public abstract class LocalBinding extends Binding {

	// The logger
	protected final Logger logger = Logger.getLogger(getClass().getName());

	/**
	 * 
	 */

	private static final long serialVersionUID = 1L;

	/**
	 * Simply checks whether or not the given interface class is valid.
	 * 
	 * @param interfaceClass
	 *            The interface class you want to check.
	 * @return true if the interface class is valid, otherwise false.
	 */
	private static boolean isValidRemoteInterface(Class<?> interfaceClass) {
		return !LocalInterface.class.isAssignableFrom(interfaceClass)
				&& !Remote.class.equals(interfaceClass);
	}

	/**
	 * This method collects all valid remote interfaces.
	 * 
	 * @param startClass
	 *            The start class.
	 * @param endClass
	 *            The end class.
	 * @param interfaces
	 *            The set with all remote interface classes.
	 */
	private static void collectAllRemoteInterfaces(Class<?> startClass,
			Class<?> endClass, Set<Class<?>> interfaces) {

		// Start with the end class
		Class<?> pointer = endClass;

		// Used to return when finished
		boolean finished = false;

		do {

			// The finished parameter
			finished = pointer == startClass;

			// For all interfaces
			for (Class<?> interfaceClass : pointer.getInterfaces()) {
				// Check
				if (isValidRemoteInterface(interfaceClass)) {
					// Add to set
					interfaces.add(interfaceClass);
				}
			}

			// Save
			pointer = pointer.getSuperclass();

		} while (!finished);
	}

	/**
	 * This method collects all remote interfaces or uses the
	 * {@link RemoteInterfaces} annotation if present to use only the specified
	 * intefaces.
	 * 
	 * @param target
	 *            The remote target.
	 * @return an array with all valid remote interfaces.
	 */
	private static Class<?>[] getRemoteInterfaces(Remote target) {
		if (target == null) {
			throw new NullPointerException("target");
		}

		// Get annotation if present
		RemoteInterfaces remoteInterfaces = target.getClass().getAnnotation(
				RemoteInterfaces.class);

		// Used to collect the interfaces
		Set<Class<?>> uniqueInterfaces = new HashSet<Class<?>>();

		/*
		 * Use the classes from the annotation.
		 */
		if (remoteInterfaces != null && remoteInterfaces.value().length > 0) {
			// Check if the interfaces are valid
			for (Class<?> interfaceClass : remoteInterfaces.value()) {
				// Check
				if (interfaceClass.isInterface()
						&& isValidRemoteInterface(interfaceClass)
						&& interfaceClass.isAssignableFrom(target.getClass())) {
					// Add to interfaces
					uniqueInterfaces.add(interfaceClass);
				}
			}
		} else {
			// Collect all interfaces
			collectAllRemoteInterfaces(Object.class, target.getClass(),
					uniqueInterfaces);
		}

		// Copy to array
		return uniqueInterfaces.toArray(new Class<?>[uniqueInterfaces.size()]);
	}

	/**
	 * This class implements the concept of an ordered execution queue.
	 * 
	 * @author Christopher Probst
	 */
	public static final class OrderedExecutionQueue implements Runnable,
			Serializable {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		// Lock the queue
		private final Object queueLock = new Object();

		/*
		 * Used to queue the runnables.
		 */
		private final Queue<Runnable> queue = new LinkedList<Runnable>();

		/**
		 * This method offers a runnable object.
		 * 
		 * @param runnable
		 *            The runnable you want to add.
		 * @return true if this queue needs to be executed.
		 */
		public boolean addOrderedExecution(Runnable runnable) {

			// Init here
			boolean needsExecution;

			synchronized (queueLock) {
				// Does this queue need to be executed ?
				needsExecution = queue.isEmpty();

				// Offer command!
				queue.offer(runnable);
			}

			// Return the state
			return needsExecution;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Runnable#run()
		 */
		@Override
		public void run() {
			Runnable runnable = null;

			for (;;) {
				synchronized (queueLock) {
					// Get command
					runnable = queue.element();
				}

				// Run!
				runnable.run();

				synchronized (queueLock) {

					// Remove command
					queue.remove();

					// Leave the loop if empty!
					if (queue.isEmpty()) {
						return;
					}
				}
			}
		}
	}

	// Here we store all ordered execution queues mapped to their method ids
	private final Map<Integer, OrderedExecutionQueue> orderedExecutionQueues;

	// Here we store the remote target
	private final Remote target;

	/**
	 * Creates a new local binding using the default
	 * interface-collect-algorithm.
	 * 
	 * @param id
	 *            The id of this binding.
	 * @param target
	 *            The target of this binding.
	 */
	public LocalBinding(long id, Remote target) {
		this(id, target, getRemoteInterfaces(target));
	}

	/**
	 * Creates a new local binding.
	 * 
	 * @param id
	 *            The id of this binding.
	 * @param target
	 *            The target of this binding.
	 * @param interfaces
	 *            The interface classes of the target.
	 */
	public LocalBinding(long id, Remote target, Class<?>[] interfaces) {
		super(id, interfaces);

		// Check all given interface classes
		for (Class<?> interfaceClass : interfaces) {
			if (!interfaceClass.isAssignableFrom(target.getClass())) {
				throw new IllegalArgumentException(interfaceClass
						+ " is not part of the hierarchy of "
						+ target.getClass());
			}
		}

		// Save target
		this.target = target;

		// Annotation
		OrderedExecution oe;

		// Create tmp
		Map<Integer, OrderedExecutionQueue> tmpOrderedExecutionQueues = null;

		// Add all methods which
		for (int i = 0, l = getMethods().size(); i < l; i++) {
			// Check if OrderedExecution is present...
			if ((oe = getMethods().get(i).getAnnotation(OrderedExecution.class)) != null
					&& oe.value()) {

				// Lazy setup
				if (tmpOrderedExecutionQueues == null) {
					tmpOrderedExecutionQueues = new HashMap<Integer, OrderedExecutionQueue>();
				}

				// Create new queue
				tmpOrderedExecutionQueues.put(i, new OrderedExecutionQueue());
			}
		}

		// Set the map
		orderedExecutionQueues = tmpOrderedExecutionQueues != null ? Collections
				.unmodifiableMap(tmpOrderedExecutionQueues) : null;
	}

	/**
	 * @return the ordered execution queues linked to their method ids or null
	 *         if this binding does not have any ordered executions.
	 */
	public Map<Integer, OrderedExecutionQueue> getOrderedExecutionQueues() {
		return orderedExecutionQueues;
	}

	/**
	 * Creates a runnable invocation. The arguments will be checked to be remote
	 * objects and converted to proxies using the given {@link ProxyManager} if
	 * necessary. The result will be stored in the given future. If you do not
	 * specify a future, this method will check the the method you want to
	 * invoke to be a void-method. If it is NOT a void-method an
	 * {@link IllegalStateException} will be thrown. If the method returns a
	 * {@link Remote} object the given dynamic registry will be used to generate
	 * a dynamic remote object.
	 * 
	 * @param proxyManager
	 *            The proxy manager.
	 * @param staticRegistry
	 *            The static registry which is used to lookup local objects
	 *            (arguments).
	 * @param dynamicRegistry
	 *            The dynamic registry which is used to replace {@link Remote}
	 *            return-values.
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
	public Runnable createInvocation(final ProxyManager proxyManager,
			final StaticRegistry staticRegistry,
			final DynamicRegistry dynamicRegistry, final Future future,
			int methodId, final Object... args) throws IllegalStateException {
		if (proxyManager == null) {
			throw new NullPointerException("proxyManager");
		} else if (dynamicRegistry == null) {
			throw new NullPointerException("dynamicRegistry");
		}

		// Check if method id does not exist...
		if (!containsMethodId(methodId)) {

			// Create message
			String msg = "Method id (" + methodId + ") does not exist";

			// Log
			logger.warning(msg);

			// Fail the future...
			if (future != null) {

				// Fail
				future.fail(new IllegalArgumentException(msg));
			}

			return null;
		} else {

			// Get final method from binding!
			final Method method = getMethods().get(methodId);

			/*
			 * Check the return value and the future.
			 */
			if (method.getReturnType() != void.class && future == null) {
				throw new IllegalStateException("The method does return a "
						+ "non-void value but you have non specified a future");
			}

			// Create a new invocation runnable
			return new Runnable() {

				@Override
				public void run() {
					try {
						/*
						 * Resolve all remote objects.
						 */
						for (int i = 0; i < args.length; i++) {
							// Replace remote object
							args[i] = proxyManager.replaceRemoteObject(args[i]);

							// Replace local object
							args[i] = Registry.replaceLocalObject(
									staticRegistry, dynamicRegistry, args[i]);
						}

						// Invoke the method
						Object result = method.invoke(target, args);

						// Do we send back a known proxy ?
						result = proxyManager.replaceProxy(result);

						// Do we send back a remote ?
						result = dynamicRegistry.replaceRemote(result);

						// Succeed the future with the filtered result
						future.succeed(result);
					} catch (InvocationTargetException e) {
						// Short log...
						logger.warning("Declared throwable catched: "
								+ e.getCause());

						if (future != null) {
							// Fail
							future.fail(e);
						}
					} catch (Throwable e) {
						// Short log...
						logger.warning("Failed to invoke method. Reason: "
								+ e.getMessage());

						if (future != null) {
							// Fail
							future.fail(e);
						}
					}
				}
			};
		}
	}

	/**
	 * Creates and invoke a runnable invocation. The arguments will be checked
	 * to be remote objects and converted to proxies using the given
	 * {@link ProxyManager} if necessary. The result will be stored in the given
	 * future. If you do not specify a future, this method will check the the
	 * method you want to invoke to be a void-method. If it is NOT a void-method
	 * an {@link IllegalStateException} will be thrown. If the method returns a
	 * {@link Remote} object the given dynamic registry will be used to generate
	 * a dynamic remote object.
	 * 
	 * @param executor
	 *            The executor of the invocation.
	 * @param proxyManager
	 *            The proxy manager.
	 * @param staticRegistry
	 *            The static registry which is used to lookup local objects
	 *            (arguments).
	 * @param dynamicRegistry
	 *            The dynamic registry which is used to replace {@link Remote}
	 *            return-values.
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
	public void invoke(Executor executor, ProxyManager proxyManager,
			StaticRegistry staticRegistry, DynamicRegistry dynamicRegistry,
			Future future, int methodId, Object... args)
			throws IllegalStateException {

		/*
		 * Create a new invocation and execute it using the given method
		 * context.
		 */
		executeInMethodContext(
				executor,
				methodId,
				createInvocation(proxyManager, staticRegistry, dynamicRegistry,
						future, methodId, args));
	}

	/**
	 * Executes the given runnable within the given method context.
	 * 
	 * @param executor
	 *            The executor.
	 * @param methodId
	 *            The id of the method (context).
	 * @param runnable
	 *            The runnable you want to execute.
	 */
	public void executeInMethodContext(Executor executor, int methodId,
			Runnable runnable) {

		if (runnable == null) {
			throw new NullPointerException("runnable");
		}

		// Try to get the queue
		OrderedExecutionQueue queue = orderedExecutionQueues != null ? orderedExecutionQueues
				.get(methodId) : null;

		/*
		 * Now execute the runnable. Either directly or the ordered execution
		 * queue of the method if necessary.
		 */
		if (queue != null) {

			// Execute queue if necessary
			if (queue.addOrderedExecution(runnable)) {

				// Set to queue
				runnable = queue;
			} else {
				runnable = null;
			}
		}

		// Is there anything to invoke ?
		if (runnable != null) {

			// Invoke with executor ?
			if (executor != null) {
				executor.execute(runnable);
			} else {
				runnable.run();
			}
		}
	}

	/**
	 * @return the remote target.
	 */
	public Remote getTarget() {
		return target;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.foxnet.rmi.binding.Binding#isDynamic()
	 */
	@Override
	public boolean isDynamic() {
		return this instanceof DynamicBinding;
	}
}
