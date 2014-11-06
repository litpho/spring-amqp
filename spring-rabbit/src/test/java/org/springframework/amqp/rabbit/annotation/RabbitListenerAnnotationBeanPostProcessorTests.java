/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.amqp.rabbit.annotation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Iterator;

import org.junit.Test;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.MessageListenerTestContainer;
import org.springframework.amqp.rabbit.config.RabbitListenerContainerTestFactory;
import org.springframework.amqp.rabbit.listener.AbstractRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.listener.MethodRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpoint;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 */
public class RabbitListenerAnnotationBeanPostProcessorTests {

	@Test
	public void simpleMessageListener() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(
				Config.class, SimpleMessageListenerTestBean.class);

		RabbitListenerContainerTestFactory factory = context.getBean(RabbitListenerContainerTestFactory.class);
		assertEquals("One container should have been registered", 1, factory.getListenerContainers().size());
		MessageListenerTestContainer container = factory.getListenerContainers().get(0);

		RabbitListenerEndpoint endpoint = container.getEndpoint();
		assertEquals("Wrong endpoint type", MethodRabbitListenerEndpoint.class, endpoint.getClass());
		MethodRabbitListenerEndpoint methodEndpoint = (MethodRabbitListenerEndpoint) endpoint;
		assertNotNull(methodEndpoint.getBean());
		assertNotNull(methodEndpoint.getMethod());

		SimpleMessageListenerContainer listenerContainer = new SimpleMessageListenerContainer();
		methodEndpoint.setupListenerContainer(listenerContainer);
		assertNotNull(listenerContainer.getMessageListener());

		assertTrue("Should have been started " + container, container.isStarted());
		context.close(); // Close and stop the listeners
		assertTrue("Should have been stopped " + container, container.isStopped());
	}

	@Test
	public void metaAnnotationIsDiscovered() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(
				Config.class, MetaAnnotationTestBean.class);

		RabbitListenerContainerTestFactory factory = context.getBean(RabbitListenerContainerTestFactory.class);
		assertEquals("one container should have been registered", 1, factory.getListenerContainers().size());
		RabbitListenerEndpoint endpoint = factory.getListenerContainers().get(0).getEndpoint();
		assertEquals("metaTestQueue", ((AbstractRabbitListenerEndpoint) endpoint).getQueueNames().iterator().next());

		context.close();
	}

	@Test
	public void multipleQueueNamesTestBeanIsDiscovered() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(
				Config.class, MultipleQueueNamesTestBean.class);

		RabbitListenerContainerTestFactory factory = context.getBean(RabbitListenerContainerTestFactory.class);
		assertEquals("one container should have been registered", 1, factory.getListenerContainers().size());
		RabbitListenerEndpoint endpoint = factory.getListenerContainers().get(0).getEndpoint();
		final Iterator<String> iterator = ((AbstractRabbitListenerEndpoint) endpoint).getQueueNames().iterator();
		assertEquals("metaTestQueue", iterator.next());
		assertEquals("testQueue", iterator.next());

		context.close();
	}

	@Test
	public void multipleQueuesTestBeanIsDiscovered() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(
				Config.class, MultipleQueuesTestBean.class);

		RabbitListenerContainerTestFactory factory = context.getBean(RabbitListenerContainerTestFactory.class);
		assertEquals("one container should have been registered", 1, factory.getListenerContainers().size());
		RabbitListenerEndpoint endpoint = factory.getListenerContainers().get(0).getEndpoint();
		final Iterator<Queue> iterator = ((AbstractRabbitListenerEndpoint) endpoint).getQueues().iterator();
		assertEquals("testQueue", iterator.next().getName());
		assertEquals("secondQueue", iterator.next().getName());

		context.close();
	}

	@Test
	public void mixedQueuesAndQueueNamesTestBeanIsDiscovered() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(
				Config.class, MixedQueuesAndQueueNamesTestBean.class);

		RabbitListenerContainerTestFactory factory = context.getBean(RabbitListenerContainerTestFactory.class);
		assertEquals("one container should have been registered", 1, factory.getListenerContainers().size());
		RabbitListenerEndpoint endpoint = factory.getListenerContainers().get(0).getEndpoint();
		final Iterator<String> iterator = ((AbstractRabbitListenerEndpoint) endpoint).getQueueNames().iterator();
		assertEquals("metaTestQueue", iterator.next());
		assertEquals("secondQueue", iterator.next());
		assertEquals("testQueue", ((AbstractRabbitListenerEndpoint) endpoint).getQueues().iterator().next().getName());

		context.close();
	}

	@Component
	static class SimpleMessageListenerTestBean {

		@RabbitListener(queues = "testQueue")
		public void handleIt(String body) {
		}

	}


	@Component
	 static class MetaAnnotationTestBean {

		@FooListener
		public void handleIt(String body) {
		}
	}


	@RabbitListener(queues = "metaTestQueue")
	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	static @interface FooListener {
	}

	@Component
	static class MultipleQueueNamesTestBean {

		@RabbitListener(queues = {"metaTestQueue, #{@myTestQueue.name}"})
		public void handleIt(String body) {
		}
	}

	@Component
	static class MultipleQueuesTestBean {

		@RabbitListener(queues = {"#{@myTestQueue}, #{@mySecondQueue}"})
		public void handleIt(String body) {
		}
	}

	@Component
	static class MixedQueuesAndQueueNamesTestBean {

		@RabbitListener(queues = {"metaTestQueue, #{@myTestQueue}", "#{@mySecondQueue.name}"})
		public void handleIt(String body) {
		}
	}

	@Configuration
	static class Config {

		@Bean
		public RabbitListenerAnnotationBeanPostProcessor postProcessor() {
			RabbitListenerAnnotationBeanPostProcessor postProcessor = new RabbitListenerAnnotationBeanPostProcessor();
			postProcessor.setEndpointRegistry(rabbitListenerEndpointRegistry());
			postProcessor.setContainerFactoryBeanName("testFactory");
			return postProcessor;
		}

		@Bean
		public RabbitListenerEndpointRegistry rabbitListenerEndpointRegistry() {
			return new RabbitListenerEndpointRegistry();
		}

		@Bean
		public RabbitListenerContainerTestFactory testFactory() {
			return new RabbitListenerContainerTestFactory();
		}

		@Bean
		public Queue myTestQueue() {
			return new Queue("testQueue");
		}

        @Bean
        public Queue mySecondQueue() {
            return new Queue("secondQueue");
        }
	}

}
