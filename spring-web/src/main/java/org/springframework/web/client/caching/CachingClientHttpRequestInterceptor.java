/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.client.caching;

import java.io.IOException;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cache.Cache;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.caching.support.DefaultCachingPolicy;
import org.springframework.web.client.caching.support.DefaultConditionalRequestStrategy;
import org.springframework.web.client.caching.support.DefaultHttpResponseCache;

/**
 * An interceptor that implements an HTTP client cache
 *
 * <p>Stores and retrieves HTTP responses from a given {@link HttpResponseCache}, performing
 * all required operations such as deciding of a response is cacheable, calculating freshness of
 * cached responses, revalidating stale entries using conditional requests, etc.
 *
 * <p>Its behavior can be chosen using {@link CachingPolicy} and {@link ConditionalRequestStrategy}
 * implementations, depending on its nature (is it a shared or a private cache) and the custom
 * features required.
 *
 * <p>TODO: does not support stale-while-revalidate, should serve cached response while revalidating = be async
 * <p>TOOD: wrap response with caching content wrapper to avoid reading content twice?
 *
 * @author Brian Clozel
 * @author Jakub Jirutka
 * @see <a href="https://tools.ietf.org/html/rfc7234">rfc 7234</a>, HTTP/1.1 Caching
 * @see <a href="https://tools.ietf.org/html/rfc5861">rfc 5861</a>, HTTP Cache-Control Extensions for Stale Content
 * @since 4.3
 */
public class CachingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

	protected final Log logger = LogFactory.getLog(getClass());

	private final HttpResponseCache httpResponseCache;

	private final CachingPolicy cachingPolicy;

	private final ConditionalRequestStrategy conditionalRequestStrategy;

	/**
	 * Create a caching interceptor instance with the default options:
	 * <ul>
	 *     <li>a {@link DefaultHttpResponseCache} that caches responses in memory, up to 1Mo per response.</li>
	 *     <li>a {@link DefaultCachingPolicy} that's configured as a private cache (i.e. not shared)
	 *     and stores responses up to 1Mo.</li>
	 *     <li>a {@link DefaultConditionalRequestStrategy}</li>
	 * </ul>
	 */
	public CachingClientHttpRequestInterceptor() {
		this(new DefaultHttpResponseCache(1024 * 1024), new DefaultCachingPolicy(false, 1024 * 1024),
				new DefaultConditionalRequestStrategy());
	}


	/**
	 * Create a caching interceptor instance with the following options:
	 * <ul>
	 *     <li>a {@link DefaultHttpResponseCache} that caches in the given {@code Cache} instance,
	 *     up to {@code maxResponseBodySize} bytes per response.</li>
	 *     <li>a {@link DefaultCachingPolicy} that's configured as a shared or private cache
	 *     (depending on {@code isSharedCache} value) and stores responses up to {@code maxResponseBodySize} bytes.</li>
	 *     <li>a {@link DefaultConditionalRequestStrategy}</li>
	 * </ul>
	 * @param cache the cache instance to store the responses in
	 * @param isSharedCache whether the cache should be considered as shared or not
	 * @param maxResponseBodySize if the response body exceeds that value, it will not be stored in the cache
	 */
	public CachingClientHttpRequestInterceptor(Cache cache, boolean isSharedCache, long maxResponseBodySize) {
		this(new DefaultHttpResponseCache(maxResponseBodySize, cache),
				new DefaultCachingPolicy(isSharedCache, maxResponseBodySize),
				new DefaultConditionalRequestStrategy());
	}

	/**
	 * Create a caching interceptor with full control over its behavior, by providing all the required
	 * implementations for HTTP response caching, caching and conditional requests policies.
	 */
	public CachingClientHttpRequestInterceptor(HttpResponseCache httpResponseCache, CachingPolicy cachingPolicy,
			ConditionalRequestStrategy conditionalRequestStrategy) {
		this.httpResponseCache = httpResponseCache;
		this.cachingPolicy = cachingPolicy;
		this.conditionalRequestStrategy = conditionalRequestStrategy;
	}

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body,
			ClientHttpRequestExecution execution) throws IOException {

		if (this.cachingPolicy.isServableFromCache(request)) {
			HttpCacheEntry cachedResponse = this.httpResponseCache.get(request);
			Date now = new Date();
			if (cachedResponse != null) {
				if (this.cachingPolicy.isCachedResponseUsable(request, cachedResponse, now)) {
					if (this.logger.isTraceEnabled()) {
						logger.trace("Serving response from cache for request \"" + request.getMethod() + " " + request.getURI() + "\"");
					}
					// TODO: mutating headers here may not be a great idea...
					cachedResponse.getHeaders().set(HttpHeaders.AGE, Long.toString(cachedResponse.calculateCurrentAge(now)));
					return cachedResponse;
				}
				else if (this.conditionalRequestStrategy.canRevalidate(cachedResponse)) {
					HttpRequest conditional = this.conditionalRequestStrategy.createConditionalRequest(request, cachedResponse);
					if (this.logger.isTraceEnabled()) {
						logger.trace("Sending conditional request for \"" + request.getMethod() + " " + request.getURI() + "\"");
					}
					return executeAndCacheConditional(conditional, cachedResponse, body, execution);
				}
			}
		}
		if (this.logger.isTraceEnabled()) {
			logger.trace("Request not servable from cache \"" + request.getMethod() + " " + request.getURI() + "\"");
		}
		return executeAndCache(request, body, execution);
	}

	protected ClientHttpResponse executeAndCache(HttpRequest request, byte[] body,
			ClientHttpRequestExecution execution) throws IOException {

		Date requestSent = new Date();
		ClientHttpResponse response = execution.execute(request, body);
		Date responseReceived = new Date();
		if (this.cachingPolicy.isResponseCacheable(request, response)) {
			if (this.logger.isTraceEnabled()) {
				logger.trace("Caching response for request \"" + request.getMethod() + " " + request.getURI() + "\"");
			}
			try {
				return this.httpResponseCache.put(request, response, requestSent, responseReceived);
			}
			catch (Exception exc) {
				logger.warn("Could not cache response for request \"" + request.getMethod() + " "
						+ request.getURI() + "\"", exc);
				return response;
			}
		}
		return response;
	}

	protected ClientHttpResponse executeAndCacheConditional(HttpRequest conditional, HttpCacheEntry cachedResponse,
			byte[] body, ClientHttpRequestExecution execution) throws IOException {

		Date requestSent = new Date();
		ClientHttpResponse response = execution.execute(conditional, body);
		Date responseReceived = new Date();
		if (HttpStatus.NOT_MODIFIED.equals(response.getStatusCode())) {
			if (this.logger.isTraceEnabled()) {
				logger.trace("Conditional request received NOT_MODIFIED, update cache for \""
						+ conditional.getMethod() + " " + conditional.getURI() + "\"");
			}
			this.httpResponseCache.put(conditional, response, requestSent, responseReceived);
			return this.httpResponseCache.get(conditional);
		}
		else if (this.cachingPolicy.isResponseCacheable(conditional, response)) {
			if (this.logger.isTraceEnabled()) {
				logger.trace("Conditional request received updated response for \""
						+ conditional.getMethod() + " " + conditional.getURI() + "\"");
			}
			this.httpResponseCache.put(conditional, response, requestSent, responseReceived);
		}
		else if (response.getStatusCode().is5xxServerError()
				&& this.cachingPolicy.canServeStaleResponseIfError(cachedResponse)) {
			return cachedResponse;
		}
		return response;
	}

}
