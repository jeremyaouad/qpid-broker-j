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

package org.apache.qpid.server.management.plugin.servlet.query;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.DatatypeConverter;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.apache.qpid.server.filter.OrderByExpression;
import org.apache.qpid.server.filter.SelectorParsingException;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.test.utils.QpidTestCase;

public class ConfiguredObjectQueryTest extends QpidTestCase
{
    private static final String NUMBER_ATTR = "numberAttr";
    private static final String DATE_ATTR = "dateAttr";
    private static final String ENUM_ATTR = "enumAttr";
    private static final String ENUM2_ATTR = "enum2Attr";

    enum Snakes
    {
        ANACONDA,
        PYTHON,
        VIPER
    };

    private final List<ConfiguredObject<?>> _objects = new ArrayList<>();
    private ConfiguredObjectQuery _query;

    public void testNoClauses_SingleResult() throws Exception
    {
        final UUID objectUuid = UUID.randomUUID();
        final String objectName = "obj1";

        ConfiguredObject obj1 = createCO(new HashMap<String, Object>()
        {{
            put(ConfiguredObject.ID, objectUuid);
            put(ConfiguredObject.NAME, objectName);
        }});

        _objects.add(obj1);

        _query = new ConfiguredObjectQuery(_objects, null, null);

        final List<String> headers = _query.getHeaders();
        assertEquals("Unexpected headers", Lists.newArrayList(ConfiguredObject.ID, ConfiguredObject.NAME), headers);

        List<List<Object>> results = _query.getResults();
        assertEquals("Unexpected number of results", 1, results.size());

        List<Object> row = results.iterator().next();
        assertEquals("Unexpected row", Lists.newArrayList(objectUuid, objectName), row);
    }

    public void testArithmeticStatementInOrderBy() throws Exception
    {
        final List<OrderByExpression> orderByExpressions;
        String orderByClause = "a + b";
        ConfiguredObjectFilterParser parser = new ConfiguredObjectFilterParser();
        parser.setConfiguredObjectExpressionFactory(new ConfiguredObjectExpressionFactory());
        try
        {
            orderByExpressions = parser.parseOrderBy(orderByClause);
            assertEquals(1, orderByExpressions.size());
        }
        catch (ParseException | TokenMgrError e)
        {
            throw new SelectorParsingException("Unable to parse orderBy clause", e);
        }
    }


    public void testInvalidStatementInOrderBy() throws Exception
    {
        final List<OrderByExpression> orderByExpressions;
        String orderByClause = "a + b foo";
        ConfiguredObjectFilterParser parser = new ConfiguredObjectFilterParser();
        parser.setConfiguredObjectExpressionFactory(new ConfiguredObjectExpressionFactory());
        try
        {
            orderByExpressions = parser.parseOrderBy(orderByClause);
            fail("Invalid expression was parsed without exception");
        }
        catch (ParseException | TokenMgrError e)
        {
            // pass
        }
    }

    public void testNoClauses_TwoResult() throws Exception
    {
        final UUID object1Uuid = UUID.randomUUID();
        final String object1Name = "obj1";

        final UUID object2Uuid = UUID.randomUUID();
        final String object2Name = "obj2";

        ConfiguredObject obj1 = createCO(new HashMap<String, Object>()
        {{
            put(ConfiguredObject.ID, object1Uuid);
            put(ConfiguredObject.NAME, object1Name);
            put("foo", "bar");
        }});

        ConfiguredObject obj2 = createCO(new HashMap<String, Object>()
        {{
            put(ConfiguredObject.ID, object2Uuid);
            put(ConfiguredObject.NAME, object2Name);
        }});

        _objects.add(obj1);
        _objects.add(obj2);

        _query = new ConfiguredObjectQuery(_objects, null, null);

        List<List<Object>> results = _query.getResults();
        assertEquals("Unexpected number of results", 2, results.size());

        final Iterator<List<Object>> iterator = results.iterator();
        List<Object> row1 = iterator.next();
        assertEquals("Unexpected row", Lists.newArrayList(object1Uuid, object1Name), row1);

        List<Object> row2 = iterator.next();
        assertEquals("Unexpected row", Lists.newArrayList(object2Uuid, object2Name), row2);
    }

