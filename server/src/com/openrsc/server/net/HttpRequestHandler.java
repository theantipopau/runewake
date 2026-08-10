package com.openrsc.server.net;

import com.openrsc.server.Server;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;

public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

	private static final String STATUS_URI = "/status";

	private final String websocketUri;
	private final Server server;

	public HttpRequestHandler(String wsUri, Server server) {
		websocketUri = wsUri;
		this.server = server;
	}

	@Override
	public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
		if (this.websocketUri.equalsIgnoreCase(request.uri())) { // if the request uri matches the web socket path, we forward to next handler which will handle the upgrade handshake
			ctx.fireChannelRead(request.retain()); // we need to increment the reference count to retain the ByteBuf for upcoming processing
		} else if (STATUS_URI.equalsIgnoreCase(request.uri())) {
			writeStatusResponse(ctx, request);
		} else {
			// Otherwise, process your HTTP request and send the flush the response
			HttpResponse response = new DefaultHttpResponse(
				request.protocolVersion(), HttpResponseStatus.OK);
			ctx.write(response);
			ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
			future.addListener(ChannelFutureListener.CLOSE);
		}
	}

	/**
	 * Minimal JSON status endpoint for server-browser / uptime-monitor use
	 * (see ROADMAP.md "Phase 2"). Deliberately reuses only already-public,
	 * already-computed server state (player count, config, start time) —
	 * no new tracking added, so this can't drift from what `::online`/
	 * `::uptime` already report in-game.
	 */
	private void writeStatusResponse(ChannelHandlerContext ctx, FullHttpRequest request) {
		int playerCount = server.getWorld().getPlayers().size();
		long uptimeMillis = (System.nanoTime() - server.getServerStartedTime()) / 1_000_000L;
		String serverName = jsonEscape(server.getConfig().SERVER_NAME);

		String json = "{"
			+ "\"serverName\":\"" + serverName + "\","
			+ "\"players\":" + playerCount + ","
			+ "\"uptimeMillis\":" + uptimeMillis
			+ "}";

		byte[] body = json.getBytes(StandardCharsets.UTF_8);
		ByteBuf content = Unpooled.wrappedBuffer(body);
		FullHttpResponse response = new DefaultFullHttpResponse(
			request.protocolVersion(), HttpResponseStatus.OK, content);
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
		response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
		// Server browsers fetch this cross-origin from a static web page (see web/server-browser/).
		response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

	private static String jsonEscape(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
		throws Exception {
		cause.printStackTrace();
		ctx.close();
	}
}
