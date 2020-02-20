# Confluence Jira Macro all customfields workaround

This Jira plugin increases the performance of queries from the "Confluence Jira Macro".
It reduces the load on Jira and enables the creation of Confluence reports that are larger then previously possible.
The current performance problem is documented in https://jira.atlassian.com/browse/CONFSERVER-56017 .
(If you don't know about this ticket, you probably don't need this plugin)

## Problem description

The "Confluence Jira Macro" can be used on a Confluence page to retrieve ticket information from a linked Jira application.
It is also capable of retrieving the values of customfields.

Unfortunately, while the API of Jira expects all required customfields to be referenced by their ID, the Confluence macro tries to reference them by their name (in lowercase).
As this turns out not to work, the macro also requests *all* customfields, which indeed leads to the actually requested customfields being returned by Jira.
However the fact that all customfields have to be computed although only a small subset was requested by the user leads to a higher impact onto Jira and may even lead to timeouts in the query processing.

## Solution

This plugin installs a servlet filter in front of the Jira API used by the macro.
The filter intercepts all queries, detects if the query tries to retrieve customfields and if so rewrites the query to only ask the real Jira API for the customfields actually requested by the user.

## Outlook

This problem should be fixed properly by Atlassian in the Confluence Jira Macro to directly query Jira for only the needed customfields by their ID.
