/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.apache.qpid.server.logging.logback;

import java.util.ArrayList;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.LogbackException;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.status.Status;

import org.apache.qpid.test.utils.QpidTestCase;

public class Logback1027WorkaroundTurboFilterTest extends QpidTestCase
{
    private static final String TEST_LOG_MESSAGE = "hello";
    private Logback1027WorkaroundTurboFilter _filter = new Logback1027WorkaroundTurboFilter();
    private Logger _logger;
    private SnoopingAppender _snoopingAppender;

    public void setUp() throws Exception
    {
        super.setUp();
        LoggerContext context = new LoggerContext();
        _logger = context.getLogger(Logback1027WorkaroundTurboFilterTest.class);
        _snoopingAppender = new SnoopingAppender();
        _logger.addAppender(_snoopingAppender);
    }

    public void testOneException()
    {
        Exception e = new Exception();
        final FilterReply reply = doDecide(e);
        assertEquals(FilterReply.NEUTRAL, reply);

        assertEquals(0, _snoopingAppender.getEvents().size());
    }

    public void testSuppressedExceptionRecursion()
    {
        Exception e1 = new Exception();
        Exception e2 = new Exception();
        e2.addSuppressed(e1);
        e1.addSuppressed(e2);

        final FilterReply reply = doDecide(e1);
        assertEquals(FilterReply.DENY, reply);

        final List<ILoggingEvent> events = _snoopingAppender.getEvents();
        assertEquals(1, events.size());

        assertLoggingEvent(events.get(0));
    }

    private void assertLoggingEvent(final ILoggingEvent loggingEvent)
    {
        assertEquals(Level.INFO, loggingEvent.getLevel());
        assertEquals(TEST_LOG_MESSAGE, loggingEvent.getMessage());
        assertNull(loggingEvent.getArgumentArray());
        IThrowableProxy thing = loggingEvent.getThrowableProxy();
        assertEquals(Logback1027WorkaroundTurboFilter.StringifiedException.class.getName(), thing.getClassName());
    }

    public void testInitCauseRecursion() throws Exception
    {
        Exception e1 = new Exception();
        Exception e2 = new Exception();
        e2.initCause(e1);
        e1.initCause(e2);

        final FilterReply reply = doDecide(e1);
        assertEquals(FilterReply.DENY, reply);
        assertEquals(1, _snoopingAppender.getEvents().size());
    }

    public void testNoRecursion()
    {
        Exception e1 = new Exception();
        Exception e2 = new Exception();
        Exception e3 = new Exception();

        e2.addSuppressed(e3);
        e1.addSuppressed(e2);
        e1.initCause(e3);

        final FilterReply reply = doDecide(e1);
        assertEquals(FilterReply.NEUTRAL, reply);
        assertEquals(0, _snoopingAppender.getEvents().size());
    }

    public void testNoRecursion2()
    {
        Exception e1 = new Exception();
        Exception e2 = new Exception();
        Exception e3 = new Exception();

        e2.initCause(e3);
        e1.initCause(e2);
        e1.addSuppressed(e3);

        final FilterReply reply = doDecide(e1);
        assertEquals(FilterReply.NEUTRAL, reply);
        assertEquals(0, _snoopingAppender.getEvents().size());
    }

    private FilterReply doDecide(final Exception e1)
    {
        return _filter.decide(null, _logger, Level.INFO, "hello", null, e1);
    }

    private static class SnoopingAppender implements Appender<ILoggingEvent>
    {

        private List<ILoggingEvent> _events = new ArrayList<>();

        List<ILoggingEvent> getEvents()
        {
            return _events;
        }

        @Override
        public void doAppend(final ILoggingEvent event) throws LogbackException
        {
            _events.add(event);
        }

        @Override
        public String getName()
        {
            return null;
        }

        @Override
        public void setName(final String name)
        {
        }

        @Override
        public void setContext(final Context context)
        {
        }

        @Override
        public Context getContext()
        {
            return null;
        }

        @Override
        public void addStatus(final Status status)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addInfo(final String msg)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addInfo(final String msg, final Throwable ex)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addWarn(final String msg)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addWarn(final String msg, final Throwable ex)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addError(final String msg)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addError(final String msg, final Throwable ex)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addFilter(final Filter<ILoggingEvent> newFilter)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clearAllFilters()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Filter<ILoggingEvent>> getCopyOfAttachedFiltersList()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public FilterReply getFilterChainDecision(final ILoggingEvent event)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void start()
        {
        }

        @Override
        public void stop()
        {
        }

        @Override
        public boolean isStarted()
        {
            return true;
        }
    }
}