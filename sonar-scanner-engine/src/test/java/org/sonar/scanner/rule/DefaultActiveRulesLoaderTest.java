/*
 * SonarQube
 * Copyright (C) 2009-2023 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.scanner.rule;

import com.google.common.collect.ImmutableSortedMap;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.batch.rule.LoadedActiveRule;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.Severity;
import org.sonar.api.utils.MessageException;
import org.sonar.scanner.WsTestUtil;
import org.sonar.scanner.bootstrap.DefaultScannerWsClient;
import org.sonar.scanner.scan.branch.BranchConfiguration;
import org.sonarqube.ws.Common;
import org.sonarqube.ws.Rules;
import org.sonarqube.ws.Rules.Active;
import org.sonarqube.ws.Rules.ActiveList;
import org.sonarqube.ws.Rules.Actives;
import org.sonarqube.ws.Rules.Rule;
import org.sonarqube.ws.Rules.SearchResponse;
import org.sonarqube.ws.Rules.SearchResponse.Builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class DefaultActiveRulesLoaderTest {

  private static final int PAGE_SIZE_1 = 150;
  private static final int PAGE_SIZE_2 = 76;
  private static final RuleKey EXAMPLE_KEY = RuleKey.of("java", "S108");
  private static final String FORMAT_KEY = "format";
  private static final String FORMAT_VALUE = "^[a-z][a-zA-Z0-9]*$";
  private static final String SEVERITY_VALUE = Severity.MINOR;

  private DefaultActiveRulesLoader loader;
  private DefaultScannerWsClient wsClient;

  @Before
  public void setUp() {
    wsClient = mock(DefaultScannerWsClient.class);
    BranchConfiguration branchConfig = mock(BranchConfiguration.class);
    when(branchConfig.isPullRequest()).thenReturn(false);
    loader = new DefaultActiveRulesLoader(wsClient);
  }

  @Test
  public void feed_real_response_encode_qp() {
    int total = PAGE_SIZE_1 + PAGE_SIZE_2;

    WsTestUtil.mockStream(wsClient, urlOfPage(1), responseOfSize(PAGE_SIZE_1, total));
    WsTestUtil.mockStream(wsClient, urlOfPage(2), responseOfSize(PAGE_SIZE_2, total));

    Collection<LoadedActiveRule> activeRules = loader.load("c+-test_c+-values-17445");
    assertThat(activeRules).hasSize(total);
    assertThat(activeRules)
      .filteredOn(r -> r.getRuleKey().equals(EXAMPLE_KEY))
      .extracting(LoadedActiveRule::getParams)
      .extracting(p -> p.get(FORMAT_KEY))
      .containsExactly(FORMAT_VALUE);
    assertThat(activeRules)
      .filteredOn(r -> r.getRuleKey().equals(EXAMPLE_KEY))
      .extracting(LoadedActiveRule::getSeverity)
      .containsExactly(SEVERITY_VALUE);

    WsTestUtil.verifyCall(wsClient, urlOfPage(1));
    WsTestUtil.verifyCall(wsClient, urlOfPage(2));

    verifyNoMoreInteractions(wsClient);
  }

  @Test
  public void exception_thrown_when_elasticsearch_index_inconsistent() {
    WsTestUtil.mockStream(wsClient, urlOfPage(1), prepareCorruptedResponse());
    assertThatThrownBy(() -> loader.load("c+-test_c+-values-17445"))
      .isInstanceOf(MessageException.class)
      .hasMessage("Elasticsearch indices have become inconsistent. Consider re-indexing. " +
        "Check documentation for more information https://docs.sonarqube.org/latest/setup/troubleshooting");
  }

  private String urlOfPage(int page) {
    return "/api/rules/search.protobuf?f=repo,name,severity,lang,internalKey,templateKey,params,actives,createdAt,updatedAt,deprecatedKeys&activation=true"
      + ("") + "&qprofile=c%2B-test_c%2B-values-17445&ps=500&p=" + page + "";
  }

  /**
   * Generates an imaginary protobuf result.
   *
   * @param numberOfRules the number of rules, that the response should contain
   * @param total the number of results on all pages
   * @return the binary stream
   */
  private InputStream responseOfSize(int numberOfRules, int total) {
    Builder rules = SearchResponse.newBuilder();
    Actives.Builder actives = Actives.newBuilder();

    IntStream.rangeClosed(1, numberOfRules)
      .mapToObj(i -> RuleKey.of("java", "S" + i))
      .forEach(key -> {

        Rule.Builder ruleBuilder = Rule.newBuilder();
        ruleBuilder.setKey(key.toString());
        rules.addRules(ruleBuilder);

        Active.Builder activeBuilder = Active.newBuilder();
        activeBuilder.setCreatedAt("2014-05-27T15:50:45+0100");
        activeBuilder.setUpdatedAt("2014-05-27T15:50:45+0100");
        if (EXAMPLE_KEY.equals(key)) {
          activeBuilder.addParams(Rules.Active.Param.newBuilder().setKey(FORMAT_KEY).setValue(FORMAT_VALUE));
          activeBuilder.setSeverity(SEVERITY_VALUE);
        }
        ActiveList activeList = Rules.ActiveList.newBuilder().addActiveList(activeBuilder).build();
        actives.putAllActives(ImmutableSortedMap.of(key.toString(), activeList));
      });

    rules.setActives(actives);
    rules.setPaging(Common.Paging.newBuilder().setPageSize(numberOfRules).setTotal(total));
    return new ByteArrayInputStream(rules.build().toByteArray());
  }

  private InputStream prepareCorruptedResponse() {
    Builder rules = SearchResponse.newBuilder();
    Actives.Builder actives = Actives.newBuilder();

    IntStream.rangeClosed(1, 3)
      .mapToObj(i -> RuleKey.of("java", "S" + i))
      .forEach(key -> {

        Rule.Builder ruleBuilder = Rule.newBuilder();
        ruleBuilder.setKey(key.toString());
        rules.addRules(ruleBuilder);
      });

    rules.setActives(actives);
    rules.setPaging(Common.Paging.newBuilder().setPageSize(3).setTotal(3));
    return new ByteArrayInputStream(rules.build().toByteArray());
  }
}
