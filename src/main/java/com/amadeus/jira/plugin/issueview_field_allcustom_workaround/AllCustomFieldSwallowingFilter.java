package com.amadeus.jira.plugin.issueview_field_allcustom_workaround;

import static com.amadeus.jira.plugin.issueview_field_allcustom_workaround.BuiltInColumnNames.ALL_BUILTIN_COLUMN_NAMES;

import com.atlassian.jira.config.properties.JiraProperties;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.fields.Field;
import com.atlassian.jira.issue.fields.FieldManager;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.apache.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AllCustomFieldSwallowingFilter implements Filter {
  private static final Logger log = LoggerFactory.getLogger(AllCustomFieldSwallowingFilter.class);
  public static final String FORCE_USER_AGENT = "Confluence Override";

  @ComponentImport private final CustomFieldManager customFieldManager;
  @ComponentImport private final FieldManager fieldManager;
  @ComponentImport private final JiraProperties jiraProperties;

  @Inject
  public AllCustomFieldSwallowingFilter(
      CustomFieldManager customFieldManager,
      FieldManager fieldManager,
      JiraProperties jiraProperties) {
    this.customFieldManager = customFieldManager;
    this.fieldManager = fieldManager;
    this.jiraProperties = jiraProperties;
  }

  private Collection<String> getCustomFieldIdsForName(String name) {
    // FIXME does this work together with the generic search
    if (ALL_BUILTIN_COLUMN_NAMES.contains(name)) {
      log.trace("'{}' is a builtin column name", name);
      return Collections.singleton(name);
    }
    if (fieldManager.isCustomFieldId(name)) {
      log.trace("'{}' is already a customfield ID", name);
      return Collections.singleton(name);
    }

    // Confluence requests the name in lower case form.
    List<String> fieldIds =
        customFieldManager.getCustomFieldObjects().stream()
            .filter(cf -> cf.getName().equalsIgnoreCase(name))
            .filter(fieldManager::isCustomField)
            .map(Field::getId)
            .collect(Collectors.toList());

    if (fieldIds.isEmpty()) {
      log.trace("Did not find any customfield for '{}', using as-is", name);
      return Collections.singleton(name);
    }
    log.trace("Translated '{}' to {}", name, fieldIds);
    return fieldIds;
  }

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void doFilter(ServletRequest req, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    HttpServletRequest request = (HttpServletRequest) req;

    if (!isConfluenceMacroRequest(request)) {
      log.trace("NOT a confluence request");
      chain.doFilter(request, response);
      return;
    }

    log.debug("Swallowing parameter field=allcustom from request");

    AllCustomFieldSwallowingServletRequest newRequest =
        new AllCustomFieldSwallowingServletRequest(request, this::getCustomFieldIdsForName);

    chain.doFilter(newRequest, response);
  }

  private boolean isConfluenceMacroRequest(HttpServletRequest request) {
    if (!"GET".equals(request.getMethod())) {
      log.trace("Not a confluence macro request. Method {} != GET", request.getMethod());
      return false;
    }

    String useragent = request.getHeader(HttpHeaders.USER_AGENT);
    if (jiraProperties.isDevMode() && FORCE_USER_AGENT.equals(useragent)) {
      return true;
    }

    if (useragent == null || !useragent.startsWith("Apache-HttpClient/")) {
      log.trace("Not a confluence macro request. Useragent {}", useragent);
      return false;
    }

    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authorization == null || !authorization.startsWith("OAuth ")) {
      log.trace("Not a confluence macro request. No Oauth authorization");
      return false;
    }

    return true;
  }

  @Override
  public void destroy() {}
}
