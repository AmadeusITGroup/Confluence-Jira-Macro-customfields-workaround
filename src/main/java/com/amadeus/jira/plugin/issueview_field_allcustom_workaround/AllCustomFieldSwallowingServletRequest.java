package com.amadeus.jira.plugin.issueview_field_allcustom_workaround;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

public class AllCustomFieldSwallowingServletRequest extends HttpServletRequestWrapper {
  private static final String FIELD = "field";
  private static final String ALLCUSTOM = "allcustom";
  private static final NameValuePair FIELD_ALLCUSTOM = new BasicNameValuePair(FIELD, ALLCUSTOM);
  private static final Charset CHARSET = StandardCharsets.UTF_8;
  private final Function<String, Collection<String>> customFieldMapper;

  @Nullable private Map<String, String[]> params;

  public AllCustomFieldSwallowingServletRequest(
      HttpServletRequest request, Function<String, Collection<String>> customFieldMapper) {
    super(request);
    this.customFieldMapper = customFieldMapper;
  }

  @Override
  public String getQueryString() {
    return URLEncodedUtils.format(
        URLEncodedUtils.parse(super.getQueryString(), CHARSET).stream()
            .filter(nvp -> !FIELD_ALLCUSTOM.equals(nvp))
            .flatMap(
                nvp -> {
                  if (FIELD.equals(nvp.getName())) {
                    Collection<String> fieldIds = customFieldMapper.apply(nvp.getValue());
                    return fieldIds.stream().map(id -> new BasicNameValuePair(nvp.getName(), id));
                  } else {
                    return Stream.of(nvp);
                  }
                })
            .collect(Collectors.toList()),
        CHARSET);
  }

  @Override
  @Nullable
  public String getParameter(String name) {
    if (!FIELD.equals(name)) {
      return super.getParameter(name);
    }

    String[] values = getParameterValues(name);
    if (values == null || values.length == 0) {
      return null;
    }
    return values[0];
  }

  @Override
  public synchronized Map<String, String[]> getParameterMap() {
    if (params == null) {
      Map<String, String[]> newParams = new HashMap<>(super.getParameterMap());
      String[] fields = newParams.get(FIELD);
      String[] newFields =
          Arrays.stream(fields)
              .filter(nvp -> !ALLCUSTOM.equals(nvp))
              .flatMap(name -> customFieldMapper.apply(name).stream())
              .toArray(String[]::new);
      if (newFields.length == 0) {
        newParams.remove(FIELD);
      } else {
        newParams.put(FIELD, newFields);
      }

      params = Collections.unmodifiableMap(newParams);
    }
    return params;
  }

  @Override
  @Nullable
  public Enumeration<String> getParameterNames() {
    return Collections.enumeration(getParameterMap().keySet());
  }

  @Override
  @Nullable
  public String[] getParameterValues(String name) {
    return getParameterMap().get(name);
  }

  @Override
  public HttpServletRequest getRequest() {
    return (HttpServletRequest) super.getRequest();
  }
}
