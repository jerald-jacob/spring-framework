/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.messaging.rsocket.annotation.support;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;

import org.springframework.messaging.rsocket.ClientResponder;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.util.Assert;
import org.springframework.util.RouteMatcher;

/**
 * Default implementation of {@link ClientResponder} for annotated handlers.
 * @author Brian Clozel
 */
class DefaultClientResponder implements ClientResponder {

	private final List<Object> handlers;

	DefaultClientResponder(Object... handlers) {
		Assert.notEmpty(handlers, "handlers should not be empty");
		this.handlers = Arrays.asList(handlers);
	}

	@Override
	public BiFunction<ConnectionSetupPayload, RSocket, RSocket> toSocketAcceptor(RouteMatcher routeMatcher, RSocketStrategies strategies) {
		RSocketMessageHandler messageHandler = new RSocketMessageHandler();
		messageHandler.setHandlers(this.handlers);
		messageHandler.setRSocketStrategies(strategies);
		messageHandler.setRouteMatcher(routeMatcher);
		messageHandler.afterPropertiesSet();
		return messageHandler.clientAcceptor();
	}

}
