package nginx.unit;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import java.lang.IllegalArgumentException;
import java.lang.String;

import java.nio.charset.Charset;

import java.text.SimpleDateFormat;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.util.StringUtil;

public class Response implements HttpServletResponse {

    private long req_info_ptr;

    private String characterEncoding = "ISO-8859-1";

    /**
     * The only date format permitted when generating HTTP headers.
     */
    public static final String RFC1123_DATE =
            "EEE, dd MMM yyyy HH:mm:ss zzz";

    private static final SimpleDateFormat format =
            new SimpleDateFormat(RFC1123_DATE, Locale.US);

    /**
     * If this string is found within the comment of a cookie added with {@link #addCookie(Cookie)}, then the cookie
     * will be set as HTTP ONLY.
     */
    public final static String HTTP_ONLY_COMMENT = "__HTTP_ONLY__";

    private OutputStream outputStream = null;

    private PrintWriter writer = null;


    public Response(long ptr) {
        req_info_ptr = ptr;
    }

    /**
     * Format a set cookie value by RFC6265
     *
     * @param name the name
     * @param value the value
     * @param domain the domain
     * @param path the path
     * @param maxAge the maximum age
     * @param isSecure true if secure cookie
     * @param isHttpOnly true if for http only
     */
    public void addSetRFC6265Cookie(
            final String name,
            final String value,
            final String domain,
            final String path,
            final long maxAge,
            final boolean isSecure,
            final boolean isHttpOnly)
    {
        // Check arguments
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("Bad cookie name");
        }

        // Name is checked for legality by servlet spec, but can also be passed directly so check again for quoting
        // Per RFC6265, Cookie.name follows RFC2616 Section 2.2 token rules
        //Syntax.requireValidRFC2616Token(name, "RFC6265 Cookie name");
        // Ensure that Per RFC6265, Cookie.value follows syntax rules
        //Syntax.requireValidRFC6265CookieValue(value);

        // Format value and params
        StringBuilder buf = new StringBuilder();
        buf.append(name).append('=').append(value == null ? "" : value);

        // Append path
        if (path != null && path.length() > 0) {
            buf.append(";Path=").append(path);
        }

        // Append domain
        if (domain != null && domain.length() > 0) {
            buf.append(";Domain=").append(domain);
        }

        // Handle max-age and/or expires
        if (maxAge >= 0) {
            // Always use expires
            // This is required as some browser (M$ this means you!) don't handle max-age even with v1 cookies
            buf.append(";Expires=");
            if (maxAge == 0)
                buf.append(dateToString(0));
            else
                buf.append(dateToString(System.currentTimeMillis() + 1000L * maxAge));

            buf.append(";Max-Age=");
            buf.append(maxAge);
        }

        // add the other fields
        if (isSecure)
            buf.append(";Secure");
        if (isHttpOnly)
            buf.append(";HttpOnly");

        // add the set cookie
        addHeader("Set-Cookie", buf.toString());

