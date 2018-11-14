package nginx.unit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import java.lang.IllegalArgumentException;
import java.lang.IllegalStateException;
import java.lang.Object;
import java.lang.String;
import java.lang.StringBuffer;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import java.security.Principal;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;

import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.UrlEncoded;

import org.eclipse.jetty.server.CookieCutter;
import org.eclipse.jetty.http.MimeTypes;

public class Request implements HttpServletRequest, DynamicPathRequest
{
    private final Context context;
    private final long req_info_ptr;
    private final long req_ptr;

    protected String authType = null;

    protected boolean cookiesParsed = false;

    protected CookieCutter cookies = null;

    private final Map<String, Object> attributes = new HashMap<>();

    private MultiMap<String> parameters = null;

    private String context_path = "";
    private String servlet_path = "";
    private String path_info = "";
    private String request_uri = null;

    private DispatcherType dispatcher_type = DispatcherType.REQUEST;

    private String characterEncoding = null;

    /**
     * The only date format permitted when generating HTTP headers.
     */
    public static final String RFC1123_DATE =
            "EEE, dd MMM yyyy HH:mm:ss zzz";

    private static final SimpleDateFormat formats[] = {
        new SimpleDateFormat(RFC1123_DATE, Locale.US),
        new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
        new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.US)
    };

    private InputStream inputStream = null;
    private BufferedReader reader = null;

    public Request(Context ctx, long req_info, long req) {
        context = ctx;
        req_info_ptr = req_info;
        req_ptr = req;
    }

    @Override
    public boolean authenticate(HttpServletResponse response)
        throws IOException, ServletException
    {
        log("authenticate");

        if (response.isCommitted()) {
            throw new IllegalStateException();
        }

        return false;
    }

    @Override
    public String getAuthType()
    {
        log("getAuthType");

        return authType;
    }

    @Override
    public String getContextPath()
    {
        trace("getContextPath");

        return context_path;
    }

    public void setContextPath(String path)
    {
        context_path = path;
    }

    @Override
    public Cookie[] getCookies()
    {
        trace("getCookies");

        if (!cookiesParsed) {
            parseCookies();
        }

        //Javadoc for Request.getCookies() stipulates null for no cookies
        if (cookies == null || cookies.getCookies().length == 0) {
            return null;
        }

        return cookies.getCookies();
    }

    protected void parseCookies()
    {
        cookiesParsed = true;

        cookies = new CookieCutter();

        Enumeration<String> cookie_headers = getHeaders("Cookie");

        while (cookie_headers.hasMoreElements()) {
            cookies.addCookieField(cookie_headers.nextElement());
        }
    }

    @Override
    public long getDateHeader(String name)
    {
        trace("getDateHeader: " + name);

        String value = getHeader(name);
        if (value == null) {
            return -1L;
        }

        long res = parseDate(value);
        if (res == -1L) {
            throw new IllegalArgumentException(value);
        }

        return res;
    }

    protected long parseDate(String value)
    {
        Date date = null;
        for (int i = 0; (date == null) && (i < formats.length); i++) {
            try {
                date = formats[i].parse(value);
            } catch (ParseException e) {
                // Ignore
            }
        }
        if (date == null) {
            return -1L;
        }
        return date.getTime();
    }

    @Override
    public String getHeader(String name)
    {
        trace("getHeader: " + name);

        return getHeader(req_ptr, name, name.length());
    }
 
    private static native String getHeader(long req_ptr, String name, int name_len);


    @Override
    public Enumeration<String> getHeaderNames()
    {
        trace("getHeaderNames");

        return getHeaderNames(req_ptr);
    }

    private static native Enumeration<String> getHeaderNames(long req_ptr);


    @Override
    public Enumeration<String> getHeaders(String name)
    {
        trace("getHeaders: " + name);

        return getHeaders(req_ptr, name, name.length());
    }

    private static native Enumeration<String> getHeaders(long req_ptr, String name, int name_len);


    @Override
    public int getIntHeader(String name)
    {
        trace("getIntHeader: " + name);

        return getIntHeader(req_ptr, name, name.length());
    }

    private static native int getIntHeader(long req_ptr, String name, int name_len);


    @Override
    public String getMethod()
    {
        trace("getMethod");

        return getMethod(req_ptr);
    }

    private static native String getMethod(long req_ptr);


    @Override
    public Part getPart(String name) throws IOException, ServletException
    {
        log("getPart: " + name);

        return null;
    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException
    {
        log("getParts");

        return Collections.emptyList();
    }

    @Override
    public String getPathInfo()
    {
        trace("getPathInfo");

        return path_info;
    }

    @Override
    public void setPathInfo(String path)
    {
        path_info = path;
    }

    private static native String getPathInfo(long req_ptr);


    @Override
    public String getPathTranslated()
    {
        log("getPathTranslated");

        /* TODO */
        return null;
    }

    @Override
    public String getQueryString()
    {
        trace("getQueryString");

        return getQueryString(req_ptr);
    }

    private static native String getQueryString(long req_ptr);


    @Override
    public String getRemoteUser()
    {
        log("getRemoteUser");

        /* TODO */
        return null;
    }

    @Override
    public String getRequestedSessionId()
    {
        log("getRequestedSessionId");

        /* TODO */
        return null;
    }

    @Override
    public String getRequestURI()
    {
        trace("getRequestURI");

        if (request_uri == null) {
            request_uri = getRequestURI(req_ptr);
        }

        return request_uri;
    }

    private static native String getRequestURI(long req_ptr);


    @Override
    public void setRequestURI(String uri)
    {
        request_uri = uri;
    }


    @Override
    public StringBuffer getRequestURL()
    {
        trace("getRequestURL");

        String host = getHeader("Host");
        String uri = getRequestURI();
        return new StringBuffer("http://" + host + uri);
    }

    @Override
    public String getServletPath()
    {
        trace("getServletPath");

        return servlet_path;
    }

    @Override
    public void setServletPath(String path)
    {
        servlet_path = path;
    }

    @Override
    public HttpSession getSession()
    {
        log("getSession");

        return null;
    }

    @Override
    public HttpSession getSession(boolean create)
    {
        log("getSession: " + create);

        return null;
    }

    @Override
    public Principal getUserPrincipal()
    {
        log("getUserPrincipal");

        return null;
    }

    @Override
    public boolean isRequestedSessionIdFromCookie()
    {
        log("isRequestedSessionIdFromCookie");

        return false;
    }

    @Override
    @Deprecated
    public boolean isRequestedSessionIdFromUrl()
    {
        log("isRequestedSessionIdFromUrl");

        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromURL()
    {
        log("isRequestedSessionIdFromURL");

        return false;
    }

    @Override
    public boolean isRequestedSessionIdValid()
    {
        log("isRequestedSessionIdValid");

        return false;
    }

    @Override
    public boolean isUserInRole(String role)
    {
        log("isUserInRole: " + role);

        return false;
    }

    @Override
    public void login(String username, String password) throws ServletException
    {
        log("login: " + username + "," + password);
    }

    @Override
    public void logout() throws ServletException
    {
        log("logout");
    }


    @Override
    public AsyncContext getAsyncContext()
    {
        log("getAsyncContext");

        return null;
    }

    @Override
    public Object getAttribute(String name)
    {
        Object o = attributes.get(name);

        trace("getAttribute: " + name + " = " + o);

        return o;
    }

    @Override
    public Enumeration<String> getAttributeNames()
    {
        trace("getAttributeNames");

        Set<String> names = attributes.keySet();
        return Collections.enumeration(names);
    }

    @Override
    public String getCharacterEncoding()
    {
        trace("getCharacterEncoding");

        if (characterEncoding != null) {
            return characterEncoding;
        }

        getContentType();

        return characterEncoding;
    }

    @Override
    public int getContentLength()
    {
        trace("getContentLength");

        return (int) getContentLength(req_ptr);
    }

    private static native long getContentLength(long req_ptr);

    @Override
    public long getContentLengthLong()
    {
        trace("getContentLengthLong");

        return getContentLength(req_ptr);
    }

    @Override
    public String getContentType()
    {
        trace("getContentType");

        String content_type = getContentType(req_ptr);

        if (characterEncoding == null && content_type != null) {
            MimeTypes.Type mime = MimeTypes.CACHE.get(content_type);
            String charset = (mime == null || mime.getCharset() == null) ? MimeTypes.getCharsetFromContentType(content_type) : mime.getCharset().toString();
            if (charset != null) {
                characterEncoding = charset;
            }
        }

        return content_type;
    }

    private static native String getContentType(long req_ptr);


    @Override
    public DispatcherType getDispatcherType()
    {
        log("getDispatcherType");

        return dispatcher_type;
    }

    @Override
    public void setDispatcherType(DispatcherType type)
    {
        dispatcher_type = type;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException
    {
        trace("getInputStream");

        if (reader != null) {
            throw new IllegalStateException("getInputStream: getReader() already used");
        }

        if (inputStream == null) {
            inputStream = new InputStream(req_info_ptr);
        }

        return inputStream;
    }

    @Override
    public String getLocalAddr()
    {
        trace("getLocalAddr");

        return getLocalAddr(req_ptr);
    }

    private static native String getLocalAddr(long req_ptr);


    @Override
    public Locale getLocale()
    {
        log("getLocale");

        return Locale.getDefault();
    }

    @Override
    public Enumeration<Locale> getLocales()
    {
        log("getLocales");

        return Collections.emptyEnumeration();
    }

    @Override
    public String getLocalName()
    {
        trace("getLocalName");

        return getLocalName(req_ptr);
    }

    private static native String getLocalName(long req_ptr);


    @Override
    public int getLocalPort()
    {
        trace("getLocalPort");

        return getLocalPort(req_ptr);
    }

    private static native int getLocalPort(long req_ptr);


    private MultiMap<String> getParameters()
    {
        if (parameters != null) {
            return parameters;
        }

        parameters = new MultiMap<>();

        String query = getQueryString();

        if (query != null) {
            UrlEncoded.decodeUtf8To(query, parameters);
        }

        return parameters;
    }

    @Override
    public String getParameter(String name)
    {
        trace("getParameter: " + name);

        return getParameters().getValue(name, 0);
    }

    @Override
    public Map<String,String[]> getParameterMap()
    {
        trace("getParameterMap");

        return Collections.unmodifiableMap(getParameters().toStringArrayMap());
    }

    @Override
    public Enumeration<String> getParameterNames()
    {
        trace("getParameterNames");

        return Collections.enumeration(getParameters().keySet());
    }

    @Override
    public String[] getParameterValues(String name)
    {
        trace("getParameterValues: " + name);

        List<String> vals = getParameters().getValues(name);
        if (vals == null)
            return null;
        return vals.toArray(new String[vals.size()]);
    }

    @Override
    public String getProtocol()
    {
        trace("getProtocol");

        return getProtocol(req_ptr);
    }

    private static native String getProtocol(long req_ptr);

    @Override
    public BufferedReader getReader() throws IOException
    {
        trace("getReader");

        if (inputStream != null) {
            throw new IllegalStateException("getReader: getInputStream() already used");
        }

        if (reader == null) {
            reader = new BufferedReader(new InputStreamReader(new InputStream(req_info_ptr)));
        }

        return reader;
    }

    @Override
    @Deprecated
    public String getRealPath(String path)
    {
        log("getRealPath: " + path);

        return null;
    }

    @Override
    public String getRemoteAddr()
    {
        trace("getRemoteAddr");

        return getRemoteAddr(req_ptr);
    }

    private static native String getRemoteAddr(long req_ptr);


    @Override
    public String getRemoteHost()
    {
        trace("getRemoteHost");

        return getRemoteHost(req_ptr);
    }

    private static native String getRemoteHost(long req_ptr);


    @Override
    public int getRemotePort()
    {
        trace("getRemotePort");

        return getRemotePort(req_ptr);
    }

    private static native int getRemotePort(long req_ptr);


    @Override
    public RequestDispatcher getRequestDispatcher(String path)
    {
        trace("getRequestDispatcher: " + path);

        return context.getRequestDispatcher(path);
    }


    @Override
    public String getScheme()
    {
        log("getScheme");

        return getScheme(req_ptr);
    }

    private static native String getScheme(long req_ptr);


    @Override
    public String getServerName()
    {
        log("getServerName");

        return getServerName(req_ptr);
    }

    private static native String getServerName(long req_ptr);


    @Override
    public int getServerPort()
    {
        log("getServerPort");

        return getServerPort(req_ptr);
    }

    private static native int getServerPort(long req_ptr);

    @Override
    public ServletContext getServletContext()
    {
        trace("getServletContext");

        return context;
    }

    @Override
    public boolean isAsyncStarted()
    {
        log("isAsyncStarted");

        return false;
    }

    @Override
    public boolean isAsyncSupported()
    {
        log("isAsyncSupported");

        return false;
    }

    @Override
    public boolean isSecure()
    {
        log("isSecure");

        return false;
    }

    @Override
    public void removeAttribute(String name)
    {
        trace("removeAttribute: " + name);

        attributes.remove(name);
    }

    @Override
    public void setAttribute(String name, Object o)
    {
        trace("setAttribute: " + name + ", " + o);

        attributes.put(name, o);
    }

    @Override
    public void setCharacterEncoding(String env) throws UnsupportedEncodingException
    {
        trace("setCharacterEncoding: " + env);

        characterEncoding = env;
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException
    {
        log("startAsync");

        return null;
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException
    {
        log("startAsync(Req, resp)");

        return null;
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(
            Class<T> httpUpgradeHandlerClass) throws java.io.IOException, ServletException
    {
        log("upgrade: " + httpUpgradeHandlerClass.getName());

        return null;
    }

    @Override
    public String changeSessionId()
    {
        log("changeSessionId");

        return null;
    }

    private void log(String msg)
    {
        msg = "Request." + msg;
        log(req_info_ptr, msg, msg.length());
    }

    public static native void log(long req_info_ptr, String msg, int msg_len);


    private void trace(String msg)
    {
        msg = "Request." + msg;
        trace(req_info_ptr, msg, msg.length());
    }

    public static native void trace(long req_info_ptr, String msg, int msg_len);
}

