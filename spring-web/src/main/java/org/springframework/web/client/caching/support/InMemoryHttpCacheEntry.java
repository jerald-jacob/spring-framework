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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Date;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.caching.HttpCacheEntry;

/**
 * @author Brian Clozel
 * @since 4.3
 */
public class InMemoryHttpCacheEntry implements HttpCacheEntry {

	private final byte[] body;

	private final HttpStatus statusCode;

	private final HttpHeaders headers;

	private final long requestTime;

	private final long responseTime;

	private final long correctedInitialAge;

	// TODO: WARN time unit is seconds
	public InMemoryHttpCacheEntry(byte[] body, HttpStatus statusCode, HttpHeaders headers, Date requestDate, Date responseDate) {
		this.body = body;
		this.statusCode = statusCode;
		this.headers = headers;
		this.requestTime = requestDate.getTime() / 1000L;
		this.responseTime = responseDate.getTime() / 1000L;
		this.correctedInitialAge = calculateCorrectedInitialAge();
	}

	private long calculateCorrectedInitialAge() {
		long ageValue = Math.max(0, this.headers.getFirstDate(HttpHeaders.AGE)) / 1000L;
		long dateValue = Math.max(0, this.headers.getDate()) / 1000L;
		long apparentAge = Math.max(0, this.responseTime - dateValue);
		long correctedAgeValue = ageValue + (this.responseTime - this.requestTime);
		return Math.max(apparentAge, correctedAgeValue);
	}

	@Override
	public long getRequestTime() {
		return this.responseTime;
	}

	@Override
	public long getResponseTime() {
		return this.responseTime;
	}

	@Override
	public long getCorrectedInitialAge() {
		return this.correctedInitialAge;
	}

	@Override
	public long calculateCurrentAge(Date now) {
		return this.correctedInitialAge + (now.getTime() / 1000L) - this.responseTime;
	}

	@Override
	public HttpStatus getStatusCode() throws IOException {
		return this.statusCode;
	}

	@Override
	public int getRawStatusCode() throws IOException {
		return this.statusCode.value();
	}

	@Override
	public String getStatusText() throws IOException {
		return this.statusCode.getReasonPhrase();
	}

	@Override
	public void close() {
		// nothing
	}

	@Override
	public InputStream getBody() throws IOException {
		return new ByteArrayInputStream(this.body);
	}

	@Override
	public HttpHeaders getHeaders() {
		// TODO: should we make headers immutable here?
		//return HttpHeaders.readOnlyHttpHeaders(this.headers);
		return this.headers;
	}
}
