package it.com.amadeus.jira.plugin.issueview_field_allcustom_workaround;

import static org.assertj.core.api.Assertions.assertThat;

import com.amadeus.jira.plugin.issueview_field_allcustom_workaround.AllCustomFieldSwallowingFilter;
import com.atlassian.jira.functest.framework.BaseJiraFuncTest;
import com.atlassian.jira.functest.framework.FuncTestRuleChain;
import com.atlassian.jira.functest.framework.FunctTestConstants;
import com.atlassian.jira.issue.customfields.CustomFieldUtils;
import com.atlassian.jira.rest.api.issue.IssueCreateResponse;
import com.atlassian.jira.rest.api.issue.IssueFields;
import com.atlassian.jira.testkit.beans.Screen;
import com.atlassian.jira.testkit.beans.Screen.Tab;
import com.atlassian.jira.testkit.client.restclient.ScreensClient;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

public class IntegrationTest extends BaseJiraFuncTest {
  @Rule public TestRule funcTest = FuncTestRuleChain.forTest(this);

  private static final String TEST_CUSTOMFIELD_1_NAME = "dummy1";
  private static final String TEST_CUSTOMFIELD_2_NAME = "dummy2";
  private Long testCustomfield1Id;
  private Long testCustomfield2Id;
  private Long testDuplicatedNameCustomfieldId;

  private static final String PROJECT_KEY = FunctTestConstants.PROJECT_MONKEY_KEY;

  @Before
  public void setUp() {
    backdoor.restoreBlankInstance();
    backdoor.websudo().disable();
    testCustomfield1Id = createTextField(TEST_CUSTOMFIELD_1_NAME);
    testCustomfield2Id = createTextField(TEST_CUSTOMFIELD_2_NAME);
    testDuplicatedNameCustomfieldId = createTextField(TEST_CUSTOMFIELD_1_NAME);

    for (Screen screen : backdoor.screens().getAllScreens()) {
      // testkit alone does not allow do add fields by id
      ScreensClient screenClient = new ScreensClient(environmentData, screen.getId());

      Tab tab = screen.getTabs().get(0);
      for (long customfieldId :
          Arrays.asList(testCustomfield1Id, testCustomfield2Id, testDuplicatedNameCustomfieldId)) {
        screenClient.addField(tab.getId(), cfId(customfieldId));
      }
    }
  }

  @Test
  public void testFunctionality() throws IOException, URISyntaxException {
    IssueCreateResponse ticket1 = backdoor.issues().createIssue(PROJECT_KEY, "ticket 1");
    IssueFields ticket1Fields = new IssueFields();
    ticket1Fields.customField(testCustomfield1Id, "value01");
    ticket1Fields.customField(testCustomfield2Id, "value02");
    ticket1Fields.customField(testDuplicatedNameCustomfieldId, "value03");
    backdoor.issues().setIssueFields(ticket1.key(), ticket1Fields);

    IssueCreateResponse ticket2 = backdoor.issues().createIssue(PROJECT_KEY, "ticket 2");
    IssueFields ticket2Fields = new IssueFields();
    ticket2Fields.customField(testCustomfield1Id, "value11");
    ticket2Fields.customField(testCustomfield2Id, "value12");
    ticket2Fields.customField(testDuplicatedNameCustomfieldId, "value13");
    backdoor.issues().setIssueFields(ticket2.key(), ticket2Fields);

    String searchResult =
        searchIssues(
            false,
            "project = " + PROJECT_KEY,
            Arrays.asList("summary", "allcustom", TEST_CUSTOMFIELD_1_NAME));
    assertThat(searchResult)
        .contains("summary")
        .contains(TEST_CUSTOMFIELD_1_NAME)
        .contains(cfId(testCustomfield1Id))
        .contains(cfId(testDuplicatedNameCustomfieldId))
        .contains(TEST_CUSTOMFIELD_2_NAME)
        .contains(cfId(testCustomfield2Id));

    searchResult =
        searchIssues(
            true,
            "project = " + PROJECT_KEY,
            Arrays.asList("summary", "allcustom", TEST_CUSTOMFIELD_1_NAME));
    assertThat(searchResult)
        .contains("summary")
        .contains(TEST_CUSTOMFIELD_1_NAME)
        .contains(cfId(testCustomfield1Id))
        .contains(cfId(testDuplicatedNameCustomfieldId))
        .contains("value01", "value03")
        .contains("value11", "value13")
        .doesNotContain(TEST_CUSTOMFIELD_2_NAME)
        .doesNotContain(cfId(testCustomfield2Id))
        .doesNotContain("value02")
        .doesNotContain("value12");

    searchResult =
        searchIssues(
            true,
            "project = " + PROJECT_KEY,
            Arrays.asList("summary", "allcustom", cfId(testCustomfield1Id)));
    assertThat(searchResult)
        .contains("summary")
        .contains(TEST_CUSTOMFIELD_1_NAME)
        .contains(cfId(testCustomfield1Id))
        .doesNotContain(TEST_CUSTOMFIELD_2_NAME)
        .doesNotContain(cfId(testCustomfield2Id))
        .doesNotContain(cfId(testDuplicatedNameCustomfieldId))
        .doesNotContain("value02", "value03")
        .doesNotContain("value12", "value13");
  }