    public void testSelectClause() throws Exception
    {
        final UUID objectUuid = UUID.randomUUID();

        ConfiguredObject obj = createCO(new HashMap<String, Object>()
        {{
            put(ConfiguredObject.ID, objectUuid);
            put(NUMBER_ATTR, 1234);
        }});

        _objects.add(obj);

        _query = new ConfiguredObjectQuery(_objects,
                                           String.format("%s,%s", ConfiguredObject.ID, NUMBER_ATTR),
                                           null);

        List<List<Object>> results = _query.getResults();
        assertEquals("Unexpected number of results", 1, results.size());

        final List<String> headers = _query.getHeaders();
        assertEquals("Unexpected headers", Lists.newArrayList(ConfiguredObject.ID, NUMBER_ATTR), headers);

        final Iterator<List<Object>> iterator = results.iterator();
        List<Object> row = iterator.next();
        assertEquals("Unexpected row", Lists.newArrayList(objectUuid, 1234), row);
    }

    public void testSelectClause_NonExistingColumn() throws Exception
    {
       ConfiguredObject obj = createCO(new HashMap<String, Object>()
        {{
            put(ConfiguredObject.ID, UUID.randomUUID());
        }});
        _objects.add(obj);

        _query = new ConfiguredObjectQuery(_objects, String.format("foo"), null);
        List<List<Object>> results = _query.getResults();
        assertEquals("Unexpected number of results", 1, results.size());
        assertEquals("Unexpected headers", Collections.singletonList("foo"), _query.getHeaders());
        assertEquals("Unexpected row", Collections.singletonList(null), results.get(0));
    }

    public void testSelectClause_ColumnAliases() throws Exception
    {
        final UUID objectUuid = UUID.randomUUID();

        ConfiguredObject obj = createCO(new HashMap<String, Object>()
        {{
            put(ConfiguredObject.ID, objectUuid);
            put(ConfiguredObject.NAME, "myObj");
            put(NUMBER_ATTR, 1234);
        }});

        _objects.add(obj);

        _query = new ConfiguredObjectQuery(_objects,
                                           String.format("%s,CONCAT(%s,%s) AS alias", ConfiguredObject.ID, ConfiguredObject.NAME, NUMBER_ATTR),
                                           null);

        List<List<Object>> results = _query.getResults();
        assertEquals("Unexpected number of results", 1, results.size());

        final List<String> headers = _query.getHeaders();
        assertEquals("Unexpected headers", Lists.newArrayList(ConfiguredObject.ID, "alias"), headers);

        final Iterator<List<Object>> iterator = results.iterator();
        List<Object> row = iterator.next();
        assertEquals("Unexpected row", Lists.newArrayList(objectUuid, "myObj1234"), row);
    }

    public void testQuery_StringEquality() throws Exception
    {
        final UUID objectUuid = UUID.randomUUID();
        final String objectName = "obj2";

        ConfiguredObject nonMatch = createCO(new HashMap<String, Object>()
        {{
            put(ConfiguredObject.ID, UUID.randomUUID());
            put(ConfiguredObject.NAME, "obj1");
        }});

        ConfiguredObject match = createCO(new HashMap<String, Object>()
        {{
            put(ConfiguredObject.ID, objectUuid);
            put(ConfiguredObject.NAME, objectName);
        }});

        _objects.add(nonMatch);
        _objects.add(match);

        _query = new ConfiguredObjectQuery(_objects, null, String.format("name = '%s'", objectName));

        final List<String> headers = _query.getHeaders();
        assertEquals("Unexpected headers", Lists.newArrayList(ConfiguredObject.ID, ConfiguredObject.NAME), headers);

        List<List<Object>> results = _query.getResults();
        assertEquals("Unexpected number of results", 1, results.size());

        final Iterator<List<Object>> iterator = results.iterator();
        List<Object> row = iterator.next();
        assertEquals("Unexpected row", objectUuid, row.get(0));
    }

    public void testQuery_DateInequality() throws Exception
    {
        final long now = System.currentTimeMillis();
        final UUID objectUuid = UUID.randomUUID();
        final long oneDayInMillis = TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS);
        final Date yesterday = new Date(now - oneDayInMillis);
        final Date tomorrow = new Date(now + oneDayInMillis);

