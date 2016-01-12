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
 * A policy interface that describes the HTTP cache behavior when:
 * <ul>
 *     <li>checking if a given request is eligible to be fulfilled by a cached response
 *     <li>checking if a cached response can be used to fulfill a given request
 *     <li>checking if a received response can be reused later and should be cached
 * </ul>
 *
 * <p>Implementations should be carefully chosen depending on the nature of the HTTP client cache,
 * i.e. is it a shared or a private cache? How should it deal with stale cached responses?
 *
 * @author Brian Clozel
 * @since 4.3
 * @see <a href="https://tools.ietf.org/html/rfc7234">rfc 7234</a>, HTTP/1.1 Caching
 */
public interface CachingPolicy {

	/**
	 * Check whether the given request can be served using a cached response
	 *
	 * <p>Information on the request, such as a {@code "Cache-Control: no-cache"} HTTP header
	 * signal that a request should not be served from the cache.
	 *
	 * @param request the request to check
	 * @return {@code true} is the request can be served from cache, {@false} otherwise
	 */
	boolean isServableFromCache(HttpRequest request);

	/**
	 * Check whether the given response retrieved from the cache can be used to fulfill
	 * the given request
	 *
	 * @param request the request to be fulfilled
	 * @param response the cached response considered to fulfill the given request
	 * @param now the current date
	 * @return {@code true} is the response can be used, {@false} otherwise
	 */
	boolean isCachedResponseUsable(HttpRequest request, HttpCacheEntry response, Date now);

	/**
	 * Check whether the response received for the given request can be stored
	 * in the HTTP client cache for future use
	 *
	 * @param request the request that has been fulfilled by the given response
	 * @param response the response that may be stored in the cache
	 * @return {@code true} is the response is cacheable, {@false} otherwise
	 */
	boolean isResponseCacheable(HttpRequest request, ClientHttpResponse response);

	/**
	 * Check whether the given stale cached response can still be used as the origin
	 * server responded with an error to our conditional request. Implementations
	 * should check for response headers, such as the rfc 5861 "stale-if-error" directive.
	 *
	 * @param response the cached response, which is now stale
	 * @return {@code true} is the response can be reused, {@false} otherwise
	 * @see <a href="https://tools.ietf.org/html/rfc5861">rfc 5861</a>, HTTP Cache-Control Extensions for Stale Content
	 */
	boolean canServeStaleResponseIfError(HttpCacheEntry response);
}
