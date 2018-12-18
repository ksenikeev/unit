package nginx.unit;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

public class ForwardRequestWrapper implements DynamicPathRequest
{
    private final Request request_;

    private final boolean keep_attrs;

    private final String orig_servlet_path;
    private final String orig_path_info;
    private final String orig_uri;
    private final String orig_context_path;
    private final String orig_query;

    private final DispatcherType orig_dtype;

    public ForwardRequestWrapper(ServletRequest request)
    {
        if (request instanceof Request) {
            request_ = (Request) request;
        } else {
            request_ = (Request) request.getAttribute(Request.BARE);
        }

        keep_attrs = request_.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI) != null;

        orig_dtype = request_.getDispatcherType();

        orig_servlet_path = request_.getServletPath();
        orig_path_info = request_.getPathInfo();
        orig_uri = request_.getRequestURI();
        orig_context_path = request_.getContextPath();
        orig_query = request_.getQueryString();
    }

    @Override
    public void setDispatcherType(DispatcherType type)
    {
        request_.setDispatcherType(type);

        /*
            9.4.2 Forwarded Request Parameters
            ...
            Note that these attributes must always reflect the information in
            the original request even under the situation that multiple
            forwards and subsequent includes are called.
         */

        if (keep_attrs) {
            return;
        }

        /*
            9.4.2 Forwarded Request Parameters
            ...
            The values of these attributes must be equal to the return values
            of the HttpServletRequest methods getRequestURI, getContextPath,
            getServletPath, getPathInfo, getQueryString respectively, invoked
            on the request object passed to the first servlet object in the
            call chain that received the request from the client.
         */

        request_.setAttribute_(RequestDispatcher.FORWARD_SERVLET_PATH, orig_servlet_path);
        request_.setAttribute_(RequestDispatcher.FORWARD_PATH_INFO, orig_path_info);
        request_.setAttribute_(RequestDispatcher.FORWARD_REQUEST_URI, orig_uri);
        request_.setAttribute_(RequestDispatcher.FORWARD_CONTEXT_PATH, orig_context_path);
        request_.setAttribute_(RequestDispatcher.FORWARD_QUERY_STRING, orig_query);
    }

    @Override
    public void setServletPath(String servlet_path, String path_info)
    {
        request_.setServletPath(servlet_path, path_info);
    }

    @Override
    public void setRequestURI(String uri)
    {
        request_.setRequestURI(uri);
    }

    @Override
    public void setQueryString(String query)
    {
        if (query != null) {
            request_.setQueryString(query);
        }
    }

    public void close()
    {
        request_.setDispatcherType(orig_dtype);

        request_.setRequestURI(orig_uri);
        request_.setServletPath(orig_servlet_path, orig_path_info);
        request_.setQueryString(orig_query);

        if (keep_attrs) {
            return;
        }

        request_.setAttribute_(RequestDispatcher.FORWARD_SERVLET_PATH, null);
        request_.setAttribute_(RequestDispatcher.FORWARD_PATH_INFO, null);
        request_.setAttribute_(RequestDispatcher.FORWARD_REQUEST_URI, null);
        request_.setAttribute_(RequestDispatcher.FORWARD_CONTEXT_PATH, null);
        request_.setAttribute_(RequestDispatcher.FORWARD_QUERY_STRING, null);
    }
}
