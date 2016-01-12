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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Assert;
import org.springframework.web.client.caching.HttpCacheEntry;
import org.springframework.web.client.caching.HttpResponseCache;

/**
 *
 * size limit, key generated from URL (do not take into account http headers)
 * @author Brian Clozel
 * @since 4.3
 */
public class DefaultHttpResponseCache implements HttpResponseCache {

	private static final String DEFAULT_CACHE_NAME = "spring-web-client-cache";

	public static final int DEFAULT_BUFFER_SIZE = 4096;

	private final long maxResponseBodySize;

	private final Cache cache;

	public DefaultHttpResponseCache(long maxResponseBodySize) {
		this(maxResponseBodySize, new ConcurrentMapCache(DEFAULT_CACHE_NAME, false));
	}

	public DefaultHttpResponseCache(long maxResponseBodySize, Cache cache) {
		this.maxResponseBodySize = maxResponseBodySize;
		this.cache = cache;
	}

	@Override
	public HttpCacheEntry get(HttpRequest request) {
		return this.cache.get(generateCacheKey(request), HttpCacheEntry.class);
	}

	private String generateCacheKey(HttpRequest request) {
		return request.getURI().toString();
	}

	@Override
	public HttpCacheEntry put(HttpRequest request, ClientHttpResponse response, Date requestSent, Date responseReceived) {
		Assert.notNull(response, "Http client response must not be null");
		try {
			byte[] body = readResponseBody(request, response);
			HttpCacheEntry entry = new InMemoryHttpCacheEntry(body, response.getStatusCode(), response.getHeaders(),
					requestSent, responseReceived);
			this.cache.put(generateCacheKey(request), entry);
			return entry;
		}
		catch (IOException exc) {
			throw new HttpResponseCacheException(exc);
		}
	}

	private byte[] readResponseBody(HttpRequest request, ClientHttpResponse response) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(DEFAULT_BUFFER_SIZE);
		InputStream in = response.getBody();

		int byteCount = 0;
		byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
		int bytesRead;
		while ((bytesRead = in.read(buffer)) != -1) {
			out.write(buffer, 0, bytesRead);
			byteCount += bytesRead;
			if (byteCount > this.maxResponseBodySize - 1) {
				throw new IllegalArgumentException("Client Http response for " + request.getURI().toString()
						+ " exceeds size limit of " + this.maxResponseBodySize);
			}
		}
		out.flush();
		response.close();
		return out.toByteArray();
	}

	@Override
	public void evict(HttpRequest request) {
		this.cache.evict(generateCacheKey(request));
	}

	@Override
	public void clear() {
		this.cache.clear();
	}

}
