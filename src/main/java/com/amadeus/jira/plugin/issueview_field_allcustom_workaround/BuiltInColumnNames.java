package com.amadeus.jira.plugin.issueview_field_allcustom_workaround;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class BuiltInColumnNames {
  private BuiltInColumnNames() {}

  // Copyright (c) 2020 Atlassian and others. Apache 2.0 licensed
  // https://github.com/atlassian-archive/confluence-server-built-in-column-names
  public static final Set<String> ALL_BUILTIN_COLUMN_NAMES =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
                  "description",
                  "environment",
                  "key",
                  "summary",
                  "type",
                  "parent",
                  "creator",
                  "project",
                  "priority",
                  "status",
                  "version",
                  "resolution",
                  "security",
                  "assignee",
                  "reporter",
                  "created",
                  "updated",
                  "due",
                  "component",
                  "components",
                  "votes",
                  "comments",
                  "attachments",
                  "subtasks",
                  "fixversion",
                  "timeoriginalestimate",
                  "timeestimate",
                  "statuscategory")));
}