        // Expire responses with set-cookie headers so they do not get cached.
        setDateHeader("Expires", 0);
    }

    @Override
    public void addCookie(Cookie cookie)
    {
        trace("addCookie: " + cookie.getName() + "=" + cookie.getValue());

        String comment = cookie.getComment();
        boolean httpOnly = false;

        if (comment != null) {
            int i = comment.indexOf(HTTP_ONLY_COMMENT);
            if (i >= 0) {
                httpOnly = true;
                comment = comment.replace(HTTP_ONLY_COMMENT, "").trim();
                if (comment.length() == 0) {
                    comment = null;
                }
            }
        }

        if (StringUtil.isBlank(cookie.getName()))
        {
            throw new IllegalArgumentException("Cookie.name cannot be blank/null");
        }

        addSetRFC6265Cookie(cookie.getName(),
            cookie.getValue(),
            cookie.getDomain(),
            cookie.getPath(),
            cookie.getMaxAge(),
            cookie.getSecure(),
            httpOnly || cookie.isHttpOnly());
    }

    @Override
    public void addDateHeader(String name, long date)
    {
        trace("addDateHeader: " + name + ": " + date);

        String value = dateToString(date);
        addHeader(req_info_ptr, name, name.length(), value, value.length());
    }

    private static String dateToString(long date)
    {
        Date dateValue = new Date(date);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return format.format(dateValue);
    }


    @Override
    public void addHeader(String name, String value)
    {
        trace("addHeader: " + name + ": " + value);

        addHeader(req_info_ptr, name, name.length(), value, value.length());
    }

    private static native void addHeader(long req_info_ptr, String name, int name_len, String value, int value_len);


    @Override
    public void addIntHeader(String name, int value)
    {
        trace("addIntHeader: " + name + ": " + value);

        addIntHeader(req_info_ptr, name, name.length(), value);
    }

    private static native void addIntHeader(long req_info_ptr, String name, int name_len, int value);


    @Override
    public boolean containsHeader(String name)
    {
        trace("containsHeader: " + name);

        return containsHeader(req_info_ptr, name, name.length());
    }

    private static native boolean containsHeader(long req_info_ptr, String name, int name_len);


    @Override
    @Deprecated
    public String encodeRedirectUrl(String url)
    {
        return encodeRedirectURL(url);
    }

    @Override
    public String encodeRedirectURL(String url)
    {
        log("encodeRedirectURL: " + url);

        return url;
    }

    @Override
    @Deprecated
    public String encodeUrl(String url)
    {
        return encodeURL(url);
    }

    @Override
    public String encodeURL(String url)
    {
        log("encodeURL: " + url);

        return url;
    }

    @Override
    public String getHeader(String name)
    {
        trace("getHeader: " + name);

        return getHeader(req_info_ptr, name, name.length());
    }

    private static native String getHeader(long req_info_ptr, String name, int name_len);


    @Override
    public Collection<String> getHeaderNames()
    {
        trace("getHeaderNames");

        Enumeration<String> e = getHeaderNames(req_info_ptr);
        if (e == null) {
            return Collections.emptyList();
        }

        return Collections.list(e);
    }

    private static native Enumeration<String> getHeaderNames(long req_info_ptr);


    @Override
    public Collection<String> getHeaders(String name)
    {
        trace("getHeaders: " + name);

        Enumeration<String> e = getHeaders(req_info_ptr, name, name.length());
        if (e == null) {
            return Collections.emptyList();
        }

        return Collections.list(e);
    }

    private static native Enumeration<String> getHeaders(long req_info_ptr, String name, int name_len);


    @Override
    public int getStatus()
    {
        trace("getStatus");

        return getStatus(req_info_ptr);
    }

    private static native int getStatus(long req_info_ptr);


    @Override
    public void sendError(int sc) throws IOException
    {
        sendError(sc, null);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException
    {
        trace("sendError: " + sc + ", " + msg);

        if (isCommitted()) {
            throw new IllegalStateException("Response already sent");
        }

        setStatus(sc);

        Request request = getRequest(req_info_ptr);

        // If we are allowed to have a body, then produce the error page.
        if (sc != SC_NO_CONTENT && sc != SC_NOT_MODIFIED &&
            sc != SC_PARTIAL_CONTENT && sc >= SC_OK)
        {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, sc);
            request.setAttribute(RequestDispatcher.ERROR_MESSAGE, msg);
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI,
                                 request.getRequestURI());
/*
            request.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME,
                                 request.getServletName());
*/
        }

        if (!request.isAsyncStarted()) {
            commit();
        }
    }

    private static native Request getRequest(long req_info_ptr);

    private void commit()
    {
        if (writer != null) {
            writer.close();

        } else if (outputStream != null) {
            outputStream.close();

        } else {
            commit(req_info_ptr);
        }
    }

    private static native void commit(long req_info_ptr);


    @Override
    public void sendRedirect(String location) throws IOException
    {
        trace("sendRedirect: " + location);

        sendRedirect(req_info_ptr, location, location.length());
    }

    private static native void sendRedirect(long req_info_ptr, String location, int location_len);


    @Override
    public void setDateHeader(String name, long date)
    {
        trace("setDateHeader: " + name + ": " + date);

        String value = dateToString(date);
        setHeader(req_info_ptr, name, name.length(), value, value.length());
    }


    @Override
    public void setHeader(String name, String value)
    {
        trace("setHeader: " + name + ": " + value);

        setHeader(req_info_ptr, name, name.length(), value, value.length());
    }

    private static native void setHeader(long req_info_ptr, String name, int name_len, String value, int value_len);


    @Override
    public void setIntHeader(String name, int value)
    {
        trace("setIntHeader: " + name + ": " + value);

        setIntHeader(req_info_ptr, name, name.length(), value);
    }

    private static native void setIntHeader(long req_info_ptr, String name, int name_len, int value);


    @Override
    public void setStatus(int sc)
    {
        trace("setStatus: " + sc);

        setStatus(req_info_ptr, sc);
    }

    private static native void setStatus(long req_info_ptr, int sc);


    @Override
    @Deprecated
    public void setStatus(int sc, String sm)
    {
        trace("setStatus: " + sc + "; " + sm);

        setStatus(req_info_ptr, sc);
    }


    @Override
    public void flushBuffer() throws IOException
    {
        trace("flushBuffer");

        if (writer != null) {
            writer.flush();
        }

        if (outputStream != null) {
            outputStream.flush();
        }
    }

    @Override
    public int getBufferSize()
    {
        trace("getBufferSize");

        return getBufferSize(req_info_ptr);
    }

    public static native int getBufferSize(long req_info_ptr);


    @Override
    public String getCharacterEncoding()
    {
        log("getCharacterEncoding");

        return characterEncoding;
    }

    @Override
    public String getContentType()
    {
        trace("getContentType");

        return getContentType(req_info_ptr);
    }

    private static native String getContentType(long req_info_ptr);

    @Override
    public Locale getLocale()
    {
        log("getLocale");

        return null;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException
    {
        trace("getOutputStream");

        if (writer != null) {
            throw new IllegalStateException("Writer already created");
        }

        if (outputStream == null) {
            outputStream = new OutputStream(req_info_ptr);
        }

        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException
    {
        trace("getWriter ( characterEncoding = '" + characterEncoding + "' )");

        if (outputStream != null) {
            throw new IllegalStateException("OutputStream already created");
        }

        if (writer == null) {
            ServletOutputStream stream = new OutputStream(req_info_ptr);

            writer = new PrintWriter(
                new OutputStreamWriter(stream, Charset.forName(characterEncoding)),
                false);
        }

        return writer;
    }

    @Override
    public boolean isCommitted()
    {
        trace("isCommitted");

        return isCommitted(req_info_ptr);
    }

    public static native boolean isCommitted(long req_info_ptr);

    @Override
    public void reset()
    {
        trace("reset");

        reset(req_info_ptr);

        writer = null;
        outputStream = null;
    }

    public static native void reset(long req_info_ptr);

    @Override
    public void resetBuffer()
    {
        trace("resetBuffer");

        resetBuffer(req_info_ptr);
    }

    public static native void resetBuffer(long req_info_ptr);

    @Override
    public void setBufferSize(int size)
    {
        trace("setBufferSize: " + size);

        setBufferSize(req_info_ptr, size);
    }

    public static native void setBufferSize(long req_info_ptr, int size);

    @Override
    public void setCharacterEncoding(String charset)
    {
        log("setCharacterEncoding " + charset);

        characterEncoding = charset;
    }


    @Override
    public void setContentLength(int len)
    {
        trace("setContentLength: " + len);

        setContentLength(req_info_ptr, len);
    }

    @Override
    public void setContentLengthLong(long len)
    {
        trace("setContentLengthLong: " + len);

        setContentLength(req_info_ptr, len);
    }

    private static native void setContentLength(long req_info_ptr, long len);


    @Override
    public void setContentType(String type)
    {
        log("setContentType: " + type);

        // TODO sync charset with characterEncoding
        setContentType(req_info_ptr, type, type.length());
    }

    private static native void setContentType(long req_info_ptr, String type, int type_len);


    @Override
    public void setLocale(Locale loc)
    {
        log("setLocale: " + loc);
    }

    private void log(String msg)
    {
        msg = "Response." + msg;
        log(req_info_ptr, msg, msg.length());
    }

    public static native void log(long req_info_ptr, String msg, int msg_len);


    private void trace(String msg)
    {
        msg = "Response." + msg;
        trace(req_info_ptr, msg, msg.length());
    }

    public static native void trace(long req_info_ptr, String msg, int msg_len);
}
