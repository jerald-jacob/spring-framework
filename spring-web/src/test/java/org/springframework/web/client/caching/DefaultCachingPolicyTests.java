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

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.caching.support.DefaultCachingPolicy;

/**
 * Test fixture for {@link DefaultCachingPolicyTests}.
 *
 * @author Brian Clozel
 */
public class DefaultCachingPolicyTests {

	private static final Charset UTF8 = Charset.forName("UTF-8");

	private static final String TEST_BODY = "testbody";

	private DefaultCachingPolicy privateCachingPolicy;

	private DefaultCachingPolicy sharedCachingPolicy;

	private HttpRequest request;

	private HttpHeaders requestHeaders;

	private ClientHttpResponse response;

	private HttpHeaders responseHeaders;

	private HttpCacheEntry cacheEntry;

	private HttpHeaders cacheEntryHeaders;

	@Before
	public void setup() throws Exception {
		this.request = mock(ClientHttpRequest.class);
		this.requestHeaders = new HttpHeaders();
		this.response = mock(ClientHttpResponse.class);
		this.responseHeaders = new HttpHeaders();
		this.cacheEntry = mock(HttpCacheEntry.class);
		this.cacheEntryHeaders = new HttpHeaders();

		this.requestHeaders.setAccept(Collections.singletonList(MediaType.ALL));
		given(this.request.getMethod()).willReturn(HttpMethod.GET);
		given(this.request.getURI()).willReturn(new URI("http://example.org/resource"));
		given(this.request.getHeaders()).willReturn(this.requestHeaders);

		//this.responseHeaders.setDate(new Date().getTime());
		//this.responseHeaders.setContentType(MediaType.TEXT_PLAIN);
		//this.responseHeaders.setContentLength(TEST_BODY.length());
		given(this.response.getStatusCode()).willReturn(HttpStatus.OK);
		given(this.response.getRawStatusCode()).willReturn(HttpStatus.OK.value());
		given(this.response.getHeaders()).willReturn(this.responseHeaders);
		//given(this.response.getBody()).willReturn(new ByteArrayInputStream(TEST_BODY.getBytes(UTF8)));

		given(this.cacheEntry.getStatusCode()).willReturn(HttpStatus.OK);
		given(this.cacheEntry.getRawStatusCode()).willReturn(HttpStatus.OK.value());
		given(this.cacheEntry.getHeaders()).willReturn(this.cacheEntryHeaders);

		this.privateCachingPolicy = new DefaultCachingPolicy(false, 1024);
		this.sharedCachingPolicy = new DefaultCachingPolicy(true, 1024);
	}

	@Test
	public void shouldNotServeNoStoreFromCache() throws Exception {

		this.requestHeaders.setCacheControl(CacheControl.noStore().getHeaderValue());
		Assert.assertFalse(this.privateCachingPolicy.isServableFromCache(this.request));
	}

	@Test
	public void shouldNotServeNoCacheFromCache() throws Exception {

		this.requestHeaders.setCacheControl(CacheControl.noCache().getHeaderValue());
		Assert.assertFalse(this.privateCachingPolicy.isServableFromCache(this.request));
	}

	@Test
	public void shouldNotServeMaxAgeZeroFromCache() throws Exception {

		this.requestHeaders.setCacheControl(CacheControl.maxAge(0, TimeUnit.SECONDS).getHeaderValue());
		Assert.assertFalse(this.privateCachingPolicy.isServableFromCache(this.request));
	}

	@Test
	public void shouldNotServeRangeFromCache() throws Exception {

		this.requestHeaders.setRange(Collections.singletonList(HttpRange.createByteRange(42)));
		Assert.assertFalse(this.privateCachingPolicy.isServableFromCache(this.request));
	}

	@Test
	public void shouldNotServePostFromCache() throws Exception {

		given(this.request.getMethod()).willReturn(HttpMethod.POST);
		Assert.assertFalse(this.privateCachingPolicy.isServableFromCache(this.request));
	}

	@Test
	public void shouldServeFreshMaxAgeCachedEntry() throws Exception {

		this.cacheEntryHeaders.setCacheControl(CacheControl.maxAge(100, TimeUnit.SECONDS).getHeaderValue());
		given(this.cacheEntry.calculateCurrentAge(any(Date.class))).willReturn(10L);
		Assert.assertTrue(this.privateCachingPolicy.isCachedResponseUsable(this.request, this.cacheEntry, new Date()));
	}

