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

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.*;

import java.net.URI;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Test fixture for {@link CachingClientHttpRequestInterceptor}.
 *
 * @author Brian Clozel
 */
public class CachingClientHttpRequestInterceptorTests {

	private ClientHttpRequestExecution clientExecution;

	private CachingPolicy cachingPolicy;

	private HttpResponseCache httpResponseCache;

	private ConditionalRequestStrategy conditionalRequestStrategy;

	private CachingClientHttpRequestInterceptor interceptor;

	private HttpRequest request;

	private ClientHttpResponse response;

	private byte[] body;

	@Before
	public void setup() throws Exception {
		this.clientExecution = mock(ClientHttpRequestExecution.class);
		this.cachingPolicy = mock(CachingPolicy.class);
		this.httpResponseCache = mock(HttpResponseCache.class);
		this.conditionalRequestStrategy = mock(ConditionalRequestStrategy.class);

		this.interceptor = new CachingClientHttpRequestInterceptor(this.httpResponseCache, this.cachingPolicy,
				this.conditionalRequestStrategy);

		this.request = mock(HttpRequest.class);
		given(this.request.getMethod()).willReturn(HttpMethod.GET);
		given(this.request.getURI()).willReturn(new URI("http://example.org/resource"));
		this.response = mock(ClientHttpResponse.class);
		this.body = new byte[0];
	}

	@Test
	public void requestNotServableFromCache() throws Exception {
		given(this.cachingPolicy.isServableFromCache(this.request)).willReturn(false);
		given(this.clientExecution.execute(this.request, this.body)).willReturn(this.response);
		given(this.cachingPolicy.isResponseCacheable(this.request, this.response)).willReturn(false);

		ClientHttpResponse actual = this.interceptor.intercept(this.request, this.body, this.clientExecution);

		assertThat(actual, is(this.response));
		then(this.cachingPolicy).should().isServableFromCache(this.request);
		then(this.clientExecution).should().execute(this.request, this.body);
		then(this.cachingPolicy).should().isResponseCacheable(this.request, this.response);
	}

	@Test
	public void cacheMissAndResponseNotCacheable() throws Exception {
		given(this.cachingPolicy.isServableFromCache(this.request)).willReturn(true);
		given(this.httpResponseCache.get(this.request)).willReturn(null);
		given(this.clientExecution.execute(this.request, this.body)).willReturn(this.response);
		given(this.cachingPolicy.isResponseCacheable(this.request, this.response)).willReturn(false);

		ClientHttpResponse actual = this.interceptor.intercept(this.request, this.body, this.clientExecution);

		assertThat(actual, is(this.response));
		then(this.cachingPolicy).should().isServableFromCache(this.request);
		then(this.httpResponseCache).should().get(this.request);
		then(this.clientExecution).should().execute(this.request, this.body);
		then(this.cachingPolicy).should().isResponseCacheable(this.request, this.response);
		then(this.clientExecution).should().execute(this.request, this.body);
	}

	@Test
	public void cacheMissAndResponseCacheable() throws Exception {
		given(this.cachingPolicy.isServableFromCache(this.request)).willReturn(true);
		given(this.httpResponseCache.get(this.request)).willReturn(null);
		given(this.clientExecution.execute(this.request, this.body)).willReturn(this.response);
		given(this.cachingPolicy.isResponseCacheable(this.request, this.response)).willReturn(true);

		ClientHttpResponse actual = this.interceptor.intercept(this.request, this.body, this.clientExecution);

		assertThat(actual, is(this.response));
		then(this.cachingPolicy).should().isServableFromCache(this.request);
		then(this.httpResponseCache).should().get(this.request);
		then(this.clientExecution).should().execute(this.request, this.body);
		then(this.cachingPolicy).should().isResponseCacheable(this.request, this.response);
		then(this.httpResponseCache).should().put(eq(this.request), eq(this.response), any(Date.class), any(Date.class));
	}

