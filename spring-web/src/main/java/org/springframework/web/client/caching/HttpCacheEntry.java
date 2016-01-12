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

import org.springframework.http.client.ClientHttpResponse;

import java.io.Serializable;
import java.util.Date;

/**
 * A {@link ClientHttpResponse} that's {@link Serializable} and thus can be stored in
 * a Cache for future use.
 *
 * @author Brian Clozel
 * @since 4.3
 * @see <a href="https://tools.ietf.org/html/rfc7234#section-4.2.3"></a>
 */
public interface HttpCacheEntry extends ClientHttpResponse, Serializable {


	/**
	 */
    long getRequestTime();

	/**
	 */
    long getResponseTime();

	/**
	 */
	long getCorrectedInitialAge();

	/**
	 *
	 * @param now
	 * @return
	 */
	long calculateCurrentAge(Date now);
}