	@Test
	public void shouldServeFreshExpiresCachedEntry() throws Exception {

		this.cacheEntryHeaders.setExpires(new Date().getTime() + 20 * 1000);
		this.cacheEntryHeaders.setDate(new Date().getTime());
		given(this.cacheEntry.calculateCurrentAge(any(Date.class))).willReturn(10L);
		Assert.assertTrue(this.privateCachingPolicy.isCachedResponseUsable(this.request, this.cacheEntry, new Date()));
	}

	@Test
	public void shouldServeFreshCachedEntrySharedCache() throws Exception {

		this.cacheEntryHeaders.setCacheControl(CacheControl.empty().sMaxAge(3700, TimeUnit.SECONDS).getHeaderValue());
		given(this.cacheEntry.calculateCurrentAge(any(Date.class))).willReturn(3601L);
		Assert.assertFalse(this.privateCachingPolicy.isCachedResponseUsable(this.request, this.cacheEntry, new Date()));
		Assert.assertTrue(this.sharedCachingPolicy.isCachedResponseUsable(this.request, this.cacheEntry, new Date()));
	}

	@Test
	public void shouldNotServeStaleCachedEntry() throws Exception {

		this.cacheEntryHeaders.setCacheControl(CacheControl.maxAge(100, TimeUnit.SECONDS).getHeaderValue());
		given(this.cacheEntry.calculateCurrentAge(any(Date.class))).willReturn(200L);
		Assert.assertFalse(this.privateCachingPolicy.isCachedResponseUsable(this.request, this.cacheEntry, new Date()));
	}

	@Test
	public void shouldServeCachedEntryWithDefaultFreshness() throws Exception {

		this.privateCachingPolicy.setDefaultFreshness(3600, TimeUnit.SECONDS);
		given(this.cacheEntry.calculateCurrentAge(any(Date.class))).willReturn(3599L);
		Assert.assertTrue(this.privateCachingPolicy.isCachedResponseUsable(this.request, this.cacheEntry, new Date()));
	}

	@Test
	public void shouldNotServeStaleMaxAgeCachedEntry() throws Exception {

		this.requestHeaders.setCacheControl(CacheControl.maxAge(100, TimeUnit.SECONDS).getHeaderValue());
		given(this.cacheEntry.calculateCurrentAge(any(Date.class))).willReturn(200L);
		Assert.assertFalse(this.privateCachingPolicy.isCachedResponseUsable(this.request, this.cacheEntry, new Date()));
	}

	@Test
	public void shouldServeStaleCacheEntryMaxStaleRequest() throws Exception {

		this.requestHeaders.setCacheControl("max-stale=20");
		this.cacheEntryHeaders.setCacheControl(CacheControl.maxAge(200, TimeUnit.SECONDS).getHeaderValue());
		given(this.cacheEntry.calculateCurrentAge(any(Date.class))).willReturn(210L);
		Assert.assertTrue(this.privateCachingPolicy.isCachedResponseUsable(this.request, this.cacheEntry, new Date()));
	}

	@Test
	public void shouldNotServeStaleMustRevalidateCacheEntryMaxStaleRequest() throws Exception {

		this.requestHeaders.setCacheControl("max-stale=20");
		this.cacheEntryHeaders.setCacheControl(CacheControl.maxAge(200, TimeUnit.SECONDS).mustRevalidate().getHeaderValue());
		given(this.cacheEntry.calculateCurrentAge(any(Date.class))).willReturn(210L);
		Assert.assertFalse(this.privateCachingPolicy.isCachedResponseUsable(this.request, this.cacheEntry, new Date()));
	}

	@Test
	public void shouldNotServeStaleProxyRevalidateCacheEntryMaxStaleRequest() throws Exception {

		this.requestHeaders.setCacheControl("max-stale=20");
		this.cacheEntryHeaders.setCacheControl(CacheControl.maxAge(200, TimeUnit.SECONDS).proxyRevalidate().getHeaderValue());
		given(this.cacheEntry.calculateCurrentAge(any(Date.class))).willReturn(210L);
		Assert.assertTrue(this.privateCachingPolicy.isCachedResponseUsable(this.request, this.cacheEntry, new Date()));
		Assert.assertFalse(this.sharedCachingPolicy.isCachedResponseUsable(this.request, this.cacheEntry, new Date()));
	}

	@Test
	public void shouldNotServeFreshMaxAgeMinFreshCachedEntry() throws Exception {

		this.requestHeaders.setCacheControl("min-fresh=60");
		this.cacheEntryHeaders.setCacheControl(CacheControl.maxAge(100, TimeUnit.SECONDS).getHeaderValue());
		given(this.cacheEntry.calculateCurrentAge(any(Date.class))).willReturn(50L);
		Assert.assertFalse(this.privateCachingPolicy.isCachedResponseUsable(this.request, this.cacheEntry, new Date()));
	}


}