	@Test
	public void cachedResponseNotUsableResponseNotCacheable() throws Exception {
		HttpCacheEntry cached = mock(HttpCacheEntry.class);

		given(this.cachingPolicy.isServableFromCache(this.request)).willReturn(true);
		given(this.httpResponseCache.get(this.request)).willReturn(cached);
		given(this.cachingPolicy.isCachedResponseUsable(eq(this.request), eq(cached), any(Date.class))).willReturn(false);
		given(this.conditionalRequestStrategy.canRevalidate(cached)).willReturn(false);
		given(this.clientExecution.execute(this.request, this.body)).willReturn(this.response);
		given(this.cachingPolicy.isResponseCacheable(this.request, this.response)).willReturn(false);

		ClientHttpResponse actual = this.interceptor.intercept(this.request, this.body, this.clientExecution);

		assertThat(actual, is(this.response));
		then(this.cachingPolicy).should().isServableFromCache(this.request);
		then(this.httpResponseCache).should().get(this.request);
		then(this.cachingPolicy).should().isCachedResponseUsable(eq(this.request), eq(cached), any(Date.class));
		then(this.conditionalRequestStrategy).should().canRevalidate(cached);
		then(this.conditionalRequestStrategy).should(never()).createConditionalRequest(any(), any());
		then(this.clientExecution).should().execute(this.request, this.body);
		then(this.cachingPolicy).should().isResponseCacheable(this.request, this.response);
		then(this.httpResponseCache).should(never()).put(any(), any(), any(), any());
	}

	@Test
	public void cachedResponseNotUsableConditionalRequestNotModified() throws Exception {
		HttpCacheEntry cached = mock(HttpCacheEntry.class);
		HttpRequest conditional = mock(HttpRequest.class);
		given(conditional.getMethod()).willReturn(HttpMethod.GET);
		given(conditional.getURI()).willReturn(new URI("http://example.org/resource-conditional"));
		given(this.response.getStatusCode()).willReturn(HttpStatus.NOT_MODIFIED);
		HttpCacheEntry cacheUpdated = mock(HttpCacheEntry.class);

		given(this.cachingPolicy.isServableFromCache(this.request)).willReturn(true);
		given(this.httpResponseCache.get(this.request)).willReturn(cached);
		given(this.cachingPolicy.isCachedResponseUsable(eq(this.request), eq(cached), any(Date.class))).willReturn(false);
		given(this.conditionalRequestStrategy.canRevalidate(cached)).willReturn(true);
		given(this.conditionalRequestStrategy.createConditionalRequest(this.request, cached)).willReturn(conditional);
		given(this.clientExecution.execute(conditional, this.body)).willReturn(this.response);
		given(this.httpResponseCache.get(conditional)).willReturn(cacheUpdated);

		ClientHttpResponse actual = this.interceptor.intercept(this.request, this.body, this.clientExecution);

		assertThat(actual, is(cacheUpdated));
		then(this.httpResponseCache).should().get(this.request);
		then(this.cachingPolicy).should().isCachedResponseUsable(eq(this.request), eq(cached), any(Date.class));
		then(this.conditionalRequestStrategy).should().canRevalidate(cached);
		then(this.conditionalRequestStrategy).should().createConditionalRequest(this.request, cached);
		then(this.clientExecution).should().execute(conditional, this.body);
		then(this.httpResponseCache).should().put(eq(conditional), eq(this.response), any(Date.class), any(Date.class));
	}

