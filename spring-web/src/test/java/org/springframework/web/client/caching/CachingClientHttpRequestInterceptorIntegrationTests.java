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

import static org.junit.Assert.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.caching.support.DefaultCachingPolicy;
import org.springframework.web.client.caching.support.DefaultConditionalRequestStrategy;
import org.springframework.web.client.caching.support.DefaultHttpResponseCache;

/**
 * Integration tests for {@link CachingClientHttpRequestInterceptor}
 *
 * @author Brian Clozel
 */
public class CachingClientHttpRequestInterceptorIntegrationTests {

	private static final Charset UTF8 = Charset.forName("UTF-8");

	private static final String TEST_BODY = "testbody";

	private ConcurrentMapCache cache;

	private CachingPolicy cachingPolicy;

	private HttpResponseCache httpResponseCache;

	private ConditionalRequestStrategy conditionalRequestStrategy;

	private CachingClientHttpRequestInterceptor interceptor;

	private RestTemplate client;

	private ClientHttpRequestFactory requestFactory;

	private ClientHttpRequest request;

	private HttpHeaders requestHeaders;

	private ClientHttpResponse response;

	private HttpHeaders responseHeaders;


	@Before
	public void setup() throws Exception {
		this.requestFactory = mock(ClientHttpRequestFactory.class);
		this.request = mock(ClientHttpRequest.class);
		this.requestHeaders = new HttpHeaders();
		this.response = mock(ClientHttpResponse.class);
		this.responseHeaders = new HttpHeaders();

		this.requestHeaders.setAccept(Collections.singletonList(MediaType.ALL));
		this.responseHeaders.setDate(new Date().getTime());
		this.responseHeaders.setContentType(MediaType.TEXT_PLAIN);
		this.responseHeaders.setContentLength(TEST_BODY.length());

		this.cache = new ConcurrentMapCache("cachingclienttests");
		this.cachingPolicy = new DefaultCachingPolicy(false, 2048);
		this.httpResponseCache = new DefaultHttpResponseCache(2048, this.cache);
		this.conditionalRequestStrategy = new DefaultConditionalRequestStrategy();

		this.interceptor = new CachingClientHttpRequestInterceptor(this.httpResponseCache, this.cachingPolicy,
				this.conditionalRequestStrategy);
		this.client = new RestTemplate(this.requestFactory);
		this.client.setInterceptors(Collections.singletonList(this.interceptor));

		given(this.request.getHeaders()).willReturn(this.requestHeaders);
		given(this.response.getStatusCode()).willReturn(HttpStatus.OK);
		given(this.response.getRawStatusCode()).willReturn(HttpStatus.OK.value());
		given(this.response.getHeaders()).willReturn(this.responseHeaders);
		given(this.response.getBody()).willReturn(new ByteArrayInputStream(TEST_BODY.getBytes(UTF8)));
		given(this.request.execute()).willReturn(this.response);
	}

	@Test
	public void shouldAddResponseInCache() throws Exception {
		URI requestUri = new URI("http://example.com/resource");
		given(requestFactory.createRequest(requestUri, HttpMethod.GET)).willReturn(this.request);
		given(this.request.getURI()).willReturn(requestUri);
		this.responseHeaders.setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).getHeaderValue());

		ResponseEntity<String> actual = this.client.getForEntity("http://example.com/resource", String.class);

		assertEquals(TEST_BODY, actual.getBody());
		assertNotNull(this.httpResponseCache.get(this.request));
	}

	@Test
	public void shouldFetchResponseFromCache() throws Exception {
		URI requestUri = new URI("http://example.com/resource");
		given(requestFactory.createRequest(requestUri, HttpMethod.GET)).willReturn(this.request);
		given(this.request.getURI()).willReturn(requestUri);
		this.httpResponseCache.put(this.request, this.response, new Date(), new Date());
		this.responseHeaders.setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).getHeaderValue());

		ResponseEntity<String> actual = this.client.getForEntity("http://example.com/resource", String.class);

		assertEquals(TEST_BODY, actual.getBody());
		verify(this.request, never()).execute();
	}

}
