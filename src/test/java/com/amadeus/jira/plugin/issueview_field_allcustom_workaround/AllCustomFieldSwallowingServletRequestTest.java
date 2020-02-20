package com.amadeus.jira.plugin.issueview_field_allcustom_workaround;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlassian.jira.util.collect.EnumerationIterator;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class AllCustomFieldSwallowingServletRequestTest {

  @Test
  public void testParamsWithOtherFieldParams() {
    MockHttpServletRequest request = new MockHttpServletRequest();

    request.setParameter("foo", "bar");
    request.setParameter("field", new String[] {"one", "allcustom", "two"});

    assertThat(request.getParameterMap())
        .containsOnlyKeys("foo", "field")
        .containsEntry("foo", new String[] {"bar"})
        .containsEntry("field", new String[] {"one", "allcustom", "two"});
    assertThat(request.getParameter("field")).isNotNull();
    assertThat(request.getParameterValues("field")).containsExactly("one", "allcustom", "two");

    assertThat(EnumerationIterator.fromEnumeration(request.getParameterNames()))
        .containsExactlyInAnyOrder("foo", "field");

    AllCustomFieldSwallowingServletRequest wrappedRequest =
        new AllCustomFieldSwallowingServletRequest(
            request,
            // testing mapper
            id -> Arrays.asList(id, id.toUpperCase()));

    assertThat(wrappedRequest.getParameterMap())
        .containsOnlyKeys("foo", "field")
        .containsEntry("foo", new String[] {"bar"})
        .containsEntry("field", new String[] {"one", "ONE", "two", "TWO"});
    assertThat(wrappedRequest.getParameter("field")).isNotNull();
    assertThat(wrappedRequest.getParameterValues("field"))
        .containsExactly("one", "ONE", "two", "TWO");

    assertThat(EnumerationIterator.fromEnumeration(wrappedRequest.getParameterNames()))
        .containsExactlyInAnyOrder("foo", "field");
  }

  @Test
  public void testParamsWithNoOtherFieldParams() {
    MockHttpServletRequest request = new MockHttpServletRequest();

    request.setParameter("foo", "bar");
    request.setParameter("field", new String[] {"allcustom"});

    assertThat(request.getParameterMap())
        .containsOnlyKeys("foo", "field")
        .containsEntry("foo", new String[] {"bar"})
        .containsEntry("field", new String[] {"allcustom"});
    assertThat(request.getParameter("field")).isEqualTo("allcustom");
    assertThat(request.getParameterValues("field")).containsExactly("allcustom");

    assertThat(EnumerationIterator.fromEnumeration(request.getParameterNames()))
        .containsExactly("foo", "field");

    AllCustomFieldSwallowingServletRequest wrappedRequest =
        new AllCustomFieldSwallowingServletRequest(
            request, AllCustomFieldSwallowingServletRequestTest::duplicate);

    assertThat(wrappedRequest.getParameterMap())
        .containsOnlyKeys("foo")
        .containsEntry("foo", new String[] {"bar"});
    assertThat(wrappedRequest.getParameter("field")).isNull();
    assertThat(wrappedRequest.getParameterValues("field")).isNull();

    assertThat(EnumerationIterator.fromEnumeration(wrappedRequest.getParameterNames()))
        .containsExactly("foo");
  }

  @Test
  public void testSingleNonAllcustomfieldParam() {
    MockHttpServletRequest request = new MockHttpServletRequest();

    request.setParameter("field", new String[] {"foo"});

    assertThat(request.getParameterMap())
        .containsOnlyKeys("field")
        .containsEntry("field", new String[] {"foo"});
    assertThat(request.getParameter("field")).isEqualTo("foo");
    assertThat(request.getParameterValues("field")).containsExactly("foo");

    assertThat(EnumerationIterator.fromEnumeration(request.getParameterNames()))
        .containsExactly("field");

    AllCustomFieldSwallowingServletRequest wrappedRequest =
        new AllCustomFieldSwallowingServletRequest(request, Collections::singleton);

    assertThat(wrappedRequest.getParameterMap())
        .containsOnlyKeys("field")
        .containsEntry("field", new String[] {"foo"});
    assertThat(wrappedRequest.getParameter("field")).isEqualTo("foo");
    assertThat(wrappedRequest.getParameterValues("field")).containsExactly("foo");

    assertThat(EnumerationIterator.fromEnumeration(wrappedRequest.getParameterNames()))
        .containsExactly("field");
  }

  @Test
  public void testQueryPathRewrite() {
    String originalQueryString = "baz=quux&field=one&field=allcustom&field=two";
    MockHttpServletRequest request = new MockHttpServletRequest();

    request.setRequestURI("/foo/bar");
    request.setQueryString(originalQueryString);

    AllCustomFieldSwallowingServletRequest wrappedRequest =
        new AllCustomFieldSwallowingServletRequest(
            request, AllCustomFieldSwallowingServletRequestTest::duplicate);

    String cleanQueryString = "baz=quux&field=one&field=one&field=two&field=two";

    assertThat(wrappedRequest.getQueryString()).isEqualTo(cleanQueryString);
  }

  private static <T> Collection<T> duplicate(T t) {
    return Arrays.asList(t, t);
  }
}