	@Test
	public void cachedResponseServedStaleIfServerError() throws Exception {
		HttpCacheEntry cached = mock(HttpCacheEntry.class);
		HttpRequest conditional = mock(HttpRequest.class);
		given(conditional.getMethod()).willReturn(HttpMethod.GET);
		given(conditional.getURI()).willReturn(new URI("http://example.org/resource-conditional"));
		given(this.response.getStatusCode()).willReturn(HttpStatus.INTERNAL_SERVER_ERROR);

		given(this.cachingPolicy.isServableFromCache(this.request)).willReturn(true);
		given(this.httpResponseCache.get(this.request)).willReturn(cached);
		given(this.cachingPolicy.isCachedResponseUsable(eq(this.request), eq(cached), any(Date.class))).willReturn(false);
		given(this.conditionalRequestStrategy.canRevalidate(cached)).willReturn(true);
		given(this.conditionalRequestStrategy.createConditionalRequest(this.request, cached)).willReturn(conditional);
		given(this.clientExecution.execute(conditional, this.body)).willReturn(this.response);
		given(this.cachingPolicy.canServeStaleResponseIfError(eq(cached))).willReturn(true);

		ClientHttpResponse actual = this.interceptor.intercept(this.request, this.body, this.clientExecution);

		assertThat(actual, is(cached));
		then(this.httpResponseCache).should().get(this.request);
		then(this.cachingPolicy).should().isCachedResponseUsable(eq(this.request), eq(cached), any(Date.class));
		then(this.conditionalRequestStrategy).should().canRevalidate(cached);
		then(this.conditionalRequestStrategy).should().createConditionalRequest(this.request, cached);
		then(this.clientExecution).should().execute(conditional, this.body);
		then(this.cachingPolicy).should().canServeStaleResponseIfError(eq(cached));
	}

	@Test
	public void cachedResponseNotUsableConditionalRequestUpdatedResource() throws Exception {
		HttpCacheEntry cached = mock(HttpCacheEntry.class);
		HttpRequest conditional = mock(HttpRequest.class);
		given(conditional.getMethod()).willReturn(HttpMethod.GET);
		given(conditional.getURI()).willReturn(new URI("http://example.org/resource-conditional"));

		given(this.cachingPolicy.isServableFromCache(this.request)).willReturn(true);
		given(this.httpResponseCache.get(this.request)).willReturn(cached);
		given(this.cachingPolicy.isCachedResponseUsable(eq(this.request), eq(cached), any(Date.class))).willReturn(false);
		given(this.conditionalRequestStrategy.canRevalidate(cached)).willReturn(true);
		given(this.conditionalRequestStrategy.createConditionalRequest(this.request, cached)).willReturn(conditional);
		given(this.clientExecution.execute(conditional, this.body)).willReturn(this.response);
		given(this.cachingPolicy.isResponseCacheable(conditional, this.response)).willReturn(true);

		ClientHttpResponse actual = this.interceptor.intercept(this.request, this.body, this.clientExecution);

		assertThat(actual, is(this.response));
		then(this.httpResponseCache).should().get(this.request);
		then(this.cachingPolicy).should().isCachedResponseUsable(eq(this.request), eq(cached), any(Date.class));
		then(this.conditionalRequestStrategy).should().canRevalidate(cached);
		then(this.conditionalRequestStrategy).should().createConditionalRequest(this.request, cached);
		then(this.clientExecution).should().execute(conditional, this.body);
		then(this.cachingPolicy).should().isResponseCacheable(conditional, this.response);
		then(this.httpResponseCache).should().put(eq(conditional), eq(this.response), any(Date.class), any(Date.class));
	}

	@Test
	public void serveFromCache() throws Exception {
		HttpCacheEntry cached = mock(HttpCacheEntry.class);
		HttpHeaders cachedHeaders = new HttpHeaders();

		given(this.cachingPolicy.isServableFromCache(this.request)).willReturn(true);
		given(this.httpResponseCache.get(this.request)).willReturn(cached);
		given(cached.calculateCurrentAge(any(Date.class))).willReturn(3600L);
		given(cached.getHeaders()).willReturn(cachedHeaders);
		given(this.cachingPolicy.isCachedResponseUsable(eq(this.request), eq(cached), any(Date.class))).willReturn(true);

		ClientHttpResponse actual = this.interceptor.intercept(this.request, this.body, this.clientExecution);

		assertThat(actual, is(cached));
		assertThat(cachedHeaders.getFirst(HttpHeaders.AGE), is("3600"));
		then(this.cachingPolicy).should().isServableFromCache(this.request);
		then(this.httpResponseCache).should().get(this.request);
		then(this.cachingPolicy).should().isCachedResponseUsable(eq(this.request), eq(cached), any(Date.class));
		then(this.clientExecution).should(never()).execute(any(), any());
	}

}
