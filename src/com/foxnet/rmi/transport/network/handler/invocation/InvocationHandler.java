package com.foxnet.rmi.transport.network.handler.invocation;

import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

import com.foxnet.rmi.binding.registry.DynamicRegistry;
import com.foxnet.rmi.binding.registry.ProxyManager;
import com.foxnet.rmi.binding.registry.Registry;
import com.foxnet.rmi.transport.network.ConnectionManager;
import com.foxnet.rmi.util.Request;

@Sharable
public class InvocationHandler extends SimpleChannelHandler {

	@Override
	public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
			throws Exception {
		ctx.setAttachment(new DynamicRegistry());

		super.channelOpen(ctx, e);
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
			throws Exception {

		if (e.getMessage() instanceof Request) {
			// Convert to request
			Request request = (Request) e.getMessage();

			if (request.getData() instanceof InvocationMessage) {
				InvocationMessage im = (InvocationMessage) request.getData();

				ConnectionManager cm = ConnectionManager.of(ctx.getChannel());
				DynamicRegistry dr = (DynamicRegistry) ctx.getAttachment();
				if (cm != null) {

					// Do invocation
					Registry.invoke(im.isDynamic(), im.getBindingId(),
							cm.getMethodInvocator(), new ProxyManager() {

								@Override
								public Object replaceRemoteObject(
										Object remoteObject) {
									return remoteObject;
								}

								@Override
								public Object replaceProxy(Object proxy) {
									return proxy;
								}
							}, cm.getStaticRegistry(), dr, request, im
									.getMethodId(), im.getArguments());

				}
			}
		}

		super.messageReceived(ctx, e);
	}
}
