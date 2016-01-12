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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;

/**
 * Utility class that parses the "Cache-Control" request and response HTTP headers
 * Its use is only intended by HTTP client cache implementations and should not be
 * exposed as a public API.
 *
 * @author Brian Clozel
 * @see org.springframework.web.client.caching.CachingClientHttpRequestInterceptor
 * @since 4.3
 */
class CacheControlHeader {

	private static final Pattern DIRECTIVE_PATTERN = Pattern.compile("\\s*([\\w\\-]+)\\s*(=)?\\s*([^\\s,]*)?\\s*");

	private long maxAge = -1;

	private boolean noCache = false;

	private boolean noStore = false;

	private boolean mustRevalidate = false;

	private boolean noTransform = false;

	private boolean cachePublic = false;

	private boolean cachePrivate = false;

	private boolean proxyRevalidate = false;

	private long sMaxAge = -1;

	private long maxStale = -1;

	private long minFresh = -1;

	private long staleWhileRevalidate = -1;

	private long staleIfError = -1;

	protected CacheControlHeader() {

	}

	/**
	 * Parse the {@code "Cache-Control"} header in the given {@link HttpHeaders} instance
	 */
	public static CacheControlHeader parse(HttpHeaders headers) {

		CacheControlHeader ccHeader = new CacheControlHeader();
		String ccValue = headers.getCacheControl();

		if (ccValue != null) {
			Matcher matcher = DIRECTIVE_PATTERN.matcher(ccValue);
			while (matcher.find()) {
				String directive = matcher.group(1).toLowerCase();

				if ("max-age".equals(directive)) {
					ccHeader.maxAge = Long.parseLong(matcher.group(3));
				}
				else if ("s-maxage".equals(directive)) {
					ccHeader.sMaxAge = Long.parseLong(matcher.group(3));
				}
				else if ("max-stale".equals(directive)) {
					ccHeader.maxStale = Long.parseLong(matcher.group(3));
				}
				else if ("min-fresh".equals(directive)) {
					ccHeader.minFresh = Long.parseLong(matcher.group(3));
				}
				else if ("must-revalidate".equals(directive)) {
					ccHeader.mustRevalidate = true;
				}
				else if ("no-cache".equals(directive)) {
					ccHeader.noCache = true;
				}
				else if ("no-store".equals(directive)) {
					ccHeader.noStore = true;
				}
				else if ("no-transform".equals(directive)) {
					ccHeader.noTransform = true;
				}
				else if ("private".equals(directive)) {
					ccHeader.cachePrivate = true;
				}
				else if ("public".equals(directive)) {
					ccHeader.cachePublic = true;
				}
				else if ("proxy-revalidate".equals(directive)) {
					ccHeader.proxyRevalidate = true;
				}
				else if ("stale-while-revalidate".equals(directive)) {
					ccHeader.staleWhileRevalidate = Long.parseLong(matcher.group(3));
				}
				else if ("stale-if-error".equals(directive)) {
					ccHeader.staleIfError = Long.parseLong(matcher.group(3));
				}
			}
		}

		return ccHeader;
	}

	public long getMaxAge() {
		return maxAge;
	}

	public boolean isNoCache() {
		return noCache;
	}

	public boolean isNoStore() {
		return noStore;
	}

	public boolean isMustRevalidate() {
		return mustRevalidate;
	}

	public boolean isNoTransform() {
		return noTransform;
	}

	public boolean isCachePublic() {
		return cachePublic;
	}

	public boolean isCachePrivate() {
		return cachePrivate;
	}

	public boolean isProxyRevalidate() {
		return proxyRevalidate;
	}

	public long getsMaxAge() {
		return sMaxAge;
	}

	public long getMaxStale() {
		return maxStale;
	}

	public long getMinFresh() {
		return minFresh;
	}

	public long getStaleWhileRevalidate() {
		return staleWhileRevalidate;
	}

	public long getStaleIfError() {
		return staleIfError;
	}

}