  private Long createTextField(String name) {
    String cfId =
        backdoor
            .customFields()
            .createCustomField(
                name,
                "",
                FunctTestConstants.BUILT_IN_CUSTOM_FIELD_KEY
                    + ":"
                    + FunctTestConstants.CUSTOM_FIELD_TYPE_TEXTFIELD,
                FunctTestConstants.BUILT_IN_CUSTOM_FIELD_KEY
                    + ":"
                    + FunctTestConstants.CUSTOM_FIELD_TEXT_SEARCHER);

    return CustomFieldUtils.getCustomFieldId(cfId);
  }

  private String searchIssues(
      boolean emulateConfluenceMacro, String query, Collection<String> fields)
      throws IOException, URISyntaxException {

    // http://localhost:2990/jira/sr/jira.issueviews:searchrequest-xml/temp/SearchRequest.xml?tempMax=20&returnMax=true&jqlQuery=&field=summary&field=type&field=created&field=updated&field=allcustom&field=fo
    try (CloseableHttpClient client = HttpClients.createDefault()) {
      URIBuilder uriBuilder = new URIBuilder(environmentData.getBaseUrl().toURI());

      CredentialsProvider credsProvider = new BasicCredentialsProvider();
      credsProvider.setCredentials(
          AuthScope.ANY,
          new UsernamePasswordCredentials(
              FunctTestConstants.ADMIN_USERNAME, FunctTestConstants.ADMIN_PASSWORD));
      AuthCache authCache = new BasicAuthCache();
      authCache.put(
          new HttpHost(uriBuilder.getHost(), uriBuilder.getPort(), uriBuilder.getScheme()),
          new BasicScheme());

      HttpClientContext context = HttpClientContext.create();

      context.setCredentialsProvider(credsProvider);
      context.setAuthCache(authCache);

      uriBuilder.setPath(
          uriBuilder.getPath() + "/sr/jira.issueviews:searchrequest-xml/temp/SearchRequest.xml");
      uriBuilder.addParameter("jqlQuery", query);
      uriBuilder.addParameter("tempMax", String.valueOf(20));
      uriBuilder.addParameter("returnMax", String.valueOf(20));
      fields.forEach(field -> uriBuilder.addParameter("field", field));

      HttpGet request = new HttpGet(uriBuilder.build());

      if (emulateConfluenceMacro) {
        request.addHeader(HttpHeaders.USER_AGENT, AllCustomFieldSwallowingFilter.FORCE_USER_AGENT);
      } else {
        request.addHeader(HttpHeaders.USER_AGENT, "not specified");
      }

      try (CloseableHttpResponse response = client.execute(request, context)) {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(HttpStatus.SC_OK);

        Header contentType = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        assertThat(contentType.getValue()).isEqualTo("text/xml;charset=UTF-8");
        HttpEntity entity = response.getEntity();
        assertThat(entity).isNotNull();
        return IOUtils.toString(entity.getContent(), StandardCharsets.US_ASCII.name());
      }
    }
  }

  private static String cfId(long id) {
    return CustomFieldUtils.CUSTOM_FIELD_PREFIX + id;
  }
}
