package ro.isdc.wro.http.handler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Handler interface for components that process HTTP request.
 *
 * Typically implemented to provide api functionality for the
 * WroFilter. Handlers are applied before the processing in
 * the WroFilter, and only one handler can be applied. If one
 * handler is applied, other handlers are skipped and
 * processing is not performed.
 *
 * @author Ivar Conradi Østhus
 * @created Created on May 19, 2012
 */
public interface RequestHandler  {

  /**
   * Handle the given request, generating a response.
   * @param request current HTTP request
   * @param response current HTTP response
   * @throws IOException in case of I/O errors
   */
  void handle(HttpServletRequest request, HttpServletResponse response)
      throws IOException;

  /**
   * Determines if current request can be handled by this requestHandler
   * @param request current HTTP request
   * @return true if this requestHandler should handle this request
   */
  boolean accept(HttpServletRequest request);

  /**
   * Used to determine if the RequestHandler is enabled
   */
  boolean isEnabled();
}
