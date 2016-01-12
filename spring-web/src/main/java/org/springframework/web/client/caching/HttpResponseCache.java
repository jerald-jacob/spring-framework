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

import java.util.Date;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Cache interface for HTTP client cache implementations.
 *
 * <p>Stores and retrieves {@link ClientHttpResponse} using {@link HttpRequest} to generate
 * cache keys.
 *
 * @author Brian Clozel
 * @since 4.3
 * @see <a href="https://tools.ietf.org/html/rfc7234">rfc 7234</a>, HTTP/1.1 Caching
 */
public interface HttpResponseCache {

	/**
	 * Return a cached response for the given request
	 *
	 * @param request The request
	 * @return A cached response for the given request, or {@code null} if
	 * this cache contains no entry for the request.
	 */
	HttpCacheEntry get(HttpRequest request);

	/**
	 * Store a {@link ClientHttpResponse} in the cache
	 *
	 * TODO: if not 200 OK, should update existing cache entry (404 -> delete, not modified-> extends lifetime?)
	 *
	 * @param request the request sent by the client that triggered the given response
	 * @param response the response to be cached
	 * @param requestSent When the request was sent
	 * @param responseReceived When the response was received
	 */
	HttpCacheEntry put(HttpRequest request, ClientHttpResponse response, Date requestSent, Date responseReceived);

	/**
	 * Delete all cached responses in the cache that match the given request
	 */
	void evict(HttpRequest request);

	/**
	 * Clear the content of the cache
	 */
	void clear();
}
