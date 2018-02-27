package org.springframework.web.reactive;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.logging.Logger;

import org.hamcrest.Matchers;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.server.reactive.AbstractHttpHandlerIntegrationTests;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.bootstrap.TomcatHttpServer;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.HttpWebHandlerAdapter;
import org.springframework.web.server.handler.FilteringWebHandler;

import static org.junit.Assert.assertThat;

public class TomcatAsyncTimeoutWebFilterIntegrationTests extends AbstractHttpHandlerIntegrationTests {

	private Logger logger = Logger.getLogger(getClass().getName());

	private WebClient webClient;

	@Before
	public void setup() throws Exception {
		super.setup();
		this.webClient = WebClient.create("http://localhost:" + this.port);
	}

	@Test
	public void asyncTimeoutHandling() {
		Assume.assumeThat(this.server, Matchers.instanceOf(TomcatHttpServer.class));

		Flux<String> result = this.webClient.get()
				.uri("/")
				.retrieve()
				.bodyToFlux(String.class);

		StepVerifier.create(result)
				.expectNextMatches(s -> s.startsWith("data0"))
				.thenCancel()
				.verify(Duration.ofSeconds(10L));
	}

	@Override
	protected HttpHandler createHttpHandler() {
		WebHandler streaming = new WebHandler() {
			@Override
			public Mono<Void> handle(ServerWebExchange exchange) {
				Flux<Publisher<DataBuffer>> responseBody = interval(Duration.ofMillis(50), 100)
						.map(l -> toDataBuffer("data" + l + "\n", exchange.getResponse().bufferFactory()))
						.map(Flux::just);

				Mono<Void> handler = exchange.getResponse().writeAndFlushWith(responseBody.concatWith(Flux.never()));

				return handler.doOnError(throwable -> {
					// getting a "java.lang.IllegalStateException: Async operation timeout."
					// at first the request information is available
					logger.info("Request Method: " + exchange.getRequest().getMethodValue());
					logger.info(exchange.getRequest().toString());

					// after some time the request information has been recycled by Tomcat
					// with a call to org.apache.coyote.Request::recycle(); after that, the
					// ServerHttpRequest holds bogus information (null fields only)
					for(int i=0; i<10; i++) {
						assertThat(exchange.getRequest().getMethodValue(), Matchers.equalToIgnoringCase("GET"));
					}
				});
			}
		};

		WebFilter testFilter = new WebFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
				return chain.filter(exchange).doOnError(throwable -> {
					// getting a "java.lang.IllegalStateException: Async operation timeout."
					// at first the request information is available
					logger.info("Request Method: " + exchange.getRequest().getMethodValue());
					logger.info(exchange.getRequest().toString());

					// after some time the request information has been recycled by Tomcat
					// with a call to org.apache.coyote.Request::recycle(); after that, the
					// ServerHttpRequest holds bogus information (null fields only)
					for(int i=0; i<10; i++) {
						assertThat(exchange.getRequest().getMethodValue(), Matchers.equalToIgnoringCase("GET"));
					}
				});
			}
		};

		return new HttpWebHandlerAdapter(new FilteringWebHandler(streaming, Arrays.asList(testFilter)));
	}

	private DataBuffer toDataBuffer(String value, DataBufferFactory factory) {
		byte[] data = (value).getBytes(StandardCharsets.UTF_8);
		DataBuffer buffer = factory.allocateBuffer(data.length);
		buffer.write(data);
		return buffer;
	}
}