        ConfiguredObject nonMatch = createCO(new HashMap<String, Object>()
        {{
            put(ConfiguredObject.ID, UUID.randomUUID());
            put(DATE_ATTR, yesterday);
        }});

        ConfiguredObject match = createCO(new HashMap<String, Object>()
        {{
            put(ConfiguredObject.ID, objectUuid);
            put(DATE_ATTR, tomorrow);
        }});

        _objects.add(nonMatch);
        _objects.add(match);

        _query = new ConfiguredObjectQuery(_objects,
                                           String.format("%s,%s", ConfiguredObject.ID, DATE_ATTR),
                                           String.format("%s > NOW()", DATE_ATTR));

        List<List<Object>> results = _query.getResults();
        assertEquals("Unexpected number of results", 1, results.size());

        final Iterator<List<Object>> iterator = results.iterator();
        List<Object> row = iterator.next();
        assertEquals("Unexpected row", objectUuid, row.get(0));
    }

    public void testQuery_DateEquality() throws Exception
    {
        final long now = System.currentTimeMillis();
        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        String nowIso8601Str = DatatypeConverter.printDateTime(calendar);

        final UUID objectUuid = UUID.randomUUID();

        ConfiguredObject nonMatch = createCO(new HashMap<String, Object>()
        {{
            put(ConfiguredObject.ID, UUID.randomUUID());
            put(DATE_ATTR, new Date(0));
        }});

        ConfiguredObject match = createCO(new HashMap<String, Object>()
        {{
            put(ConfiguredObject.ID, objectUuid);
            put(DATE_ATTR, new Date(now));
        }});

        _objects.add(nonMatch);
        _objects.add(match);

        _query = new ConfiguredObjectQuery(_objects,
                                           String.format("%s,%s", ConfiguredObject.ID, DATE_ATTR),
                                           String.format("%s = TO_DATE('%s')", DATE_ATTR,
                                                         nowIso8601Str));

        List<List<Object>> results = _query.getResults();
        assertEquals("Unexpected number of results", 1, results.size());

        final Iterator<List<Object>> iterator = results.iterator();
        List<Object> row = iterator.next();
        assertEquals("Unexpected row", objectUuid, row.get(0));
    }

    public void testQuery_DateExpressions() throws Exception
    {
        final UUID objectUuid = UUID.randomUUID();

        ConfiguredObject match = createCO(new HashMap<String, Object>()
        {{
            put(ConfiguredObject.ID, objectUuid);
            put(DATE_ATTR, new Date(0));
        }});

        _objects.add(match);

        _query = new ConfiguredObjectQuery(_objects,
                                           String.format("%s,%s", ConfiguredObject.ID, DATE_ATTR),
                                           String.format("%s = DATE_ADD(TO_DATE('%s'), '%s')",
                                                         DATE_ATTR,
                                                         "1970-01-01T10:00:00Z",
                                                         "-PT10H"));

        List<List<Object>> results = _query.getResults();
        assertEquals("Unexpected number of results", 1, results.size());

        final Iterator<List<Object>> iterator = results.iterator();
        List<Object> row = iterator.next();
        assertEquals("Unexpected row", objectUuid, row.get(0));
    }

    public void testDateToString() throws Exception
    {
        final UUID objectUuid = UUID.randomUUID();

        ConfiguredObject match = createCO(new HashMap<String, Object>()
        {{
            put(ConfiguredObject.ID, objectUuid);
            put(DATE_ATTR, new Date(0));
        }});

        _objects.add(match);

        _query = new ConfiguredObjectQuery(_objects,
                                           String.format("%s, TO_STRING(%s)", ConfiguredObject.ID, DATE_ATTR),
                                           null);

        List<List<Object>> results = _query.getResults();
        assertEquals("Unexpected number of results", 1, results.size());

        final Iterator<List<Object>> iterator = results.iterator();
        List<Object> row = iterator.next();
        assertEquals("Unexpected row", Lists.newArrayList(objectUuid, "1970-01-01T00:00:00Z"), row);
    }

    public void testDateToFormattedString() throws Exception
    {
        final UUID objectUuid = UUID.randomUUID();

        ConfiguredObject match = createCO(new HashMap<String, Object>()
        {{
            put(ConfiguredObject.ID, objectUuid);
            put(DATE_ATTR, new Date(0));
        }});

        _objects.add(match);

        _query = new ConfiguredObjectQuery(_objects,
                                           String.format("%s, TO_STRING(%s,'%s', 'UTC')",
                                                         ConfiguredObject.ID,
                                                         DATE_ATTR,
                                                         "%1$tF %1$tZ"),
                                           null);

        List<List<Object>> results = _query.getResults();
        assertEquals("Unexpected number of results", 1, results.size());

        final Iterator<List<Object>> iterator = results.iterator();
        List<Object> row = iterator.next();
        assertEquals("Unexpected row", Lists.newArrayList(objectUuid, "1970-01-01 UTC"), row);
    }

    public void testQuery_EnumEquality() throws Exception
    {
        final UUID objectUuid = UUID.randomUUID();

        ConfiguredObject obj = createCO(new HashMap<String, Object>()
        {{
            put(ConfiguredObject.ID, objectUuid);
            put(ENUM_ATTR, Snakes.PYTHON);
            put(ENUM2_ATTR, Snakes.PYTHON);
        }});

        _objects.add(obj);

        _query = new ConfiguredObjectQuery(_objects,
                                           String.format("%s", ConfiguredObject.ID),
                                           String.format("%s = '%s'", ENUM_ATTR, Snakes.PYTHON));

        List<List<Object>> results = _query.getResults();
        assertEquals("Unexpected number of results - enumAttr equality with enum constant", 1, results.size());

        List<Object> row = _query.getResults().iterator().next();
        assertEquals("Unexpected row", objectUuid, row.get(0));

        _query = new ConfiguredObjectQuery(_objects,
                                           String.format("%s", ConfiguredObject.ID),
                                           String.format("'%s' = %s", Snakes.PYTHON, ENUM_ATTR));

        results = _query.getResults();
        assertEquals("Unexpected number of results - enum constant equality with enumAttr", 1, results.size());

        row = _query.getResults().iterator().next();
        assertEquals("Unexpected row", objectUuid, row.get(0));

        _query = new ConfiguredObjectQuery(_objects,
                                           String.format("%s", ConfiguredObject.ID),
                                           String.format("%s <> '%s'", ENUM_ATTR, "toad"));

        results = _query.getResults();
        assertEquals("Unexpected number of results - enumAttr not equal enum constant", 1, results.size());

        _query = new ConfiguredObjectQuery(_objects,
                                           String.format("%s", ConfiguredObject.ID),
                                           String.format("%s = %s", ENUM_ATTR, ENUM2_ATTR));

        results = _query.getResults();
        assertEquals("Unexpected number of results - two attributes of type enum", 1, results.size());

    }

    public void testQuery_EnumEquality_InExpresssions() throws Exception
    {
        final UUID objectUuid = UUID.randomUUID();

        ConfiguredObject obj = createCO(new HashMap<String, Object>()
        {{
            put(ConfiguredObject.ID, objectUuid);
            put(ENUM_ATTR, Snakes.PYTHON);
            put(ENUM2_ATTR, Snakes.PYTHON);
        }});

        _objects.add(obj);

        _query = new ConfiguredObjectQuery(_objects,
                                           String.format("%s", ConfiguredObject.ID),
                                           String.format("%s in ('%s', '%s', '%s')",
                                                         ENUM_ATTR,
                                                         "toad", Snakes.VIPER, Snakes.PYTHON));

        List<List<Object>> results = _query.getResults();
        assertEquals("Unexpected number of results - emumAttr with set including the enum's constants", 1, results.size());

        _query = new ConfiguredObjectQuery(_objects,
                                           String.format("%s", ConfiguredObject.ID),
                                           String.format("%s in (%s)", ENUM_ATTR, ENUM2_ATTR));

        results = _query.getResults();
        assertEquals("Unexpected number of results - enumAttr with set including enum2Attr", 1, results.size());

        _query = new ConfiguredObjectQuery(_objects,
                                           String.format("%s", ConfiguredObject.ID),
                                           String.format("'%s' in (%s)", Snakes.PYTHON, ENUM_ATTR));

        results = _query.getResults();
        assertEquals("Unexpected number of results - attribute within the set", 1, results.size());
    }

    public void testFunctionActualParameterMismatch() throws Exception
    {
        try
        {
            _query = new ConfiguredObjectQuery(_objects,
                                               "TO_STRING() /*Too few arguments*/ ",
                                               null);
            fail("Exception not thrown");
        }
        catch (SelectorParsingException e)
        {
            // PASS
        }
    }

    public void testSingleOrderByClause() throws Exception
    {
        final int NUMBER_OF_OBJECTS = 3;

        for (int i = 0; i < NUMBER_OF_OBJECTS; ++i)
        {
            final int foo = (i + 1) % NUMBER_OF_OBJECTS;
            ConfiguredObject object = createCO(new HashMap<String, Object>()
            {{
                put("foo", foo);
            }});
            _objects.add(object);
        }

        ConfiguredObject object = createCO(new HashMap<String, Object>()
        {{
            put("foo", null);
        }});
        _objects.add(object);

        List<List<Object>> results;

        _query = new ConfiguredObjectQuery(_objects, "foo", null, "foo ASC");
        results = _query.getResults();
        assertQueryResults(new Object[][]{{null}, {0}, {1}, {2}}, results);

        _query = new ConfiguredObjectQuery(_objects, "foo", null, "foo DESC");
        results = _query.getResults();
        assertQueryResults(new Object[][]{{2}, {1}, {0}, {null}}, results);

        // if not specified order should be ASC
        _query = new ConfiguredObjectQuery(_objects, "foo", null, "foo");
        results = _query.getResults();
        assertQueryResults(new Object[][]{{null}, {0}, {1}, {2}}, results);
    }

    public void testTwoOrderByClauses() throws Exception
    {
        ConfiguredObject object;

        object = createCO(new HashMap<String, Object>()
        {{
            put("foo", 1);
            put("bar", 1);
        }});
        _objects.add(object);

        object = createCO(new HashMap<String, Object>()
        {{
            put("foo", 1);
            put("bar", 2);
        }});
        _objects.add(object);

        object = createCO(new HashMap<String, Object>()
        {{
            put("foo", 2);
            put("bar", 0);
        }});
        _objects.add(object);

        _query = new ConfiguredObjectQuery(_objects, "foo,bar", null, "foo, bar");
        assertQueryResults(new Object[][]{{1, 1}, {1, 2}, {2, 0}}, _query.getResults());

        _query = new ConfiguredObjectQuery(_objects, "foo,bar", null, "foo DESC, bar");
        assertQueryResults(new Object[][]{{2, 0}, {1, 1}, {1, 2}}, _query.getResults());

        _query = new ConfiguredObjectQuery(_objects, "foo,bar", null, "foo DESC, bar DESC");
        assertQueryResults(new Object[][]{{2, 0}, {1, 2}, {1, 1}}, _query.getResults());

        _query = new ConfiguredObjectQuery(_objects, "foo,bar", null, "foo, bar DESC");
        assertQueryResults(new Object[][]{{1, 2}, {1, 1}, {2, 0}}, _query.getResults());

        _query = new ConfiguredObjectQuery(_objects, "foo,bar", null, "bar, foo");
        assertQueryResults(new Object[][]{{2, 0}, {1, 1}, {1, 2}}, _query.getResults());

        _query = new ConfiguredObjectQuery(_objects, "foo,bar", null, "bar DESC, foo");
        assertQueryResults(new Object[][]{{1, 2}, {1, 1}, {2, 0}}, _query.getResults());

        _query = new ConfiguredObjectQuery(_objects, "foo,bar", null, "bar, foo DESC");
        assertQueryResults(new Object[][]{{2, 0}, {1, 1}, {1, 2}}, _query.getResults());

        _query = new ConfiguredObjectQuery(_objects, "foo,bar", null, "bar DESC, foo DESC");
        assertQueryResults(new Object[][]{{1, 2}, {1, 1}, {2, 0}}, _query.getResults());

        _query = new ConfiguredObjectQuery(_objects, "foo,bar", null, "boo DESC, foo DESC, bar");
        assertQueryResults(new Object[][]{{2, 0}, {1, 1}, {1, 2}}, _query.getResults());

        _query = new ConfiguredObjectQuery(_objects, "foo, bar", null, "1, 2");
        assertQueryResults(new Object[][]{{1, 1}, {1, 2}, {2, 0}}, _query.getResults());

        _query = new ConfiguredObjectQuery(_objects, "foo, bar", null, "2, 1");
        assertQueryResults(new Object[][]{{2, 0}, {1, 1}, {1, 2}}, _query.getResults());

        _query = new ConfiguredObjectQuery(_objects, "foo, bar", null, "foo, 2 DESC");
        assertQueryResults(new Object[][]{{1, 2}, {1, 1}, {2, 0}}, _query.getResults());
    }

    public void testOrderByWithInvalidColumnIndex()
    {
        try
        {
            new ConfiguredObjectQuery(_objects, "id", null, "2");
            fail("Exception is expected for column index out of bounds");
        }
        catch (EvaluationException e)
        {
            // pass
        }

        try
        {
            new ConfiguredObjectQuery(_objects, "id", null, "0 DESC");
            fail("Exception is expected for column index 0");
        }
        catch (EvaluationException e)
        {
            // pass
        }
    }


    public void testLimitWithoutOffset() throws Exception
    {
        int numberOfTestObjects = 3;
        for(int i=0;i<numberOfTestObjects;i++)
        {
            final String name = "test-" + i;
            ConfiguredObject object = createCO(new HashMap<String, Object>()
            {{
                put("name", name);
            }});
            _objects.add(object);
        }

        _query = new ConfiguredObjectQuery(_objects, "name", null, "name", "1", "0");
        assertQueryResults(new Object[][]{{"test-0"}}, _query.getResults());

        _query = new ConfiguredObjectQuery(_objects, "name", null, "name", "1", "1");
        assertQueryResults(new Object[][]{{"test-1"}}, _query.getResults());

        _query = new ConfiguredObjectQuery(_objects, "name", null, "name", "1", "3");
        assertQueryResults(new Object[0][1], _query.getResults());

        _query = new ConfiguredObjectQuery(_objects, "name", null, "name", "-1", "1");
        assertQueryResults(new Object[][]{{"test-1"},{"test-2"}}, _query.getResults());

        _query = new ConfiguredObjectQuery(_objects, "name", null, "name", "-1", "-4");
        assertQueryResults(new Object[][]{{"test-0"},{"test-1"},{"test-2"}}, _query.getResults());

        _query = new ConfiguredObjectQuery(_objects, "name", null, "name", "-1", "-2");
        assertQueryResults(new Object[][]{{"test-1"},{"test-2"}}, _query.getResults());

        _query = new ConfiguredObjectQuery(_objects, "name", null, "name", "invalidLimit", "invalidOffset");
        assertQueryResults(new Object[][]{{"test-0"},{"test-1"},{"test-2"}}, _query.getResults());

        _query = new ConfiguredObjectQuery(_objects, "name", null, "name", null, null);
        assertQueryResults(new Object[][]{{"test-0"},{"test-1"},{"test-2"}}, _query.getResults());
    }

    private void assertQueryResults(final Object[][] expectedAttributes,
                                    final List<List<Object>> results)
    {
        final int rows = expectedAttributes.length;
        assertEquals("Unexpected number of result rows", rows, results.size());
        if (rows > 0)
        {
            final int cols = expectedAttributes[0].length;
            for (int row = 0; row < rows; ++row)
            {
                assertEquals("Unexpected number of result columns", cols, results.get(row).size());
                for (int col = 0; col < cols; ++col)
                {
                    assertEquals("Unexpected row order", expectedAttributes[row][col], results.get(row).get(col));
                }
            }
        }
    }

    private ConfiguredObject createCO(final HashMap<String, Object> map)
    {
        ConfiguredObject object = mock(ConfiguredObject.class);

        Map<String, Object> orderedMap = Maps.newTreeMap();
        orderedMap.putAll(map);

        when(object.getAttributeNames()).thenReturn(orderedMap.keySet());
        for(String attributeName : orderedMap.keySet())
        {
            when(object.getAttribute(attributeName)).thenReturn(orderedMap.get(attributeName));
        }
        return object;
    }
}
