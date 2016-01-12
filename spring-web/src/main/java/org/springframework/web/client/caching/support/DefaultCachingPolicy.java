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

package org.springframework.web.client.caching.support;

import static java.util.Arrays.*;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.caching.CachingPolicy;
import org.springframework.web.client.caching.HttpCacheEntry;

/**
 * This {@link CachingPolicy} mostly follows rfc 7234 and rfc 5862 and
 * assist the {@link org.springframework.web.client.caching.CachingClientHttpRequestInterceptor}
 * in its caching decisions.
 *
 * <p>This caching policy can be configured as a shared cache or a private cache,
 * depending on the expected behavior of the cache regarding private information.
 * Also, a maximum response body size can be configured to save resources in the
 * configured {@code Cache} instance.
 *
 * <p>This implementation does not strictly follow the rfcs and has some limitations:
 * <ul>
 *     <li>it does not cache responses to range requests</li>
 *     <li>it does not cache responses to requests with "Vary" headers</li>
 * </ul>
 *
 * @author Brian Clozel
 * @since 4.3
 * @see <a href="https://tools.ietf.org/html/rfc7234#section-4.2">rfc 7234, HTTP/1.1 Caching</a>
 * @see <a href="http://tools.ietf.org/html/rfc5861">rfc 5861, Cache-Control extensions for stale content</a>
 */
public class DefaultCachingPolicy implements CachingPolicy {

	/**
	 * See https://tools.ietf.org/html/rfc7231#section-6.1
	 */
	private static final Set<Integer> CACHEABLE_STATUSES =
			new HashSet<Integer>(asList(200, 203, 204, 300, 301, 404, 405, 410, 414, 501));

	private final long maxResponseBodySize;

	private final boolean isSharedCache;

	private long defaultFreshness;

	/**
	 * Create a {@code DefaultCachingPolicy} instance that will cache
	 * responses of size up to {@code maxResponseBodySize}.
	 * Depending on the {@code isSharedCache} parameter, the cache implementation
	 * will behave as a private cache or a shared cache.
	 *
	 * <p>A shared cache won't typically store responses that are marked as "private",
	 * contain authentication credentials or Cookies.
	 *
	 * @param isSharedCache whether this cache instance is shared
	 * @param maxResponseBodySize the maximum body size of cached responses
	 */
	public DefaultCachingPolicy(boolean isSharedCache, long maxResponseBodySize) {
		this.isSharedCache = isSharedCache;
		this.maxResponseBodySize = maxResponseBodySize;
		this.defaultFreshness = TimeUnit.HOURS.toSeconds(1);
	}

	/**
	 * Return the default freshness enforced by the Caching Policy, in seconds.
	 *
	 * <p>If no freshness information is provided by the origin server,
	 * but the response may still be cached, the Caching Policy can
	 * decide to cache a response for this custom duration.
	 */
	public long getDefaultFreshness() {
		return defaultFreshness;
	}

	/**
	 * Set the default freshness for this Caching Policy,
	 * to be applied to cached responses if none is provided by the origin server.
	 *
	 * @param freshness the default freshness
	 * @param unit the time unit of the {@code duration} argument
	 */
	public void setDefaultFreshness(long freshness, TimeUnit unit) {
		this.defaultFreshness = unit.toSeconds(freshness);
	}

	@Override
	public boolean isServableFromCache(HttpRequest request) {
		if (request.getMethod().matches("GET")) {
			CacheControlHeader ccHeader = CacheControlHeader.parse(request.getHeaders());
			if (!ccHeader.isNoCache() && !ccHeader.isNoStore()
					&& ccHeader.getMaxAge() != 0 && request.getHeaders().getRange().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/*
	 * See https://tools.ietf.org/html/rfc7234#section-4.2
	 */
	@Override
	public boolean isCachedResponseUsable(HttpRequest request, HttpCacheEntry response, Date now) {
		CacheControlHeader ccRequestHeader = CacheControlHeader.parse(request.getHeaders());
		CacheControlHeader ccResponseHeader = CacheControlHeader.parse(response.getHeaders());
		boolean revalidate = ccResponseHeader.isMustRevalidate();
		if (this.isSharedCache) {
			revalidate = revalidate || ccResponseHeader.isProxyRevalidate();
		}
		if (!revalidate && ccRequestHeader.getMaxStale() != -1) {
			return calculateFreshnessLifetime(response) + ccRequestHeader.getMaxStale() > response.calculateCurrentAge(now);
		}
		else if (ccRequestHeader.getMinFresh() != -1) {
			return calculateFreshnessLifetime(response) - ccRequestHeader.getMinFresh() > response.calculateCurrentAge(now);
		}
		else if (ccRequestHeader.getMaxAge() != -1) {
			return response.calculateCurrentAge(now) < ccRequestHeader.getMaxAge();
		}
		return calculateFreshnessLifetime(response) > response.calculateCurrentAge(now);
	}

	/*
	 * See https://tools.ietf.org/html/rfc7234#section-4.2.1
	 */
	protected long calculateFreshnessLifetime(ClientHttpResponse response) {
		CacheControlHeader ccHeader = CacheControlHeader.parse(response.getHeaders());
		if (this.isSharedCache) {
			if (ccHeader.getsMaxAge() > 0) {
				return ccHeader.getsMaxAge();
			}
		}
		if (ccHeader.getMaxAge() > 0) {
			return ccHeader.getMaxAge();
		}
		else if (response.getHeaders().getExpires() > 0) {
			return (response.getHeaders().getExpires() - response.getHeaders().getDate()) / 1000L;
		}
		return this.defaultFreshness;
	}


	/*
	 * See https://tools.ietf.org/html/rfc7234#section-3
	 */
	@Override
	public boolean isResponseCacheable(HttpRequest request, ClientHttpResponse response) {

		if (!isServableFromCache(request)) {
			return false;
		}
		try {
			if (!CACHEABLE_STATUSES.contains(response.getRawStatusCode())) {
				return false;
			}
		}
		catch (IOException exc) {
			throw new IllegalStateException(exc);
		}
		CacheControlHeader ccResponseHeader = CacheControlHeader.parse(response.getHeaders());
		if (ccResponseHeader.isCachePrivate() || ccResponseHeader.isNoStore()) {
			return false;
		}
		if (this.isSharedCache) {
			if (request.getHeaders().get(HttpHeaders.AUTHORIZATION) != null) {
				if (ccResponseHeader.isCachePublic() && ccResponseHeader.getsMaxAge() <= 0) {
					return false;
				}
			}
		}
		if (response.getHeaders().containsKey(HttpHeaders.VARY)) {
			return false;
		}
		try {
			if (response.getHeaders().getDate() == -1) {
				return false;
			}
		}
		catch (IllegalArgumentException exc) {
			return false;
		}
		if (response.getHeaders().getContentLength() > this.maxResponseBodySize) {
			return false;
		}
		return ccResponseHeader.getsMaxAge() > 0
				|| ccResponseHeader.getMaxAge() > 0
				|| ccResponseHeader.isCachePublic()
				|| response.getHeaders().getExpires() > new Date().getTime();
	}

	@Override
	public boolean canServeStaleResponseIfError(HttpCacheEntry response) {
		return true;
	}
}
