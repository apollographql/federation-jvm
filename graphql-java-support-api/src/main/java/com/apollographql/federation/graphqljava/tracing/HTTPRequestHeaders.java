package com.apollographql.federation.graphqljava.tracing;

import org.jetbrains.annotations.Nullable;

/**
 * If the context object on your GraphQL ExecutionInput implements this interface,
 * FederationTracingInstrumentation will generate traces only when requested to by the gateway in
 * front of it.
 *
 * @deprecated HTTPRequestHeaders relies on old deprecated GraphQL context mechanism. Migrate to use
 * new generic context map mechanism which should include ftv1 entry.
 */
@Deprecated
public interface HTTPRequestHeaders {
  /**
   * Return the value of the given HTTP header from the request, or null if the header is not
   * provided. The header name should be treated as case-insensitive. If the header is provided
   * multiple times, choose the first one.
   *
   * @param caseInsensitiveHeaderName the HTTP header name to get
   * @return the header, or null if not provided
   */
  @Nullable
  String getHTTPRequestHeader(String caseInsensitiveHeaderName);
}
