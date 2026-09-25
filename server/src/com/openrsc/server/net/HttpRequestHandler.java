package com.openrsc.server.net;

import com.openrsc.server.Server;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;

/**
 * Serves the small, read-only HTTP face of the world on the WebSocket port:
 * a JSON status document for server browsers, a liveness probe, and a
 * Prometheus-style metrics exposition for uptime monitors.
 *
 * Everything here reuses already-public, already-computed server state (player
 * count, configured slot limit, start time, server name) so these endpoints
 * cannot drift from what {@code ::online}/{@code ::uptime} report in-game, and
 * no request touches the database.
 */
public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

	private static final String STATUS_URI = "/status";
	private static final String HEALTH_URI = "/healthz";
	private static final String METRICS_URI = "/metrics";
	private static final String PROMETHEUS_CONTENT_TYPE = "text/plain; version=0.0.4; charset=UTF-8";

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
			return;
		}

		// Tolerate query strings (?t=...) so a browser or monitor can cache-bust
		// without turning a known route into a 404.
		String path = stripQuery(request.uri());

		HttpMethod method = request.method();
		boolean headOnly = HttpMethod.HEAD.equals(method);
		if (!HttpMethod.GET.equals(method) && !headOnly) {
			writeBody(ctx, request, HttpResponseStatus.METHOD_NOT_ALLOWED, "text/plain; charset=UTF-8",
				"method not allowed\n", false, HttpHeaderNames.ALLOW, "GET, HEAD");
			return;
		}

		if (STATUS_URI.equalsIgnoreCase(path)) {
			writeStatusResponse(ctx, request, headOnly);
		} else if (HEALTH_URI.equalsIgnoreCase(path)) {
			writeBody(ctx, request, HttpResponseStatus.OK, "text/plain; charset=UTF-8", "ok\n", headOnly);
		} else if (METRICS_URI.equalsIgnoreCase(path)) {
			writeMetricsResponse(ctx, request, headOnly);
		} else {
			writeBody(ctx, request, HttpResponseStatus.NOT_FOUND, "text/plain; charset=UTF-8", "not found\n", headOnly);
		}
	}

	/**
	 * Minimal JSON status endpoint for server-browser / uptime-monitor use
	 * (see ROADMAP.md "Phase 2"). Deliberately reuses only already-public,
	 * already-computed server state (player count, slot limit, config, start
	 * time) — no new tracking added, so this can't drift from what
	 * {@code ::online}/{@code ::uptime} already report in-game. The original
	 * fields (serverName/players/uptimeMillis) are unchanged; maxPlayers and
	 * uptimeSeconds are additive so existing consumers keep working.
	 */
	private void writeStatusResponse(ChannelHandlerContext ctx, FullHttpRequest request, boolean headOnly) {
		int playerCount = server.getWorld().getPlayers().size();
		int maxPlayers = server.getConfig().MAX_PLAYERS;
		long uptimeMillis = (System.nanoTime() - server.getServerStartedTime()) / 1_000_000L;
		String serverName = jsonEscape(server.getConfig().SERVER_NAME);

		String json = "{"
			+ "\"serverName\":\"" + serverName + "\","
			+ "\"players\":" + playerCount + ","
			+ "\"maxPlayers\":" + maxPlayers + ","
			+ "\"uptimeMillis\":" + uptimeMillis + ","
			+ "\"uptimeSeconds\":" + (uptimeMillis / 1000L)
			+ "}";

		writeBody(ctx, request, HttpResponseStatus.OK, "application/json; charset=UTF-8", json, headOnly);
	}

	/**
	 * Prometheus text exposition of the same gauges /status reports, so a
	 * monitor or dashboard can scrape without a JSON parser. Values are read
	 * once per scrape; nothing is tracked between requests.
	 */
	private void writeMetricsResponse(ChannelHandlerContext ctx, FullHttpRequest request, boolean headOnly) {
		int playerCount = server.getWorld().getPlayers().size();
		int maxPlayers = server.getConfig().MAX_PLAYERS;
		long uptimeSeconds = (System.nanoTime() - server.getServerStartedTime()) / 1_000_000_000L;

		String body = "# HELP runewake_players_online Players currently logged in.\n"
			+ "# TYPE runewake_players_online gauge\n"
			+ "runewake_players_online " + playerCount + "\n"
			+ "# HELP runewake_players_max Configured player slot limit.\n"
			+ "# TYPE runewake_players_max gauge\n"
			+ "runewake_players_max " + maxPlayers + "\n"
			+ "# HELP runewake_uptime_seconds Seconds since the world started.\n"
			+ "# TYPE runewake_uptime_seconds gauge\n"
			+ "runewake_uptime_seconds " + uptimeSeconds + "\n";

		writeBody(ctx, request, HttpResponseStatus.OK, PROMETHEUS_CONTENT_TYPE, body, headOnly);
	}

	private void writeBody(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status,
						   String contentType, String body, boolean headOnly) {
		writeBody(ctx, request, status, contentType, body, headOnly, null, null);
	}

	/**
	 * Writes a full response with an explicit length. A HEAD request reports the
	 * body length a GET would return but sends no content, per RFC 9110.
	 */
	private void writeBody(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status,
						   String contentType, String body, boolean headOnly,
						   CharSequence extraHeader, String extraValue) {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		ByteBuf content = headOnly ? Unpooled.EMPTY_BUFFER : Unpooled.wrappedBuffer(bytes);
		FullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(), status, content);
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
		response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
		response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store");
		// Server browsers fetch this cross-origin from a static web page (see web/server-browser/).
		response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
		if (extraHeader != null) {
			response.headers().set(extraHeader, extraValue);
		}

		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

	private static String stripQuery(String uri) {
		int query = uri.indexOf('?');
		return query >= 0 ? uri.substring(0, query) : uri;
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
